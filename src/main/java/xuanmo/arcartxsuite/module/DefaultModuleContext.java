package xuanmo.arcartxsuite.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.account.AccountTypeService;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.bridge.ArcartXWaypointBridge;
import xuanmo.arcartxsuite.bridge.ArcartXWorldTextureService;
import xuanmo.arcartxsuite.bridge.AdyeshachNpcBridge;
import xuanmo.arcartxsuite.crossserver.CrossServerService;
import xuanmo.arcartxsuite.keybind.KeybindService;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.bridge.TaczCombatBridge;

/**
 * 宿主提供给每个模块的上下文实现。
 */
final class DefaultModuleContext implements ModuleContext {

    private final JavaPlugin plugin;
    private final String moduleId;
    private final Logger logger;
    private final File dataFolder;
    private final File uiFolder;
    private final PacketBridgeAPI packetBridge;
    private final ClientBridgeAPI clientBridge;
    private final ItemBridgeAPI itemStackBridge;
    private final PropBridgeAPI propBridge;
    private final xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI worldTextureBridge;
    private final PacketGuardAPI packetGuard;
    private final ModuleRegistry registry;
    private final ClassLoader moduleClassLoader;
    private final KeybindService keybindService;
    private final TaczCombatBridge taczCombatBridge;
    private final CrossServerService crossServerService;

    // 模块注册的资源（onDisable 时自动清理）
    private final List<Listener> registeredListeners = new ArrayList<>();
    private final List<String> registeredCommandNames = new ArrayList<>();
    private final List<xuanmo.arcartxsuite.api.KeybindHandler> registeredKeybindHandlers = new ArrayList<>();

    private final PlaceholderResolverAPI placeholderResolver;
    private final PlaceholderExpansionRegistry expansionRegistry;

    DefaultModuleContext(
        JavaPlugin plugin,
        String moduleId,
        PacketBridgeAPI packetBridge,
        ClientBridgeAPI clientBridge,
        ItemBridgeAPI itemStackBridge,
        PropBridgeAPI propBridge,
        PacketGuardAPI packetGuard,
        ModuleRegistry registry,
        ClassLoader moduleClassLoader,
        KeybindService keybindService,
        TaczCombatBridge taczCombatBridge,
        CrossServerService crossServerService,
        PlaceholderResolverAPI placeholderResolver
    ) {
        this.plugin = plugin;
        this.moduleId = moduleId;
        this.logger = new PluginConsoleLogger("AXS-" + moduleId, moduleId);
        this.dataFolder = new File(plugin.getDataFolder(), "data/" + moduleId);
        this.uiFolder = new File(plugin.getDataFolder(), "ui");
        this.packetBridge = packetBridge;
        this.clientBridge = clientBridge;
        this.itemStackBridge = itemStackBridge;
        this.propBridge = propBridge;
        this.worldTextureBridge = new ArcartXWorldTextureService(plugin);
        this.worldTextureBridge.initialize();
        this.packetGuard = packetGuard;
        this.registry = registry;
        this.moduleClassLoader = moduleClassLoader;
        this.keybindService = keybindService;
        this.taczCombatBridge = taczCombatBridge;
        this.crossServerService = crossServerService;
        this.placeholderResolver = placeholderResolver;
        this.expansionRegistry = resolveExpansionRegistry(logger);
    }

    private static PlaceholderExpansionRegistry resolveExpansionRegistry(Logger logger) {
        try {
            // 先检测 PAPI 是否存在
            Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            // 通过反射实例化 DirectPlaceholderExpansionRegistry，避免 PAPI 缺失时
            // 直接引用该类导致 NoClassDefFoundError（该类强引用 PlaceholderExpansion）
            Class<?> directRegistry = Class.forName(
                "xuanmo.arcartxsuite.placeholder.DirectPlaceholderExpansionRegistry"
            );
            PlaceholderExpansionRegistry registry = (PlaceholderExpansionRegistry)
                directRegistry.getConstructor(Logger.class).newInstance(logger);
            return registry;
        } catch (ClassNotFoundException e) {
            logger.info("PlaceholderAPI 未安装，使用反射实现的 PlaceholderExpansionRegistry。");
            return new xuanmo.arcartxsuite.placeholder.DefaultPlaceholderExpansionRegistry(logger);
        } catch (ReflectiveOperationException e) {
            logger.warning("DirectPlaceholderExpansionRegistry 反射实例化失败: " + e.getMessage()
                + "，回退到反射实现。");
            return new xuanmo.arcartxsuite.placeholder.DefaultPlaceholderExpansionRegistry(logger);
        }
    }

