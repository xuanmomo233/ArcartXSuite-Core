package xuanmo.arcartxsuite;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.config.ConfigDiagnosisReport;
import xuanmo.arcartxsuite.lifecycle.BridgeLifecycleManager;
import xuanmo.arcartxsuite.lifecycle.ClientEventLifecycleManager;
import xuanmo.arcartxsuite.api.config.ConfigIssueSeverity;
import xuanmo.arcartxsuite.api.config.ConfigSyncSpec;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.command.ArcartXSuiteCommand;
import xuanmo.arcartxsuite.config.diagnostic.ConfigDiagnosisStore;
import xuanmo.arcartxsuite.config.diagnostic.ConfigDiagnosticEngine;
import xuanmo.arcartxsuite.config.diagnostic.RetentionCleaner;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import xuanmo.arcartxsuite.chat.ChatSignBypassService;
import xuanmo.arcartxsuite.crossserver.CrossServerService;
import xuanmo.arcartxsuite.keybind.KeybindService;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.module.PluginConsoleLogger;
import xuanmo.arcartxsuite.placeholder.PlaceholderResolverImpl;
import xuanmo.arcartxsuite.module.VersionCheckService;
import xuanmo.arcartxsuite.util.MohistCompat;

/**
 * ArcartXSuite 宿主插件入口（开源版）。
 * <p>
 * 仅负责核心基础设施（Bridges、ModuleRegistry）的生命周期，
 * 所有业务逻辑均通过 modules/*.jar 独立模块加载，宿主不再直接持有任何模块服务。
 */
public class ArcartXSuitePlugin extends JavaPlugin {

    private static final String AUTHOR_NAME = "墨墨墨 Q";
    private static final String DISPLAY_NAME = "ArcartXSuite";
    private static final String CONSOLE_PREFIX =
        ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;

    private ModuleRegistry moduleRegistry;
    private PlaceholderResolverImpl placeholderResolver;
    private KeybindService keybindService;
    private CrossServerService crossServerService;
    private ConfigDiagnosticEngine configDiagnosticEngine;
    private ConfigDiagnosisStore configDiagnosisStore;
    private VersionCheckService versionCheckService;
    private ChatSignBypassService chatSignBypassService;
    private BridgeLifecycleManager bridgeLifecycleManager;
    private ClientEventLifecycleManager clientEventLifecycleManager;
    /** 宿主自身的 spec（config.yml） */
    private final List<ModuleConfigSpec> hostConfigSpecs = new ArrayList<>();
    /** 外部模块提交的 spec： ownerId -> (spec, classLoader) */
    private final java.util.LinkedHashMap<String, ModuleSpecRegistration> moduleConfigSpecs = new java.util.LinkedHashMap<>();

    private record ModuleSpecRegistration(ModuleConfigSpec spec, ClassLoader classLoader) {}

    private PluginConsoleLogger consoleLogger;

    @Override
    public java.util.logging.Logger getLogger() {
        if (consoleLogger == null) {
            consoleLogger = new PluginConsoleLogger(getDescription() == null ? "ArcartXSuite" : getDescription().getName(), null);
        }
        return consoleLogger;
    }

