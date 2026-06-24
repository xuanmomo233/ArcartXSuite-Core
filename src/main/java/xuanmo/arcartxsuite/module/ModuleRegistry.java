package xuanmo.arcartxsuite.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleLoadException;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeManager;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemMatcherSupport;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.account.AccountTypeService;
import xuanmo.arcartxsuite.account.AccountTypeServiceImpl;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.bridge.DefaultAttributeBridgeRegistry;
import xuanmo.arcartxsuite.bridge.DefaultAriaBridge;
import xuanmo.arcartxsuite.bridge.DefaultItemSourceRegistry;
import xuanmo.arcartxsuite.bridge.TaczCombatBridge;
import xuanmo.arcartxsuite.condition.DefaultScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.crossserver.CrossServerService;
import xuanmo.arcartxsuite.keybind.KeybindService;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

/**
 * 模块注册表：扫描、加载、启用、禁用和查询模块。
 */
public final class ModuleRegistry {

    private static final Logger LOGGER = new PluginConsoleLogger("AXS-ModuleRegistry", null);

    private final JavaPlugin plugin;
    private final File modulesDir;
    private final PacketBridgeAPI packetBridge;
    private final ClientBridgeAPI clientBridge;
    private final ItemBridgeAPI itemStackBridge;
    private final PropBridgeAPI propBridge;
    private final PacketGuardAPI packetGuard;
    private final KeybindService keybindService;
    private final TaczCombatBridge taczCombatBridge;
    private final CrossServerService crossServerService;
    private final ModuleSignatureVerifier signatureVerifier;
    private final PlaceholderResolverAPI placeholderResolver;

    // ─── 全局桥接单例 ──────────────────────────────────────────
    private DefaultItemSourceRegistry itemSourceRegistry;
    private ItemMatcherSupport itemMatcherSupport;
    private CurrencyBridgeManager currencyBridgeManager;
    private DefaultAttributeBridgeRegistry attributeBridgeRegistry;
    private DefaultAriaBridge ariaBridge;
    private ScriptConditionEvaluator scriptConditionEvaluator;
    private AccountTypeServiceImpl accountTypeService;

    /** 按 id 排列的已加载模块，保持加载顺序 */
    private final LinkedHashMap<String, LoadedModule> modules = new LinkedHashMap<>();
    /** 外部模块注册的命令处理器 */
    private final LinkedHashMap<String, ModuleCommandHandler> commandHandlers = new LinkedHashMap<>();
    /** 客户端包处理器注册表（按优先级排序） */
    private final CopyOnWriteArrayList<PrioritizedPacketHandler> packetHandlers = new CopyOnWriteArrayList<>();
    /** 客户端初始化处理器注册表 */
    private final CopyOnWriteArrayList<PrioritizedInitializedHandler> initializedHandlers = new CopyOnWriteArrayList<>();
    /** Capability 全局注册表（类型 -> 实现） */
    private final java.util.concurrent.ConcurrentHashMap<Class<?>, Object> capabilities = new java.util.concurrent.ConcurrentHashMap<>();
    /** 模块注册的 Capability 类型（用于按模块卸载） */
    private final java.util.concurrent.ConcurrentHashMap<String, CopyOnWriteArrayList<Class<?>>> moduleCapabilityTypes = new java.util.concurrent.ConcurrentHashMap<>();
    /** 多实例 capability：玩家数据清除注册表 */
    private final CopyOnWriteArrayList<xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable> purgeables = new CopyOnWriteArrayList<>();
    /** 多实例 capability：数据库一键迁移注册表 */
    private final CopyOnWriteArrayList<xuanmo.arcartxsuite.api.capability.DatabaseMigratable> migratables = new CopyOnWriteArrayList<>();

    public ModuleRegistry(
        JavaPlugin plugin,
        File modulesDir,
        PacketBridgeAPI packetBridge,
        ClientBridgeAPI clientBridge,
        ItemBridgeAPI itemStackBridge,
        PropBridgeAPI propBridge,
        PacketGuardAPI packetGuard,
        KeybindService keybindService,
        TaczCombatBridge taczCombatBridge,
        CrossServerService crossServerService,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.modulesDir = modulesDir;
        this.packetBridge = packetBridge;
        this.clientBridge = clientBridge;
        this.itemStackBridge = itemStackBridge;
        this.propBridge = propBridge;
        this.packetGuard = packetGuard;
        this.keybindService = keybindService;
        this.taczCombatBridge = taczCombatBridge;
        this.crossServerService = crossServerService;
        this.placeholderResolver = placeholderResolver;
        List<String> sigPubKeys = plugin.getConfig().getStringList("module-signature-public-keys");
        if (sigPubKeys.isEmpty()) {
            // 向后兼容旧版单个字符串配置
            String legacy = plugin.getConfig().getString("module-signature-public-key", "");
            if (legacy != null && !legacy.isBlank()) {
                sigPubKeys = List.of(legacy);
            }
        }
        this.signatureVerifier = new ModuleSignatureVerifier(sigPubKeys, LOGGER);
    }

    // ─── 生命周期 ─────────────────────────────────────────────