    @Override
    public JavaPlugin plugin() {
        return plugin;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public File dataFolder() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        return dataFolder;
    }

    @Override
    public File migrateLegacyDataFile(String baseFileName) {
        File target = dataFolder();
        if (baseFileName == null || baseFileName.isBlank()) {
            return target;
        }
        // 同时处理 base、base-shm、base-wal
        String[] suffixes = {"", "-shm", "-wal"};
        for (String suffix : suffixes) {
            File legacy = new File(plugin.getDataFolder(), baseFileName + suffix);
            File destination = new File(target, baseFileName + suffix);
            if (!legacy.exists() || destination.exists()) {
                continue;
            }
            try {
                Files.createDirectories(target.toPath());
                Files.move(legacy.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
                logger.info(org.bukkit.ChatColor.GOLD + "→ 已归位数据文件: "
                    + org.bukkit.ChatColor.YELLOW + legacy.getName()
                    + org.bukkit.ChatColor.GRAY + "  ➜  "
                    + org.bukkit.ChatColor.AQUA + "data/" + dataFolder.getName() + "/" + destination.getName());
            } catch (IOException exception) {
                logger.warning(org.bukkit.ChatColor.RED + "迁移 legacy 数据文件失败: " + legacy.getAbsolutePath()
                    + " -> " + destination.getAbsolutePath() + " | " + exception.getMessage());
            }
        }
        return target;
    }

    @Override
    public File migrateLegacyDirectory(String relativePath) {
        File destination = new File(dataFolder(), relativePath);
        if (relativePath == null || relativePath.isBlank()) {
            return destination;
        }
        File legacy = new File(plugin.getDataFolder(), relativePath);
        if (!legacy.isDirectory() || destination.exists()) {
            // 即使目标已存在（迁移已完成），也尝试清理遗留的空目录
            cleanupEmptyLegacyParents(legacy, plugin.getDataFolder());
            return destination;
        }
        try {
            File parent = destination.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Files.move(legacy.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            logger.info(org.bukkit.ChatColor.GOLD + "→ 已归位目录: "
                + org.bukkit.ChatColor.YELLOW + relativePath
                + org.bukkit.ChatColor.GRAY + "  ➜  "
                + org.bukkit.ChatColor.AQUA + "data/" + dataFolder.getName() + "/" + relativePath);
            cleanupEmptyLegacyParents(legacy, plugin.getDataFolder());
        } catch (IOException exception) {
            logger.warning(org.bukkit.ChatColor.RED + "迁移 legacy 目录失败: " + legacy.getAbsolutePath()
                + " -> " + destination.getAbsolutePath() + " | " + exception.getMessage());
        }
        return destination;
    }

    private void cleanupEmptyLegacyParents(File start, File stopAt) {
        File current = start;
        java.nio.file.Path stopPath = stopAt.toPath().normalize();
        while (current != null) {
            java.nio.file.Path currentPath = current.toPath().normalize();
            if (currentPath.equals(stopPath) || !currentPath.startsWith(stopPath)) {
                break;
            }
            if (!current.isDirectory()) {
                break;
            }
            String[] children = current.list();
            if (children != null && children.length == 0) {
                if (current.delete()) {
                    logger.info(org.bukkit.ChatColor.GRAY + "→ 已清理遗留空目录: " + currentPath);
                }
                current = current.getParentFile();
            } else {
                break;
            }
        }
    }

    @Override
    public File uiFolder() {
        if (!uiFolder.exists()) {
            uiFolder.mkdirs();
        }
        return uiFolder;
    }

    @Override
    public PacketBridgeAPI packetBridge() {
        return packetBridge;
    }

    @Override
    public ClientBridgeAPI clientBridge() {
        return clientBridge;
    }

    @Override
    public ItemBridgeAPI itemStackBridge() {
        return itemStackBridge;
    }

    @Override
    public ItemSourceRegistry itemSourceRegistry() {
        return registry.itemSourceRegistry();
    }

    @Override
    public ItemMatcherAPI itemMatcher() {
        return registry.itemMatcher();
    }

    @Override
    public CurrencyBridgeAPI currencyManager() {
        return registry.currencyManager();
    }

    @Override
    public AttributeBridgeRegistry attributeBridge() {
        return registry.attributeBridge();
    }

    @Override
    public AriaBridge ariaBridge() {
        return registry.ariaBridge();
    }

    @Override
    public ScriptConditionEvaluator scriptConditionEvaluator() {
        return registry.scriptConditionEvaluator();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AXSModule> Optional<T> getModule(Class<T> moduleClass) {
        return registry.getModule(moduleClass);
    }

    @Override
    public Optional<AXSModule> getModule(String moduleId) {
        return registry.getModule(moduleId);
    }

    @Override
    public PacketGuardAPI packetGuard() {
        return packetGuard;
    }

    @Override
    public boolean taczActive() {
        return taczCombatBridge != null && taczCombatBridge.isActive();
    }

    @Override
    public AccountTypeService accountTypeService() {
        return registry.accountTypeService();
    }

    @Override
    public CrossServerAPI crossServer() {
        return crossServerService;
    }

    @Override
    public InputStream openProtectedResource(String resourcePath, ClassLoader loader) {
        try {
            return registry.openProtectedResource(moduleId, resourcePath, loader);
        } catch (IOException exception) {
            logger.warning("打开受保护资源失败: " + resourcePath + " | " + exception.getMessage());
            return null;
        }
    }

    @Override
    public void exportResource(String resourcePath, File target, boolean overwrite) {
        if (target.exists() && !overwrite) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream input = openProtectedResource(resourcePath, moduleClassLoader)) {
            if (input == null) {
                logger.warning("未找到资源: " + resourcePath);
                return;
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            logger.warning("导出资源失败: " + resourcePath + " -> " + target.getAbsolutePath() + " | " + exception.getMessage());
        }
    }

    @Override
    public UiBinding prepareUiBinding(String moduleName, String configuredUiId, boolean registerOnEnable, File uiFile) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            logger.severe("ArcartX 桥接不可用，无法注册 " + moduleName + " UI");
            return null;
        }
        String runtimeUiId = PacketBridgeAPI.normalizeUiId(configuredUiId, uiFile);
        String registeredUiId = null;
        if (registerOnEnable) {
            PacketBridgeAPI.UiRegistrationResult registration = packetBridge.registerOrReloadUi(configuredUiId, uiFile);
            if (!registration.success()) {
                logger.severe("初始化 ArcartX " + moduleName + " UI 失败: " + registration.message());
                return null;
            }
            runtimeUiId = registration.runtimeUiId();
            registeredUiId = registration.registeredUiId();
        } else {
            logger.fine("ArcartX " + moduleName + " UI 自动注册已关闭，将直接使用 UI 标识: " + runtimeUiId);
        }
        if (registeredUiId != null) {
            registry.recordUiRegistration(moduleId, registeredUiId);
        }
        logger.fine("ArcartX " + moduleName + " UI 目标: " + runtimeUiId + " | 文件: " + uiFile.getAbsolutePath());
        return new UiBinding(runtimeUiId, registeredUiId);
    }

    @Override
    public boolean hasPlugin(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    // ─── 新增：宿主级资源 ────────────────────────────────────

    @Override
    public File pluginDataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public xuanmo.arcartxsuite.api.bridge.PropBridgeAPI propBridge() {
        return propBridge;
    }

    @Override
    public xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI worldTextureBridge() {
        return worldTextureBridge;
    }

    @Override
    public xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI createWaypointBridge() {
        return new ArcartXWaypointBridge(plugin);
    }

    @Override
    public xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI createAdyeshachNpcBridge() {
        return new AdyeshachNpcBridge(plugin);
    }

    // ─── 新增：事件与命令注册 ────────────────────────────────

    @Override
    public void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    @Override
    public void unregisterListeners() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
    }

    @Override
    public void registerCommand(String commandName, TabExecutor executor) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            logger.warning("命令 '" + commandName + "' 未在 plugin.yml 中声明，跳过绑定");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        registeredCommandNames.add(commandName);
        logger.fine("命令 '" + commandName + "' 已绑定到 " + executor.getClass().getSimpleName());
    }

