package xuanmo.arcartxsuite.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.config.ConfigSyncSpec;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.message.MessageProvider;

/**
 * 可插拔模块的抽象基类，封装通用生命周期管理。
 * <p>
 * 子类通过覆写声明式方法（{@link #configFileName()}, {@link #uiResourceMappings()} 等）
 * 和实现抽象方法（{@link #loadConfiguration}, {@link #startService}, {@link #stopService}）
 * 来定义模块行为。基类自动处理配置导出、UI 绑定、监听器注册、命令绑定、
 * PAPI 注册、客户端包路由注册和 shutdown 清理。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * public class MyModule extends AbstractAXSModule {
 *     private MyConfig config;
 *     private MyService service;
 *
 *     @Override public ModuleDescriptor descriptor() { ... }
 *     @Override protected String configFileName() { return "ArcartXMy.yml"; }
 *     @Override protected void loadConfiguration(File f) { config = MyConfig.load(f); }
 *     @Override protected void startService() { service = new MyService(context, config); service.start(); }
 *     @Override protected void stopService() { if (service != null) { service.shutdown(); service = null; } }
 * }
 * }</pre>
 */
public abstract class AbstractAXSModule implements AXSModule {

    /** 模块上下文，在 {@link #onEnable} 时注入 */
    protected ModuleContext context;

    private boolean ready;
    private boolean reloading; // reload 期间跳过 UI 注销，避免客户端丢失 HUD
    private boolean configFileJustMigrated; // 标记配置文件是否刚从旧位置迁移
    private final Map<String, UiBinding> uiBindings = new LinkedHashMap<>();
    private MessageProvider messages; // 模块消息提供者，由 messagesFileName() 声明后自动加载

    // ── 声明式元数据（子类按需覆写） ──────────────────────────

    /**
     * 默认配置文件名（如 "ArcartXLoginView.yml"）。
     * 返回 null 表示模块无独立配置文件。
     * <p>
     * 配置文件从模块 Jar 导出到宿主数据目录（plugins/ArcartXSuite/）。
     */
    @Nullable
    protected String configFileName() {
        return null;
    }

    /**
     * 模块消息文件名（如 {@code "messages.yml"}）。
     * 返回 null 表示模块不使用外部化消息（仍可硬编码）。
     * <p>
     * 声明后，基类在 {@link #onEnable} 时自动从模块 Jar 导出该文件到
     * {@code data/<moduleId>/<fileName>}，并加载为 {@link #messages()}。
     * 用户可编辑该文件自定义所有文本，支持 {@code &} 颜色码和 {@code {0}} 占位符。
     */
    @Nullable
    protected String messagesFileName() {
        return null;
    }