    @Override
    public void onEnable() {
        ensureRootConfigExists();
        reloadConfig();
        printStartupBanner();
        consoleInfo("欢迎使用 " + DISPLAY_NAME);
        if (MohistCompat.isMohist()) {
            consoleInfo(ChatColor.YELLOW + "检测到 Mohist 混合端，已启用兼容模式");
        }

        // 屏蔽 PlaceholderAPI 的 "Successfully registered internal expansion" 噪音日志
        suppressPlaceholderApiNoise();

        // 0. 初始化智能配置诊断系统
        initConfigDiagnostic();

        // 3. Bridges
        bridgeLifecycleManager = new BridgeLifecycleManager(this);
        if (!bridgeLifecycleManager.initialize(
            getConfig().getBoolean("tacz-compat.enabled", true),
            getConfig().getBoolean("tacz-compat.debug", false)
        )) {
            consoleError("ArcartX 桥接初始化失败，已禁用插件。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 4. 全局按键注册
        keybindService = new KeybindService(this, bridgeLifecycleManager.propBridge());
        keybindService.initialize(getConfig());

        crossServerService = new CrossServerService(this);
        crossServerService.start();

        // 5. 客户端事件转发到 ModuleRegistry
        clientEventLifecycleManager = new ClientEventLifecycleManager(this, () -> moduleRegistry);
        clientEventLifecycleManager.start();

        // 7.5 统一 PAPI 解析器
        placeholderResolver = new PlaceholderResolverImpl();

        // 8. ModuleRegistry：扫描并加载所有外部模块
        moduleRegistry = new ModuleRegistry(
            this,
            new File(getDataFolder(), "modules"),
            bridgeLifecycleManager.packetBridge(),
            bridgeLifecycleManager.clientBridge(),
            bridgeLifecycleManager.itemStackBridge(),
            bridgeLifecycleManager.propBridge(),
            null, // 开源版不含 ClientPacketGuard 实现
            keybindService,
            bridgeLifecycleManager.taczCombatBridge(),
            crossServerService,
            placeholderResolver
        );
        ModuleRegistry.LoadSummary summary = moduleRegistry.loadAll();
        consoleInfo(
            "模块加载完成: 发现 " + summary.discoveredCount()
                + " | 已启用 " + summary.enabledCount()
                + " | 已跳过 " + summary.skippedCount()
                + " | 失败 " + summary.failedCount()
        );
        if (!summary.enabledModules().isEmpty()) {
            consoleInfo("启用模块: " + String.join(", ", summary.enabledModules()));
        }
        if (!summary.skippedModules().isEmpty()) {
            consoleInfo(ChatColor.GRAY + "跳过模块: " + String.join(", ", summary.skippedModules()));
        }
        if (!summary.failedModules().isEmpty()) {
            consoleWarn("失败模块: " + String.join(", ", summary.failedModules()));
        }

        // 7. 聊天签名绕过（Paper 1.21+ 混合登录兼容）
        boolean chatSignBypassEnabled = getConfig().getBoolean("chat-sign-bypass.enabled", true);
        boolean chatSignBypassOnlyNonPremium = getConfig().getBoolean("chat-sign-bypass.only-for-non-premium", true);
        chatSignBypassService = new ChatSignBypassService(
            this, chatSignBypassEnabled, chatSignBypassOnlyNonPremium,
            moduleRegistry.accountTypeService()
        );
        chatSignBypassService.initialize();

        // 7. 注册主命令
        PluginCommand command = getCommand("arcartxsuite");
        if (command != null) {
            ArcartXSuiteCommand handler = new ArcartXSuiteCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        // 8. 跑一轮全量诊断（只报警，不写盘）
        runConfigDiagnosis(null, false);
        printConfigDiagnosisSummary();

        // 10. 异步版本检查
        versionCheckService = new VersionCheckService(this);
        versionCheckService.checkAsync();
        getServer().getPluginManager().registerEvents(versionCheckService, this);

        consoleInfo(ChatColor.GREEN + "加载完成");
    }

    // ─── 智能配置诊断 ─────────────────────────────

    private void initConfigDiagnostic() {
        configDiagnosticEngine = new ConfigDiagnosticEngine(
            getDataFolder(),
            Instant.now(),
            (ownerId, resourcePath, loader) -> {
                // 对于外部模块，优先走 ModuleRegistry
                if (moduleRegistry != null && ownerId != null && !"axs-core".equalsIgnoreCase(ownerId)) {
                    try {
                        InputStream input = moduleRegistry.openProtectedResource(ownerId, resourcePath, loader);
                        if (input != null) {
                            return input;
                        }
                    } catch (IOException ignored) {
                        // 跳过，回退标准资源加载
                    }
                }
                ClassLoader effective = loader != null ? loader : getClass().getClassLoader();
                return MohistCompat.getResourceSafe(resourcePath, effective);
            },
            getClass().getClassLoader(),
            getLogger()
        );
        configDiagnosisStore = new ConfigDiagnosisStore();

        // 宿主 config.yml 作为一个 ModuleConfigSpec
        hostConfigSpecs.add(ModuleConfigSpec.basic(
            "axs-core",
            new ConfigSyncSpec("config.yml", "config.yml",
                SyncPolicy.builder()
                    .dynamicSection("currencies")
                    .dynamicSection("keybinds")
                    .build())
        ));

        // 启动时清理过期诊断/备份目录
        try {
            new RetentionCleaner(getLogger(), Duration.ofDays(7), Duration.ofDays(60), 5)
                .cleanup(getDataFolder());
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * 模块加载期间注册其声明的 ConfigSpec 并跑一次诊断。
     */
    public void registerModuleConfigSpecs(String ownerId, List<ModuleConfigSpec> specs, ClassLoader moduleClassLoader) {
        if (configDiagnosticEngine == null || specs == null || specs.isEmpty()) {
            return;
        }
        for (ModuleConfigSpec spec : specs) {
            moduleConfigSpecs.put(spec.ownerId(), new ModuleSpecRegistration(spec, moduleClassLoader));
            ConfigDiagnosisReport report = configDiagnosticEngine.diagnose(spec, moduleClassLoader, false);
            configDiagnosisStore.put(spec, report);
        }
    }

    /** 除名外部模块的 spec（模块卸载时）。 */
    public void unregisterModuleConfigSpecs(String ownerId) {
        moduleConfigSpecs.remove(ownerId);
    }

    /** 对全量或单个 ownerId 跑一次诊断。 */
    public void runConfigDiagnosis(String ownerId, boolean writeArtifacts) {
        if (configDiagnosticEngine == null) {
            return;
        }
        List<ConfigDiagnosisReport> reports = new ArrayList<>();
        // 宿主主表
        for (ModuleConfigSpec spec : hostConfigSpecs) {
            if (ownerId != null && !ownerId.equalsIgnoreCase(spec.ownerId())) {
                continue;
            }
            ConfigDiagnosisReport report = configDiagnosticEngine.diagnose(
                spec, getClass().getClassLoader(), writeArtifacts);
            configDiagnosisStore.put(spec, report);
            reports.add(report);
        }
        // 模块主表
        for (ModuleSpecRegistration reg : moduleConfigSpecs.values()) {
            if (ownerId != null && !ownerId.equalsIgnoreCase(reg.spec().ownerId())) {
                continue;
            }
            ConfigDiagnosisReport report = configDiagnosticEngine.diagnose(
                reg.spec(), reg.classLoader(), writeArtifacts);
            configDiagnosisStore.put(reg.spec(), report);
            reports.add(report);
        }
        if (!writeArtifacts) {
            return;
        }
        if (ownerId == null) {
            configDiagnosticEngine.writeSummary(reports);
        } else {
            List<ConfigDiagnosisReport> all = new ArrayList<>();
            for (var entry : configDiagnosisStore.all()) {
                all.add(entry.report());
            }
            configDiagnosticEngine.writeSummary(all);
        }
    }

    private void printConfigDiagnosisSummary() {
        if (configDiagnosticEngine == null) {
            return;
        }
        long info = 0, warn = 0, err = 0;
        for (var e : configDiagnosisStore.all()) {
            info += e.report().countOf(ConfigIssueSeverity.INFO);
            warn += e.report().countOf(ConfigIssueSeverity.WARN);
            err += e.report().countOf(ConfigIssueSeverity.ERROR);
        }
        consoleInfo("配置诊断: " + configDiagnosisStore.all().size() + " 个目标, "
            + err + " ERROR / " + warn + " WARN / " + info + " INFO");
        if (info + warn + err > 0) {
            consoleInfo(ChatColor.YELLOW + "检测到配置问题。运行 /axs config diagnose 生成详细报告，随后用 preview / apply 查看和修复。");
        }
    }

    public ConfigDiagnosticEngine getConfigDiagnosticEngine() {
        return configDiagnosticEngine;
    }

    public ConfigDiagnosisStore getConfigDiagnosisStore() {
        return configDiagnosisStore;
    }

    @Override
    public void onDisable() {
        if (chatSignBypassService != null) {
            chatSignBypassService.shutdown();
            chatSignBypassService = null;
        }
        if (keybindService != null) {
            keybindService.shutdown();
            keybindService = null;
        }
        if (moduleRegistry != null) {
            moduleRegistry.unloadAll();
            moduleRegistry = null;
        }
        if (crossServerService != null) {
            crossServerService.shutdown();
            crossServerService = null;
        }
        if (clientEventLifecycleManager != null) {
            clientEventLifecycleManager.stop();
        }
        if (bridgeLifecycleManager != null) {
            bridgeLifecycleManager.shutdown();
        }
    }

    // ─── 公共访问 ─────────────────────────────────────────────

    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    public xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI getPacketBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.packetBridge();
    }

    public xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI getClientBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.clientBridge();
    }

    public xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI getItemStackBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.itemStackBridge();
    }

    public xuanmo.arcartxsuite.api.bridge.PropBridgeAPI getPropBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.propBridge();
    }

    public String describePacketBridgeMode() {
        return bridgeLifecycleManager == null ? "unavailable" : bridgeLifecycleManager.describePacketMode();
    }

    // ─── 资源/输出辅助 ────────────────────────────────────────

    private void ensureRootConfigExists() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("无法创建插件数据目录: " + getDataFolder().getAbsolutePath());
        }
        File rootConfigFile = new File(getDataFolder(), "config.yml");
        if (!rootConfigFile.exists()) {
            try {
                writeBundledResource("config.yml", rootConfigFile);
            } catch (IOException exception) {
                throw new IllegalStateException("写出 config.yml 失败", exception);
            }
        }
    }

    private InputStream openBundledResource(String resourcePath) throws IOException {
        InputStream input = getResource(resourcePath);
        if (input != null) return input;
        return MohistCompat.getResourceSafe(resourcePath, getClass().getClassLoader());
    }

    private void writeBundledResource(String resourcePath, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
        try (InputStream input = openBundledResource(resourcePath)) {
            if (input == null) {
                throw new IOException("未找到资源: " + resourcePath);
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void printStartupBanner() {
        CommandSender console = Bukkit.getConsoleSender();
        String version = getDescription() == null ? "" : getDescription().getVersion();
        final int arcartxWidth = 44;
        final ChatColor[] gradient = {
            ChatColor.AQUA, ChatColor.DARK_AQUA, ChatColor.BLUE, ChatColor.DARK_BLUE
        };
        console.sendMessage("");
        try (InputStream in = MohistCompat.getResourceSafe("banner.txt", getClass().getClassLoader())) {
            if (in == null) {
                console.sendMessage(ChatColor.GOLD + "ArcartXSuite");
            } else {
                java.util.Scanner scanner = new java.util.Scanner(in, java.nio.charset.StandardCharsets.UTF_8);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    StringBuilder sb = new StringBuilder();
                    int len = line.length();
                    int leftLen = Math.min(arcartxWidth, len);
                    for (int i = 0; i < leftLen; i++) {
                        int seg = (gradient.length * i) / Math.max(1, arcartxWidth);
                        if (seg >= gradient.length) seg = gradient.length - 1;
                        if (i == 0 || (gradient.length * i) / Math.max(1, arcartxWidth)
                                    != (gradient.length * (i - 1)) / Math.max(1, arcartxWidth)) {
                            sb.append(gradient[seg]);
                        }
                        sb.append(line.charAt(i));
                    }
                    if (len > arcartxWidth) {
                        sb.append(ChatColor.GOLD).append(line, arcartxWidth, len);
                    }
                    console.sendMessage(sb.toString());
                }
            }
        } catch (IOException ignored) {
            console.sendMessage(ChatColor.GOLD + "ArcartXSuite");
        }
        console.sendMessage("");
        console.sendMessage(ChatColor.GRAY + "  版本: " + ChatColor.YELLOW + "v" + version
            + ChatColor.GRAY + "  |  作者: " + ChatColor.WHITE + AUTHOR_NAME);
        console.sendMessage("");
    }

    private void suppressPlaceholderApiNoise() {
        try {
            org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi == null) {
                return;
            }
            java.util.logging.Logger papiLogger = papi.getLogger();
            java.util.logging.Filter existing = papiLogger.getFilter();
            papiLogger.setFilter(record -> {
                String msg = record.getMessage();
                if (msg != null
                    && msg.contains("Successfully registered")
                    && msg.contains("expansion")) {
                    return false;
                }
                return existing == null || existing.isLoggable(record);
            });
        } catch (RuntimeException ignored) {
        }
    }

    public void consoleInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + "INFO: " + message);
    }

    public void consoleWarn(String message) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + ChatColor.YELLOW + "WARN: " + message);
    }

    public void consoleError(String message) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + ChatColor.RED + "ERROR: " + message);
    }
}