    /**
     * 解绑当前模块注册的所有命令（内部调用，设置为空执行器）。
     */
    void unbindCommands() {
        for (String commandName : registeredCommandNames) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command != null) {
                command.setExecutor(plugin);
                command.setTabCompleter(null);
            }
        }
        registeredCommandNames.clear();
    }

    // ─── 新增：PlaceholderAPI ────────────────────────────────

    @Override
    public @NotNull PlaceholderResolverAPI placeholderResolver() {
        return placeholderResolver;
    }

    @Override
    public @NotNull PlaceholderExpansionRegistry expansionRegistry() {
        return expansionRegistry;
    }

    // ─── 新增：客户端事件路由 ────────────────────────────────

    @Override
    public void registerClientPacketHandler(ClientPacketHandler handler) {
        registerClientPacketHandler(handler, 0);
    }

    @Override
    public void registerClientPacketHandler(ClientPacketHandler handler, int priority) {
        registry.registerClientPacketHandler(moduleId, handler, priority);
    }

    @Override
    public void registerClientInitializedHandler(ClientInitializedHandler handler) {
        registry.registerClientInitializedHandler(moduleId, handler);
    }

    // ─── 新增：模块级资源导出 ────────────────────────────────

    @Override
    public File exportUiResource(String resourcePath, String relativeUiPath, boolean overwrite, ClassLoader loader) throws IOException {
        File target = new File(plugin.getDataFolder(), relativeUiPath);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
        if (target.exists() && !overwrite) {
            return target;
        }
        try (InputStream input = openProtectedResource(resourcePath, loader)) {
            if (input == null) {
                throw new IOException("未找到 UI 资源: " + resourcePath);
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    @Override
    public File exportConfigResource(String resourcePath, String targetRelativePath, boolean overwrite, ClassLoader loader) {
        File target = new File(plugin.getDataFolder(), targetRelativePath);
        if (target.exists() && !overwrite) {
            return target;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream input = openProtectedResource(resourcePath, loader)) {
            if (input == null) {
                logger.warning("未找到配置资源: " + resourcePath);
                return target;
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            logger.warning("导出配置资源失败: " + resourcePath + " -> " + target.getAbsolutePath() + " | " + exception.getMessage());
        }
        return target;
    }

    @Override
    public void unregisterUi(String registeredUiId) {
        if (packetBridge == null || registeredUiId == null) {
            return;
        }
        packetBridge.unregisterUi(registeredUiId);
    }

    @Override
    public void registerKeybindHandler(String keyName, int priority, xuanmo.arcartxsuite.api.KeybindHandler handler) {
        if (keybindService != null && keyName != null && handler != null) {
            keybindService.registerHandler(keyName, priority, handler);
            registeredKeybindHandlers.add(handler);
        }
    }

    void unregisterKeybindHandlers() {
        if (keybindService != null) {
            for (xuanmo.arcartxsuite.api.KeybindHandler handler : registeredKeybindHandlers) {
                keybindService.unregisterHandler(handler);
            }
        }
        registeredKeybindHandlers.clear();
    }

    @Override
    public <T> void registerCapability(Class<T> capabilityType, T implementation) {
        registry.registerCapability(moduleId, capabilityType, implementation);
    }

    @Override
    public <T> T getCapability(Class<T> capabilityType) {
        return registry.getCapability(capabilityType);
    }
}