    /**
     * 主配置 yml 的同步策略。默认 {@link SyncPolicy#strict() strict}：
     * 玩家 yml 中不在内置默认值的键都会被视为废弃。
     * <p>
     * 子类若存在用户可自由增删的"动态节点"（如 announcer.entries、warehouse.warehouses），
     * 应覆写此方法返回 {@code SyncPolicy.builder().dynamicSection("xxx").build()}。
     */
    @NotNull
    protected SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.strict();
    }

    /**
     * 主配置 yml 的内置版本号。默认 1。
     * <p>
     * 若有破坏性字段重命名 / 删除，请把版本号 +1，并在模块 jar 内
     * {@code <migrationFolder>/<from>-<to>.yml} 写迁移规则。
     */
    protected int currentConfigVersion() {
        return 1;
    }

    /**
     * 主配置版本号字段路径。默认 {@code "config-version"}。
     */
    @NotNull
    protected String configVersionPath() {
        return "config-version";
    }

    /**
     * 模块 jar 内迁移文件夹路径。默认 {@code "migrations"}。
     * 返回空字符串表示该模块不参与版本迁移。
     */
    @NotNull
    protected String migrationFolder() {
        return "migrations";
    }

    /**
     * 主配置的校验规则。默认空列表。
     */
    @NotNull
    protected List<ValidationRule> mainConfigValidations() {
        return List.of();
    }

    /**
     * 附属配置规约（如 chat 模块的 chat/channels/*.yml、prop 模块的 prop/key.yml 等）。
     * 默认空列表。子类按需覆写。
     */
    @NotNull
    protected List<ModuleConfigSpec> additionalConfigSpecs() {
        return List.of();
    }

    /**
     * 默认实现：基于 {@link #configFileName()} 与上面的钩子组合出主 yml 的诊断规约，
     * 并追加 {@link #additionalConfigSpecs()}。子类一般无需覆写。
     */
    @Override
    public List<ModuleConfigSpec> configSpecs() {
        List<ModuleConfigSpec> specs = new ArrayList<>();
        String fileName = configFileName();
        if (fileName != null && !fileName.isBlank()) {
            String moduleId = descriptor().id();
            String targetRelative = "data/" + moduleId + "/config.yml";
            specs.add(new ModuleConfigSpec(
                moduleId,
                new ConfigSyncSpec(fileName, targetRelative, defaultSyncPolicy()),
                currentConfigVersion(),
                configVersionPath(),
                migrationFolder(),
                mainConfigValidations()
            ));
        }
        specs.addAll(additionalConfigSpecs());
        return List.copyOf(specs);
    }

    /**
     * UI 资源映射：模块 Jar 内的资源路径 → 宿主数据目录下的相对输出路径。
     * <p>
     * 示例: {@code Map.of("arcartx/ui/login_view.yml", "ui/login_view.yml")}
     *
     * @return 资源映射，空 Map 表示无 UI 资源
     */
    @NotNull
    protected Map<String, String> uiResourceMappings() {
        return Map.of();
    }

    /**
     * 是否覆写已有的 UI 文件。默认 false。
     * 子类可在 {@link #loadConfiguration} 之后根据配置动态返回。
     */
    protected boolean overwriteUiFiles() {
        return false;
    }

    /**
     * 创建模块需要的 Bukkit 事件监听器列表。
     * 返回的监听器将由基类在 {@link #onEnable} 时自动注册，
     * {@link #onDisable} 时自动注销。
     */
    @NotNull
    protected List<Listener> createListeners() {
        return List.of();
    }

    /**
     * 模块需要绑定的独立玩家命令：命令名 → Executor。
     * 命令名必须在 plugin.yml 中已声明。
     * <p>
     * 示例: {@code Map.of("title", new TitlePlayerCommand(this))}
     */
    @NotNull
    protected Map<String, TabExecutor> commandBindings() {
        return Map.of();
    }

    /**
     * 创建 PlaceholderAPI 占位符扩展实例。返回 null 表示不注册占位符。
     */
    @Nullable
    protected Object createPlaceholderExpansion() {
        return null;
    }

    /**
     * 创建客户端自定义包处理器。返回 null 表示不处理客户端回包。
     */
    @Nullable
    protected ClientPacketHandler createPacketHandler() {
        return null;
    }

    /**
     * 客户端包处理器优先级。数值越小越优先，越大越靠后。
     * 默认 0。EventPacket 模块建议使用 100。
     */
    protected int packetHandlerPriority() {
        return 0;
    }

    /**
     * 创建客户端初始化完成处理器。返回 null 表示不需要客户端初始化通知。
     */
    @Nullable
    protected ClientInitializedHandler createInitializedHandler() {
        return null;
    }

    // ── 模块必须实现的抽象方法 ──────────────────────────────────

    /**
     * 加载配置。在配置文件已确保存在之后调用。
     * 子类在此方法中解析配置文件并缓存配置对象。
     *
     * @param configFile 配置文件（如果 {@link #configFileName()} 返回 null 则此参数为 null）
     * @throws Exception 配置加载失败时抛出
     */
    protected abstract void loadConfiguration(@Nullable File configFile) throws Exception;

    /**
     * 创建并启动模块服务。
     * 在配置加载、UI 绑定完成之后调用。
     *
     * @throws Exception 启动失败时抛出
     */
    protected abstract void startService() throws Exception;

    /**
     * 关闭模块服务并释放资源。
     * 在监听器、命令、占位符等自动注销之前调用。
     */
    protected abstract void stopService();

    // ── 基类自动处理的生命周期 ─────────────────────────────────

    @Override
    public final boolean onEnable(ModuleContext context) throws Exception {
        this.context = context;
        Logger logger = context.logger();

        try {
            // 1. 导出并加载配置
            File configFile = ensureConfigExists();
            loadConfiguration(configFile);

            // 1b. 导出并加载外部化消息（若声明）
            initMessages();

            // 若配置文件刚从旧位置迁移，提示使用智能配置体检
            if (configFileJustMigrated) {
                logger.info("配置文件已迁移至新位置，建议运行 '/arcartxsuite config preview " + descriptor().id() + "' 检查配置兼容性");
            }

            // 2. 导出 UI 资源并绑定
            exportAndBindUi();

            // 3. 绑定命令（提前到 startService 之前，使用 Supplier 延迟引用服务；
            //    即使服务启动失败，命令仍可注册并对玩家显示友好提示）
            commandBindings().forEach((name, executor) ->
                context.registerCommand(name, executor));

            // 4. 启动服务
            startService();

            // 5. 注册事件监听器
            for (Listener listener : createListeners()) {
                context.registerListener(listener);
            }

            // 6. 注册 PlaceholderAPI 占位符
            if (context.hasPlugin("PlaceholderAPI")) {
                try {
                    Object expansion = createPlaceholderExpansion();
                    if (expansion != null) {
                        context.expansionRegistry().register(expansion);
                    }
                } catch (LinkageError error) {
                    logger.warning(descriptor().name() + " PlaceholderAPI 占位符不可用，已跳过注册: " + error.getMessage());
                }
            } else {
                logger.fine(descriptor().name() + " 未检测到 PlaceholderAPI，跳过占位符注册。");
            }

            // 7. 注册客户端包处理器
            ClientPacketHandler packetHandler = createPacketHandler();
            if (packetHandler != null) {
                context.registerClientPacketHandler(packetHandler, packetHandlerPriority());
            }

            // 8. 注册客户端初始化处理器
            ClientInitializedHandler initHandler = createInitializedHandler();
            if (initHandler != null) {
                context.registerClientInitializedHandler(initHandler);
            }

            ready = true;
            return true;
        } catch (Exception exception) {
            logger.severe(descriptor().name() + " 模块启动失败: " + exception.getMessage());
            cleanupOnFailure();
            throw exception;
        } catch (LinkageError error) {
            logger.severe(descriptor().name() + " 模块启动失败，缺少运行依赖或依赖版本不兼容: " + error.getMessage());
            cleanupOnFailure();
            throw error;
        }
    }

    @Override
    public final void onDisable() {
        ready = false;
        // 先关闭业务服务
        try {
            stopService();
        } catch (Exception exception) {
            if (context != null) {
                context.logger().warning(descriptor().name() + " 模块关闭异常: " + exception.getMessage());
            }
        }
        // 注销 UI（reload 期间跳过 unregisterUi，避免客户端丢失已打开的 HUD）
        if (!reloading) {
            for (UiBinding binding : uiBindings.values()) {
                if (binding.registeredUiId() != null && context != null) {
                    context.unregisterUi(binding.registeredUiId());
                }
            }
        }
        uiBindings.clear();
        // 自动注销所有已注册的监听器、命令、占位符等
        if (context != null) {
            context.unregisterListeners();
            context.expansionRegistry().unregisterAll();
        }
    }

    @Override
    public void onReload() throws Exception {
        reloading = true;
        try {
            onDisable();
            onEnable(context);
        } finally {
            reloading = false;
        }
    }

    @Override
    public final boolean isReady() {
        return ready;
    }

    // ── 子类可用的工具方法 ─────────────────────────────────────

    /**
     * 获取指定 UI 资源路径对应的绑定结果。
     *
     * @param relativeUiPath 相对于宿主数据目录的 UI 文件路径
     * @return UI 绑定，未找到时返回 null
     */
    @Nullable
    protected UiBinding getUiBinding(String relativeUiPath) {
        return uiBindings.get(relativeUiPath);
    }

    /**
     * 获取模块的 ClassLoader（用于资源加载）。
     */
    protected ClassLoader moduleClassLoader() {
        return getClass().getClassLoader();
    }

    /**
     * 获取模块消息提供者。仅当 {@link #messagesFileName()} 返回非空时可用。
     * <p>
     * 用于替代硬编码文本：{@code messages().get("key", arg0, arg1)}。
     *
     * @return 消息提供者；若模块未声明消息文件则返回 null
     */
    @Nullable
    protected MessageProvider messages() {
        return messages;
    }

    // ── 内部方法 ──────────────────────────────────────────────

    /**
     * 确保配置文件存在。
     * <p>
     * 新版本中模块配置统一落到 {@code plugins/ArcartXSuite/data/<moduleId>/config.yml}，
     * 不再散落在宿主根目录。若检测到根目录存在旧版 yml（如 {@code ArcartXChat.yml}），
     * 会一次性迁移到新位置；新装服务器则直接从模块 Jar 导出默认配置。
     */
    @Nullable
    private File ensureConfigExists() {
        configFileJustMigrated = false;
        String fileName = configFileName();
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        File moduleDataFolder = context.dataFolder();
        File newConfigFile = new File(moduleDataFolder, "config.yml");

        // 一次性迁移：plugins/ArcartXSuite/<fileName> -> data/<moduleId>/config.yml
        File legacyFile = new File(context.pluginDataFolder(), fileName);
        if (legacyFile.isFile() && !newConfigFile.exists()) {
            try {
                java.nio.file.Files.createDirectories(moduleDataFolder.toPath());
                java.nio.file.Files.move(
                    legacyFile.toPath(),
                    newConfigFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
                configFileJustMigrated = true;
                context.logger().info(org.bukkit.ChatColor.GOLD + "→ 已归位配置文件: "
                    + org.bukkit.ChatColor.YELLOW + fileName
                    + org.bukkit.ChatColor.GRAY + "  ➜  "
                    + org.bukkit.ChatColor.AQUA + "data/" + moduleDataFolder.getName() + "/config.yml");
            } catch (IOException exception) {
                context.logger().warning("迁移配置文件失败: " + fileName
                    + " | " + exception.getMessage());
            }
        }

        // 首次启动或仍缺失：从模块 Jar 导出默认配置到 data/<moduleId>/config.yml
        if (!newConfigFile.exists()) {
            String relative = moduleDataFolder.getName() + "/config.yml";
            // exportConfigResource 第二个参数是相对 pluginDataFolder 的路径
            return context.exportConfigResource(
                fileName, "data/" + relative, false, moduleClassLoader());
        }
        return newConfigFile;
    }

    /**
     * 导出并加载外部化消息文件。
     * <p>
     * 与 {@link #ensureConfigExists()} 同样走 {@link ModuleContext#exportConfigResource}，
     * 以便正确解密付费模块的加密资源（.axb / .axl）。文件落到
     * {@code data/<moduleId>/<fileName>}，用户编辑后 reload 即可生效。
     */
    private void initMessages() {
        String fileName = messagesFileName();
        if (fileName == null || fileName.isBlank()) {
            messages = null;
            return;
        }
        File moduleDataFolder = context.dataFolder();
        File messagesFile = new File(moduleDataFolder, fileName);
        if (!messagesFile.exists()) {
            // 走宿主导出（处理加密资源解密），目标相对 pluginDataFolder
            context.exportConfigResource(
                fileName,
                "data/" + moduleDataFolder.getName() + "/" + fileName,
                false,
                moduleClassLoader()
            );
        }
        messages = new MessageProvider(moduleDataFolder, fileName, moduleClassLoader(), context.logger());
        messages.load();
        context.logger().fine(descriptor().name() + " 已加载 " + messages.size() + " 条消息。");
    }

    /**
     * 导出所有声明的 UI 资源并执行 ArcartX UI 绑定。
     */
    private void exportAndBindUi() throws IOException {
        Map<String, String> mappings = uiResourceMappings();
        if (mappings.isEmpty()) {
            return;
        }
        boolean overwrite = overwriteUiFiles();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String resourcePath = entry.getKey();
            String relativeUiPath = entry.getValue();
            File uiFile = context.exportUiResource(resourcePath, relativeUiPath, overwrite, moduleClassLoader());
            // UI 绑定由子类在 startService() 中按需调用 context.prepareUiBinding()
            // 这里仅记录导出的文件路径供子类查询
            uiBindings.put(relativeUiPath, new UiBinding(relativeUiPath, null));
        }
    }

    /**
     * 启动失败时的清理。
     */
    private void cleanupOnFailure() {
        try {
            stopService();
        } catch (Exception ignored) {
        }
        for (UiBinding binding : uiBindings.values()) {
            if (binding.registeredUiId() != null && context != null) {
                context.unregisterUi(binding.registeredUiId());
            }
        }
        uiBindings.clear();
        if (context != null) {
            context.unregisterListeners();
            context.expansionRegistry().unregisterAll();
        }
    }

    /**
     * 记录 UI 绑定结果（子类在 startService 中调用 prepareUiBinding 后更新）。
     *
     * @param relativeUiPath UI 文件相对路径
     * @param binding        UI 绑定结果
     */
    protected void recordUiBinding(String relativeUiPath, UiBinding binding) {
        uiBindings.put(relativeUiPath, binding);
    }
}