    /**
     * 预扫描 modules/ 目录，返回发现的模块 id 集合（不做加载）。
     * 宿主在内置加载逻辑前调用此方法来判断哪些模块将由外部 Jar 接管。
     */
    public Set<String> scanAvailableModuleIds() {
        Set<String> ids = new HashSet<>();
        if (!modulesDir.isDirectory()) {
            return ids;
        }
        File[] jarFiles = modulesDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) {
            return ids;
        }
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                ids.add(descriptor.id());
            } catch (IOException | ModuleLoadException exception) {
                LOGGER.warning("预扫描模块 Jar 失败: " + jarFile.getName() + " | " + exception.getMessage());
            }
        }
        return ids;
    }

    /**
     * 扫描 modules/ 目录，解析描述符、验证密码、拓扑排序、加载和启用模块。
     */
    public LoadSummary loadAll() {
        if (packetBridge != null) {
            packetBridge.resetUiRegistrationCount();
        }
        if (!modulesDir.isDirectory()) {
            modulesDir.mkdirs();
            LOGGER.fine("已创建模块目录: " + modulesDir.getAbsolutePath());
        }

        // 0. 初始化全局桥接单例（即使 modules/ 为空或后续热加载也需要）
        initializeGlobalBridges();

        File[] jarFiles = modulesDir.isDirectory()
            ? modulesDir.listFiles((dir, name) -> name.endsWith(".jar"))
            : null;
        if (jarFiles == null || jarFiles.length == 0) {
            LOGGER.fine("模块目录为空，未加载任何模块。");
            return new LoadSummary(0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }

        // 1. 解析描述符
        Map<String, DiscoveredModule> discovered = new LinkedHashMap<>();
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                if (discovered.containsKey(descriptor.id())) {
                    LOGGER.warning("模块 id 冲突: " + descriptor.id()
                        + "（" + jarFile.getName() + " vs " + discovered.get(descriptor.id()).jarFile.getName() + "），跳过后者。");
                    continue;
                }
                discovered.put(descriptor.id(), new DiscoveredModule(descriptor, jarFile));
            } catch (IOException | ModuleLoadException exception) {
                LOGGER.warning("扫描模块 Jar 失败: " + jarFile.getName() + " | " + exception.getMessage());
            }
        }

        // 2. enabled 检查
        YamlConfiguration rootConfig = loadRootConfig();
        Map<String, DiscoveredModule> authorized = new LinkedHashMap<>();
        List<String> skippedModules = new ArrayList<>();
        List<String> enabledModules = new ArrayList<>();
        List<String> failedModules = new ArrayList<>();
        for (Map.Entry<String, DiscoveredModule> entry : discovered.entrySet()) {
            String id = entry.getKey();
            DiscoveredModule dm = entry.getValue();

            if (!isModuleEnabled(rootConfig, id)) {
                LOGGER.fine(dm.descriptor.name() + " 模块已在 config.yml 中关闭。");
                skippedModules.add(dm.descriptor.name());
                continue;
            }

            authorized.put(id, dm);
        }

        // 3. 拓扑排序
        List<String> sorted = topologicalSort(authorized);

        // 4. 按序加载与启用
        for (String id : sorted) {
            DiscoveredModule dm = authorized.get(id);
            if (dm == null) {
                continue;
            }
            try {
                if (loadAndEnable(dm)) {
                    enabledModules.add(dm.descriptor.name());
                } else {
                    skippedModules.add(dm.descriptor.name());
                }
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "加载模块 " + dm.descriptor.name() + " 失败", exception);
                failedModules.add(dm.descriptor.name());
                cleanupFailedModule(id);
            } catch (LinkageError error) {
                LOGGER.log(Level.SEVERE, "加载模块 " + dm.descriptor.name() + " 失败，缺少运行依赖或依赖版本不兼容", error);
                failedModules.add(dm.descriptor.name());
                cleanupFailedModule(id);
            }
        }

        return new LoadSummary(
            discovered.size(),
            enabledModules.size(),
            skippedModules.size(),
            failedModules.size(),
            packetBridge == null ? 0 : packetBridge.successfulUiRegistrationCount(),
            enabledModules,
            skippedModules,
            failedModules
        );
    }

    /**
     * 逆序禁用并卸载所有模块。
     */
    public void unloadAll() {
        List<String> ids = new ArrayList<>(modules.keySet());
        Collections.reverse(ids);
        for (String id : ids) {
            LoadedModule loaded = modules.get(id);
            if (loaded != null && loaded.isEnabled()) {
                disableModule(loaded);
            }
        }
        for (String id : ids) {
            LoadedModule loaded = modules.get(id);
            if (loaded != null) {
                closeClassLoader(loaded);
            }
        }
        modules.clear();
        clearAllRegistrations();
        if (attributeBridgeRegistry != null) {
            attributeBridgeRegistry.shutdown();
            attributeBridgeRegistry = null;
        }
        ScriptConditionServices.reset();
        ariaBridge = null;
        scriptConditionEvaluator = null;
        if (accountTypeService != null) {
            org.bukkit.event.HandlerList.unregisterAll(accountTypeService);
            accountTypeService.clearCache();
            accountTypeService = null;
        }
    }

    /**
     * 重载指定模块（不卸载 ClassLoader）。
     */
    public boolean reloadModule(String moduleId) {
        LoadedModule loaded = modules.get(moduleId);
        if (loaded == null || !loaded.isEnabled()) {
            LOGGER.warning("模块未加载或未启用，无法重载: " + moduleId);
            return false;
        }
        try {
            DefaultModuleContext context = loaded.context();
            if (context != null) {
                context.unregisterKeybindHandlers();
            }
            removePacketHandlers(moduleId);
            removeInitializedHandlers(moduleId);
            removeCapabilities(moduleId);
            loaded.instance().onReload();
            LOGGER.info(loaded.descriptor().name() + " 模块已重载。");
            return true;
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, loaded.descriptor().name() + " 模块重载失败", exception);
            return false;
        }
    }

    /**
     * 热卸载模块：执行 {@code onDisable} → 移除注册（命令/包/能力）→ 关闭 ClassLoader。
     * 卸载后 jar 仍在 {@code modules/} 中，可通过 {@link #loadModuleById(String)} 重新启用。
     *
     * @return 是否成功卸载
     */
    public boolean unloadModule(String moduleId) {
        LoadedModule loaded = modules.get(moduleId);
        if (loaded == null) {
            LOGGER.warning("模块未加载，无法卸载: " + moduleId);
            return false;
        }
        // 检查反向依赖：若其他已启用模块声明依赖它，则禁止卸载
        List<String> dependents = new ArrayList<>();
        for (LoadedModule other : modules.values()) {
            if (other == loaded || !other.isEnabled()) continue;
            if (other.descriptor().depends().contains(moduleId)) {
                dependents.add(other.descriptor().id());
            }
        }
        if (!dependents.isEmpty()) {
            LOGGER.warning("无法卸载 " + moduleId + "，仍被依赖于: " + String.join(", ", dependents));
            return false;
        }

        if (loaded.isEnabled()) {
            disableModule(loaded);
        } else {
            clearModuleRegistrations(loaded);
        }
        modules.remove(moduleId);
        closeClassLoader(loaded);
        LOGGER.info(loaded.descriptor().name() + " 模块已卸载，ClassLoader 已释放。");
        return true;
    }

    /**
     * 加载云端模块：从内存中的 jar 字节数组直接加载并启用。
     *
     * @param jarBytes 解密后的 jar 字节数组
     * @return 是否成功加载并启用
     */
    public boolean loadCloudModule(byte[] jarBytes) {
        initializeGlobalBridges();
        try {
            ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jarBytes);
            String moduleId = descriptor.id();
            if (modules.containsKey(moduleId)) {
                LOGGER.info("云端模块 " + moduleId + " 已加载，跳过。");
                return false;
            }
            if (!isModuleEnabled(loadRootConfig(), moduleId)) {
                LOGGER.fine("云端模块 " + moduleId + " 已在 config.yml 中关闭，跳过加载。");
                return false;
            }
            if (packetBridge != null) {
                packetBridge.resetUiRegistrationCount();
            }
            boolean ok = loadAndEnable(new DiscoveredModule(descriptor, jarBytes));
            if (!ok) {
                cleanupFailedModule(moduleId);
            }
            return ok;
        } catch (Exception | LinkageError exception) {
            LOGGER.log(Level.SEVERE, "加载云端模块失败", exception);
            return false;
        }
    }

    /**
     * 热加载模块：从 {@code modules/} 目录扫描指定 id 的 jar 并加载启用。
     * 若该模块已加载，返回 false 并提示。
     *
     * @return 是否成功加载并启用
     */
    public boolean loadModuleById(String moduleId) {
        if (modules.containsKey(moduleId)) {
            LOGGER.warning("模块已加载: " + moduleId + "，如需重启请使用 reload 或 unload + load。");
            return false;
        }
        if (!isModuleEnabled(loadRootConfig(), moduleId)) {
            LOGGER.warning("模块 " + moduleId + " 已在 config.yml 中关闭，无法热加载。");
            return false;
        }
        initializeGlobalBridges();
        if (!modulesDir.isDirectory()) {
            LOGGER.warning("模块目录不存在: " + modulesDir.getAbsolutePath());
            return false;
        }
        File[] jarFiles = modulesDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) {
            return false;
        }
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                if (!moduleId.equals(descriptor.id())) continue;

                // packetBridge UI 计数复位（仅用于本次加载日志）
                if (packetBridge != null) {
                    packetBridge.resetUiRegistrationCount();
                }
                try {
                    boolean ok = loadAndEnable(new DiscoveredModule(descriptor, jarFile));
                    if (!ok) {
                        cleanupFailedModule(descriptor.id());
                    }
                    return ok;
                } catch (Exception | LinkageError exception) {
                    LOGGER.log(Level.SEVERE, "热加载模块 " + descriptor.name() + " 失败", exception);
                    cleanupFailedModule(descriptor.id());
                    return false;
                }
            } catch (IOException | ModuleLoadException exception) {
                LOGGER.warning("解析模块 Jar 失败: " + jarFile.getName() + " | " + exception.getMessage());
            }
        }
        LOGGER.warning("modules/ 目录未找到模块 id: " + moduleId);
        return false;
    }

    private volatile List<String> discoverableCache;
    private volatile long discoverableCacheTime;
    private static final long DISCOVERABLE_CACHE_TTL_MS = 30_000;

    /**
     * 列出 {@code modules/} 中存在但当前未加载的模块 id（用于 tab 补全）。
     * 结果缓存 30 秒以避免频繁扫描 JAR。
     */
    public List<String> discoverableModuleIds() {
        long now = System.currentTimeMillis();
        if (discoverableCache != null && (now - discoverableCacheTime) < DISCOVERABLE_CACHE_TTL_MS) {
            return discoverableCache;
        }
        List<String> ids = new ArrayList<>();
        if (!modulesDir.isDirectory()) return ids;
        File[] jarFiles = modulesDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) return ids;
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                if (!modules.containsKey(descriptor.id())) {
                    ids.add(descriptor.id());
                }
            } catch (IOException | ModuleLoadException ignored) {
            }
        }
        discoverableCache = List.copyOf(ids);
        discoverableCacheTime = now;
        return discoverableCache;
    }

    /**
     * 重载所有已启用的模块。
     */
    public void reloadAll() {
        for (LoadedModule loaded : modules.values()) {
            if (loaded.isEnabled()) {
                reloadModule(loaded.descriptor().id());
            }
        }
    }

    // ─── 查询 ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public <T extends AXSModule> Optional<T> getModule(Class<T> moduleClass) {
        for (LoadedModule loaded : modules.values()) {
            if (loaded.isEnabled() && moduleClass.isInstance(loaded.instance())) {
                return Optional.of((T) loaded.instance());
            }
        }
        return Optional.empty();
    }

    public Optional<AXSModule> getModule(String moduleId) {
        LoadedModule loaded = modules.get(moduleId);
        if (loaded != null && loaded.isEnabled()) {
            return Optional.of(loaded.instance());
        }
        return Optional.empty();
    }

    public List<ModuleDescriptor> listModules() {
        List<ModuleDescriptor> descriptors = new ArrayList<>();
        for (LoadedModule loaded : modules.values()) {
            descriptors.add(loaded.descriptor());
        }
        return descriptors;
    }

    public boolean isModuleLoaded(String moduleId) {
        LoadedModule loaded = modules.get(moduleId);
        return loaded != null && loaded.isEnabled();
    }

    public int countEnabled() {
        int count = 0;
        for (LoadedModule loaded : modules.values()) {
            if (loaded.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public Optional<ModuleCommandHandler> getCommandHandler(String moduleId) {
        return Optional.ofNullable(commandHandlers.get(moduleId));
    }

    public List<String> externalModuleIds() {
        List<String> ids = new ArrayList<>();
        for (LoadedModule loaded : modules.values()) {
            if (loaded.isEnabled()) {
                ids.add(loaded.descriptor().id());
            }
        }
        return ids;
    }

    /**
     * 获取当前已加载的云端模块ID列表（从内存 jarBytes 加载的模块）。
     */
    public List<String> getLoadedCloudModuleIds() {
        List<String> ids = new ArrayList<>();
        for (LoadedModule loaded : modules.values()) {
            if (loaded.jarBytes() != null) {
                ids.add(loaded.descriptor().id());
            }
        }
        return ids;
    }

    public Map<String, ModuleCommandHandler> commandHandlerMap() {
        return Collections.unmodifiableMap(commandHandlers);
    }

    public Map<String, Boolean> moduleStatusMap() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (LoadedModule loaded : modules.values()) {
            status.put(loaded.descriptor().name(), loaded.isEnabled());
        }
        return status;
    }

    // ─── 内部方法 ─────────────────────────────────────────────

    private boolean loadAndEnable(DiscoveredModule dm) throws Exception {
        ModuleDescriptor descriptor = dm.descriptor;
        File jarFile = dm.jarFile;
        byte[] jarBytes = dm.jarBytes;

        // 检查外部插件依赖
        for (String externalPlugin : descriptor.externalDepends()) {
            if (Bukkit.getPluginManager().getPlugin(externalPlugin) == null) {
                LOGGER.warning(descriptor.name() + " 模块需要 " + externalPlugin + " 插件，已跳过加载。");
                return false;
            }
        }

        // 检查 AXS 模块依赖
        for (String depId : descriptor.depends()) {
            if (!isModuleLoaded(depId)) {
                LOGGER.warning(descriptor.name() + " 模块依赖 " + depId + " 未就绪，已跳过加载。");
                return false;
            }
        }

        // 数字签名验证（如配置了公钥则强制校验）
        if (!signatureVerifier.verify(descriptor)) {
            return false;
        }

        // 创建 ClassLoader（本地 jar 或内存 bytes）
        ClassLoader classLoader;
        if (jarBytes != null) {
            classLoader = new ByteArrayModuleClassLoader(descriptor.id(), jarBytes, plugin.getClass().getClassLoader());
        } else {
            URL jarUrl = jarFile.toURI().toURL();
            classLoader = new ModuleClassLoader(descriptor.id(), jarUrl, plugin.getClass().getClassLoader());
        }

        // 实例化模块主类
        Class<?> mainClass = classLoader.loadClass(descriptor.mainClass());
        if (!AXSModule.class.isAssignableFrom(mainClass)) {
            if (classLoader instanceof java.io.Closeable closeable) {
                closeable.close();
            }
            throw new ModuleLoadException(descriptor.mainClass() + " 未实现 AXSModule 接口");
        }
        AXSModule instance = (AXSModule) mainClass.getDeclaredConstructor().newInstance();

        // 构建上下文
        DefaultModuleContext context = new DefaultModuleContext(
            plugin,
            descriptor.id(),
            packetBridge,
            clientBridge,
            itemStackBridge,
            propBridge,
            packetGuard,
            this,
            classLoader,
            keybindService,
            taczCombatBridge,
            crossServerService,
            placeholderResolver
        );

        LoadedModule loaded = jarBytes != null
            ? new LoadedModule(descriptor, instance, classLoader, jarBytes)
            : new LoadedModule(descriptor, instance, classLoader, jarFile);
        loaded.setContext(context);
        modules.put(descriptor.id(), loaded);

        // 在 onEnable 之前注册并跑模块的 ConfigSpec 诊断（dry-run，不动玩家 yml）
        try {
            if (plugin instanceof xuanmo.arcartxsuite.ArcartXSuitePlugin axsPlugin) {
                List<xuanmo.arcartxsuite.api.config.ModuleConfigSpec> specs = instance.configSpecs();
                if (specs != null && !specs.isEmpty()) {
                    axsPlugin.registerModuleConfigSpecs(descriptor.id(), specs, classLoader);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warning("模块 " + descriptor.name() + " 配置诊断异常: " + exception.getMessage());
        }

        // 启用
        boolean success = instance.onEnable(context);
        if (success) {
            loaded.setEnabled(true);
            if (instance instanceof ModuleCommandHandler handler) {
                commandHandlers.put(handler.commandId(), handler);
                for (String alias : handler.commandAliases()) {
                    commandHandlers.put(alias.toLowerCase(java.util.Locale.ROOT), handler);
                }
            } else {
                commandHandlers.put(descriptor.id(), new DefaultModuleCommandHandler(descriptor));
            }
            LOGGER.fine(descriptor.name() + " v" + descriptor.version() + " 模块已启用。");
            return true;
        } else {
            LOGGER.warning(descriptor.name() + " 模块 onEnable 返回 false，已清理并卸载。");
            cleanupFailedModule(descriptor.id());
            return false;
        }
    }

    private void disableModule(LoadedModule loaded) {
        try {
            loaded.instance().onDisable();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, loaded.descriptor().name() + " 模块 onDisable 异常", exception);
        } finally {
            clearModuleRegistrations(loaded);
            loaded.setEnabled(false);
            LOGGER.info(loaded.descriptor().name() + " 模块已禁用。");
        }
    }

    private void closeClassLoader(LoadedModule loaded) {
        try {
            if (loaded.classLoader() instanceof java.io.Closeable closeable) {
                closeable.close();
            }
        } catch (IOException exception) {
            LOGGER.warning("关闭模块 ClassLoader 失败: " + loaded.descriptor().id() + " | " + exception.getMessage());
        }
    }

    private void cleanupFailedModule(String moduleId) {
        LoadedModule loaded = modules.remove(moduleId);
        if (loaded == null) {
            return;
        }
        clearModuleRegistrations(loaded);
        try {
            loaded.instance().onDisable();
        } catch (Exception exception) {
            LOGGER.warning("清理失败模块 " + loaded.descriptor().name() + " 时发生异常: " + exception.getMessage());
        } catch (LinkageError error) {
            LOGGER.warning("清理失败模块 " + loaded.descriptor().name() + " 时依赖不可用: " + error.getMessage());
        }
        closeClassLoader(loaded);
    }

    private void clearModuleRegistrations(LoadedModule loaded) {
        String moduleId = loaded.descriptor().id();
        DefaultModuleContext context = loaded.context();
        if (context != null) {
            context.unbindCommands();
            context.unregisterKeybindHandlers();
        }
        removeCommandHandlers(loaded);
        removePacketHandlers(moduleId);
        removeInitializedHandlers(moduleId);
        removeCapabilities(moduleId);
        if (plugin instanceof xuanmo.arcartxsuite.ArcartXSuitePlugin axsPlugin) {
            axsPlugin.unregisterModuleConfigSpecs(moduleId);
        }
    }

    private void removeCommandHandlers(LoadedModule loaded) {
        AXSModule instance = loaded.instance();
        if (instance instanceof ModuleCommandHandler handler) {
            commandHandlers.entrySet().removeIf(entry -> entry.getValue() == handler);
        } else {
            commandHandlers.remove(loaded.descriptor().id());
        }
    }

    private void clearAllRegistrations() {
        packetHandlers.clear();
        initializedHandlers.clear();
        commandHandlers.clear();
        moduleCapabilityTypes.clear();
        capabilities.clear();
        purgeables.clear();
        migratables.clear();
    }

    private List<String> topologicalSort(Map<String, DiscoveredModule> modules) {
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        for (Map.Entry<String, DiscoveredModule> entry : modules.entrySet()) {
            Set<String> deps = new HashSet<>();
            for (String dep : entry.getValue().descriptor.depends()) {
                if (modules.containsKey(dep)) {
                    deps.add(dep);
                }
            }
            for (String dep : entry.getValue().descriptor.softDepends()) {
                if (modules.containsKey(dep)) {
                    deps.add(dep);
                }
            }
            dependencyGraph.put(entry.getKey(), deps);
        }

        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String id : dependencyGraph.keySet()) {
            if (!visited.contains(id)) {
                topologicalVisit(id, dependencyGraph, visited, visiting, sorted);
            }
        }
        return sorted;
    }

    private void topologicalVisit(String id, Map<String, Set<String>> graph, Set<String> visited, Set<String> visiting, List<String> sorted) {
        if (visiting.contains(id)) {
            LOGGER.warning("检测到模块循环依赖: " + id + "，已强制打断。");
            return;
        }
        if (visited.contains(id)) {
            return;
        }
        visiting.add(id);
        Set<String> deps = graph.get(id);
        if (deps != null) {
            for (String dep : deps) {
                topologicalVisit(dep, graph, visited, visiting, sorted);
            }
        }
        visiting.remove(id);
        visited.add(id);
        sorted.add(id);
    }

    // ─── 全局桥接初始化 ──────────────────────────────────────────

    private void initializeGlobalBridges() {
        // 物品来源注册表
        if (itemSourceRegistry == null) {
            itemSourceRegistry = new DefaultItemSourceRegistry(plugin);
        }
        itemSourceRegistry.initialize();

        // 物品匹配器
        itemMatcherSupport = new ItemMatcherSupport(
            itemSourceRegistry::mythicItemId,
            itemSourceRegistry::neigeItemId,
            itemSourceRegistry::overtureItemId
        );

        // 全局货币管理器
        if (currencyBridgeManager == null) {
            currencyBridgeManager = new CurrencyBridgeManager(plugin, Map.of(), placeholderResolver);
        }
        YamlConfiguration rootConfig = loadRootConfig();
        currencyBridgeManager.registerCurrencies(parseGlobalCurrencies(rootConfig));
        currencyBridgeManager.initialize();

        // 全局属性桥接注册表
        if (attributeBridgeRegistry == null) {
            attributeBridgeRegistry = new DefaultAttributeBridgeRegistry(plugin);
        }
        org.bukkit.configuration.ConfigurationSection attrBridges = rootConfig.getConfigurationSection("attribute-bridges");
        boolean apEnabled = attrBridges == null || attrBridges.getBoolean("attributeplus", true);
        boolean caEnabled = attrBridges == null || attrBridges.getBoolean("craneattribute", true);
        boolean mlEnabled = attrBridges == null || attrBridges.getBoolean("mythiclib", true);
        boolean symEnabled = attrBridges == null || attrBridges.getBoolean("symphony", true);
        attributeBridgeRegistry.setSourceEnabled(apEnabled, caEnabled, mlEnabled, symEnabled);
        attributeBridgeRegistry.initialize();

        if (ariaBridge == null) {
            ariaBridge = new DefaultAriaBridge(plugin);
        }
        ariaBridge.initialize();
        scriptConditionEvaluator = new DefaultScriptConditionEvaluator(ariaBridge, placeholderResolver);
        ScriptConditionServices.install(scriptConditionEvaluator);

        // 全局事件总线
        if (!capabilities.containsKey(xuanmo.arcartxsuite.api.capability.EventBusCapability.class)) {
            capabilities.put(xuanmo.arcartxsuite.api.capability.EventBusCapability.class,
                new SimpleEventBus(plugin.getLogger()));
        }

        // 统一账号识别服务（微软正版 / LittleSkin / 离线）
        ensureAccountTypeService();
    }

    private static Map<String, CurrencyDefinition> parseGlobalCurrencies(YamlConfiguration config) {
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("currencies");
        LinkedHashMap<String, CurrencyDefinition> result = new LinkedHashMap<>();
        if (section == null) {
            result.put("money", new CurrencyDefinition("money", "vault", "金币", 2, "", "", ""));
            result.put("points", new CurrencyDefinition("points", "playerpoints", "点券", 0, "", "", ""));
            return result;
        }
        for (String rawId : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) {
                continue;
            }
            String id = rawId.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            result.put(id, new CurrencyDefinition(
                id,
                child.getString("provider", "vault").trim().toLowerCase(java.util.Locale.ROOT),
                child.getString("display-name", rawId),
                child.getInt("precision", 2),
                child.getString("balance-placeholder", ""),
                child.getString("withdraw-command", ""),
                child.getString("deposit-command", "")
            ));
        }
        result.putIfAbsent("money", new CurrencyDefinition("money", "vault", "金币", 2, "", "", ""));
        result.putIfAbsent("points", new CurrencyDefinition("points", "playerpoints", "点券", 0, "", "", ""));
        return result;
    }

    /** 获取全局物品来源注册表 */
    public ItemSourceRegistry itemSourceRegistry() {
        return itemSourceRegistry;
    }

    /** 获取全局物品匹配器 */
    public ItemMatcherAPI itemMatcher() {
        return itemMatcherSupport;
    }

    /** 获取全局货币管理器 */
    public CurrencyBridgeManager currencyManager() {
        return currencyBridgeManager;
    }

    /** 获取全局属性桥接注册表 */
    public DefaultAttributeBridgeRegistry attributeBridge() {
        return attributeBridgeRegistry;
    }

    /** 获取 Aria 脚本桥接 */
    public AriaBridge ariaBridge() {
        if (ariaBridge == null) {
            ariaBridge = new DefaultAriaBridge(plugin);
            ariaBridge.initialize();
        }
        return ariaBridge;
    }

    /** 获取统一条件评估器 */
    public ScriptConditionEvaluator scriptConditionEvaluator() {
        if (scriptConditionEvaluator == null) {
            scriptConditionEvaluator = new DefaultScriptConditionEvaluator(ariaBridge(), placeholderResolver);
            ScriptConditionServices.install(scriptConditionEvaluator);
        }
        return scriptConditionEvaluator;
    }

    /**
     * 懒初始化统一账号识别服务（幂等）。
     * 读取 config.yml 的 {@code account-type} 节并注册 PreLogin 预解析监听器。
     */
    private void ensureAccountTypeService() {
        if (accountTypeService != null) {
            return;
        }
        YamlConfiguration rootConfig = loadRootConfig();
        org.bukkit.configuration.ConfigurationSection section = rootConfig.getConfigurationSection("account-type");
        boolean enableLookup = section == null || section.getBoolean("enable-mojang-lookup", true);
        int timeoutMs = section == null ? 10000 : section.getInt("mojang-timeout-ms", 10000);
        boolean debug = section != null && section.getBoolean("debug", false);
        String proxyHost = section == null ? null : section.getString("mojang-proxy-host", null);
        int proxyPort = section == null ? 0 : section.getInt("mojang-proxy-port", 0);
        // 仅混合登录模式（yggdrasil-source 含 ?mixed）时启用本地代理权威查询，
        // 非混合服务器传 0 跳过，避免每次都尝试连接不存在的本地代理。
        String yggdrasilSource = rootConfig.getString("auth.yggdrasil-source", "");
        int mixedProxyPort = (yggdrasilSource != null && yggdrasilSource.contains("?mixed"))
            ? rootConfig.getInt("auth.mixed-proxy-port", 25599)
            : 0;
        accountTypeService = new AccountTypeServiceImpl(plugin.getLogger(), enableLookup, timeoutMs, debug, proxyHost, proxyPort, mixedProxyPort);
        Bukkit.getPluginManager().registerEvents(accountTypeService, plugin);
        LOGGER.fine("统一账号识别服务已就绪 | Mojang查询=" + enableLookup
            + " | 超时=" + timeoutMs + "ms | 混合代理端口=" + mixedProxyPort
            + " | authlib-injector=" + accountTypeService.isAuthlibInjectorLoaded());
    }

    /** 获取宿主统一账号识别服务（永不为 null） */
    public AccountTypeService accountTypeService() {
        ensureAccountTypeService();
        return accountTypeService;
    }

    private YamlConfiguration loadRootConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(configFile);
    }

    private boolean isModuleEnabled(YamlConfiguration config, String moduleId) {
        return config.getBoolean("modules." + moduleId + ".enabled", false);
    }

    public InputStream openProtectedResource(String moduleId, String resourcePath, ClassLoader loader) throws IOException {
        ClassLoader effective = loader != null ? loader : getClass().getClassLoader();
        InputStream input = effective.getResourceAsStream(resourcePath);
        if (input != null) {
            return input;
        }
        throw new IOException("未找到资源: " + resourcePath);
    }

    void recordUiRegistration(String moduleId, String uiId) {
        LOGGER.fine("模块 " + moduleId + " 注册/重载 UI: " + uiId);
    }

    // ─── 客户端事件路由 ────────────────────────────────────────

    /**
     * 注册客户端包处理器（由 DefaultModuleContext 调用）。
     */
    void registerClientPacketHandler(String moduleId, ClientPacketHandler handler, int priority) {
        packetHandlers.add(new PrioritizedPacketHandler(moduleId, handler, priority));
        packetHandlers.sort(Comparator.comparingInt(PrioritizedPacketHandler::priority));
    }

    /**
     * 注册客户端初始化处理器（由 DefaultModuleContext 调用）。
     */
    void registerClientInitializedHandler(String moduleId, ClientInitializedHandler handler) {
        initializedHandlers.add(new PrioritizedInitializedHandler(moduleId, handler));
    }

    /**
     * 移除指定模块注册的所有客户端初始化处理器。
     */
    void removeInitializedHandlers(String moduleId) {
        initializedHandlers.removeIf(ih -> ih.moduleId().equals(moduleId));
    }

    /**
     * 移除指定模块注册的所有客户端包处理器。
     */
    void removePacketHandlers(String moduleId) {
        packetHandlers.removeIf(ph -> ph.moduleId().equals(moduleId));
    }

    /**
     * 路由客户端自定义包到已注册的处理器。
     * 按优先级遍历，第一个消费的处理器短路返回 true。
     *
     * @return true 表示包已被某个处理器消费
     */
    public boolean routeClientPacket(Player player, String packetId, List<String> data) {
        for (PrioritizedPacketHandler ph : packetHandlers) {
            try {
                if (ph.handler().handleClientPacket(player, packetId, data)) {
                    return true;
                }
            } catch (Exception exception) {
                LOGGER.warning("模块 " + ph.moduleId() + " 处理客户端包异常: " + exception.getMessage());
            }
        }
        return false;
    }

    /**
     * 通知所有已注册的客户端初始化处理器。
     */
    public void routeClientInitialized(Player player) {
        for (PrioritizedInitializedHandler ih : initializedHandlers) {
            try {
                ih.handler().onClientInitialized(player);
            } catch (Exception exception) {
                LOGGER.warning("模块 " + ih.moduleId() + " 客户端初始化处理器异常: " + exception.getMessage());
            }
        }
    }

    // ─── Capability 注册表 ────────────────────────────────────

    /**
     * 注册模块提供的能力实例（由 DefaultModuleContext 调用）。
     */
    @SuppressWarnings("unchecked")
    <T> void registerCapability(String moduleId, Class<T> capabilityType, T implementation) {
        capabilities.put(capabilityType, implementation);
        moduleCapabilityTypes.computeIfAbsent(moduleId, ignored -> new CopyOnWriteArrayList<>()).add(capabilityType);
        if (implementation instanceof xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable p) {
            purgeables.removeIf(existing -> existing.moduleId().equalsIgnoreCase(p.moduleId()));
            purgeables.add(p);
        }
        if (implementation instanceof xuanmo.arcartxsuite.api.capability.DatabaseMigratable m) {
            migratables.removeIf(existing -> existing.moduleId().equalsIgnoreCase(m.moduleId()));
            migratables.add(m);
        }
    }

    /**
     * 获取所有支持一键数据迁移的模块 ID。
     */
    public List<String> migratableModuleIds() {
        List<String> ids = new ArrayList<>();
        for (var m : migratables) {
            ids.add(m.moduleId());
        }
        return ids;
    }

    /**
     * 获取指定模块的迁移 Capability。
     */
    public Optional<xuanmo.arcartxsuite.api.capability.DatabaseMigratable> getMigratable(String moduleId) {
        for (var m : migratables) {
            if (m.moduleId().equalsIgnoreCase(moduleId)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    /**
     * 删除指定玩家在所有已注册模块中的数据。
     *
     * @return 模块ID -> 受影响行数
     */
    public Map<String, Integer> purgePlayerData(java.util.UUID playerUuid) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var p : purgeables) {
            try {
                int rows = p.purgePlayerData(playerUuid);
                result.put(p.moduleId(), rows);
            } catch (Exception e) {
                LOGGER.warning("清除玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                result.put(p.moduleId(), -1);
            }
        }
        return result;
    }

    /**
     * 删除指定玩家在指定模块中的数据。
     *
     * @return 模块ID -> 受影响行数（仅一条）；模块不存在时返回空 map
     */
    public Map<String, Integer> purgePlayerData(java.util.UUID playerUuid, String moduleId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var p : purgeables) {
            if (p.moduleId().equalsIgnoreCase(moduleId)) {
                try {
                    int rows = p.purgePlayerData(playerUuid);
                    result.put(p.moduleId(), rows);
                } catch (Exception e) {
                    LOGGER.warning("清除玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                    result.put(p.moduleId(), -1);
                }
                return result;
            }
        }
        return result;
    }

    /**
     * 清空所有已注册模块中的全部玩家数据。
     *
     * @return 模块ID -> 受影响行数
     */
    public Map<String, Integer> purgeAllPlayerData() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var p : purgeables) {
            try {
                int rows = p.purgeAllPlayerData();
                result.put(p.moduleId(), rows);
            } catch (Exception e) {
                LOGGER.warning("清空玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                result.put(p.moduleId(), -1);
            }
        }
        return result;
    }

    /**
     * 清空指定模块中的全部玩家数据。
     *
     * @return 模块ID -> 受影响行数；模块不存在时返回空 map
     */
    public Map<String, Integer> purgeAllPlayerData(String moduleId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var p : purgeables) {
            if (p.moduleId().equalsIgnoreCase(moduleId)) {
                try {
                    int rows = p.purgeAllPlayerData();
                    result.put(p.moduleId(), rows);
                } catch (Exception e) {
                    LOGGER.warning("清空玩家数据失败: module=" + p.moduleId() + " error=" + e.getMessage());
                    result.put(p.moduleId(), -1);
                }
                return result;
            }
        }
        return result;
    }

    /**
     * 获取所有已注册 PlayerDataPurgeable 的模块 ID 列表。
     */
    public List<String> purgeableModuleIds() {
        List<String> ids = new java.util.ArrayList<>(purgeables.size());
        for (var p : purgeables) {
            ids.add(p.moduleId());
        }
        return ids;
    }

    /**
     * 按类型查找能力实例。
     */
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Class<T> capabilityType) {
        return (T) capabilities.get(capabilityType);
    }

    /**
     * 移除指定模块注册的所有能力。
     * 清理 purgeables 列表和 capabilities 表中由该模块注册的实例。
     */
    void removeCapabilities(String moduleId) {
        purgeables.removeIf(p -> p.moduleId().equalsIgnoreCase(moduleId));
        migratables.removeIf(m -> m.moduleId().equalsIgnoreCase(moduleId));
        CopyOnWriteArrayList<Class<?>> types = moduleCapabilityTypes.remove(moduleId);
        if (types != null) {
            for (Class<?> capabilityType : types) {
                capabilities.remove(capabilityType);
            }
        }
    }

    /**
     * 带优先级的客户端包处理器包装。
     */
    record PrioritizedPacketHandler(String moduleId, ClientPacketHandler handler, int priority) {
    }

    record PrioritizedInitializedHandler(String moduleId, ClientInitializedHandler handler) {
    }

    private record DiscoveredModule(ModuleDescriptor descriptor, File jarFile, byte[] jarBytes) {
        DiscoveredModule(ModuleDescriptor descriptor, File jarFile) {
            this(descriptor, jarFile, null);
        }

        DiscoveredModule(ModuleDescriptor descriptor, byte[] jarBytes) {
            this(descriptor, null, jarBytes);
        }
    }

    public record LoadSummary(
        int discoveredCount,
        int enabledCount,
        int skippedCount,
        int failedCount,
        int uiRegistrationCount,
        List<String> enabledModules,
        List<String> skippedModules,
        List<String> failedModules
    ) {
        public LoadSummary {
            enabledModules = List.copyOf(enabledModules);
            skippedModules = List.copyOf(skippedModules);
            failedModules = List.copyOf(failedModules);
        }
    }
}
