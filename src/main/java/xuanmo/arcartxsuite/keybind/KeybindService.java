package xuanmo.arcartxsuite.keybind;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.KeybindHandler;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.bridge.ArcartXKeyBindBridge;

/**
 * 宿主级全局按键服务。
 * <p>
 * 从 config.yml keybinds 节读取按键定义，通过 {@link PropBridgeAPI} 统一注册到 ArcartX 客户端，
 * 并监听 {@code ClientKeyPressEvent} 将按键回调按优先级分发给模块注册的 {@link KeybindHandler}。
 */
public final class KeybindService {

    private final JavaPlugin plugin;

    /** keyName → 按优先级排序的处理器列表 */
    private final Map<String, CopyOnWriteArrayList<PrioritizedHandler>> handlers = new ConcurrentHashMap<>();
    /** 已成功注册的按键名 */
    private final List<String> registeredKeys = new ArrayList<>();
    /** 3 参数版本按键桥接（注册后通过 ClientKeyPressEvent 触发） */
    private final ArcartXKeyBindBridge keyBindBridge;
    /** ClientKeyPressEvent 监听器 */
    private Listener keyPressListener;

    public KeybindService(JavaPlugin plugin, PropBridgeAPI propBridge) {
        this.plugin = plugin;
        this.keyBindBridge = new ArcartXKeyBindBridge(plugin);
    }

    /**
     * 从 config.yml 读取 keybinds 节并注册所有按键。
     * 在 {@code onEnable} 桥接初始化完成后、模块加载前调用。
     */
    public void initialize(FileConfiguration config) {
        ConfigurationSection keybindsSection = config.getConfigurationSection("keybinds");
        if (keybindsSection == null) {
            plugin.getLogger().info("[Keybind] config.yml 未配置 keybinds 节，跳过按键注册。");
            return;
        }
        if (!keyBindBridge.initialize()) {
            plugin.getLogger().warning("[Keybind] ArcartX KeyBind 桥接不可用，按键注册已跳过。");
            return;
        }

        for (String key : keybindsSection.getKeys(false)) {
            ConfigurationSection entry = keybindsSection.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String name = entry.getString("name", "");
            String defaultKey = entry.getString("default-key", "");
            String category = entry.getString("category", "ArcartXSuite");
            if (name.isBlank() || defaultKey.isBlank()) {
                plugin.getLogger().warning("[Keybind] 跳过无效按键配置: keybinds." + key);
                continue;
            }
            boolean success = keyBindBridge.registerClientKeyBind(name, category, defaultKey);
            if (success) {
                registeredKeys.add(name);
                plugin.getLogger().info("[Keybind] 已注册客户端按键: " + name + " (默认 " + defaultKey + ")");
            } else {
                plugin.getLogger().warning("[Keybind] 注册客户端按键失败: " + name);
            }
        }

        // 注册 ClientKeyPressEvent 监听分发按键事件
        registerClientKeyPressListener();
    }

    /**
     * 注销所有已注册的按键并清理处理器。
     */
    public void shutdown() {
        unregisterClientKeyPressListener();
        for (String keyName : registeredKeys) {
            keyBindBridge.unregisterClientKeyBind(keyName);
        }
        registeredKeys.clear();
        handlers.clear();
        keyBindBridge.shutdown();
    }

    @SuppressWarnings("unchecked")
    private void registerClientKeyPressListener() {
        unregisterClientKeyPressListener();
        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            plugin.getLogger().warning("[Keybind] ArcartX 未加载，ClientKeyPressEvent 监听未注册。");
            return;
        }
        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> rawEventClass = Class.forName(
                "priv.seventeen.artist.arcartx.event.client.ClientKeyPressEvent",
                true, classLoader
            );
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().warning("[Keybind] ClientKeyPressEvent 不是 Bukkit Event。");
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayerMethod = rawEventClass.getMethod("getPlayer");
            Method getKeyNameMethod = rawEventClass.getMethod("getKeyName");
            keyPressListener = new Listener() { };
            Bukkit.getPluginManager().registerEvent(
                eventClass, keyPressListener, EventPriority.MONITOR,
                (listener, event) -> handleKeyPressEvent(event, rawEventClass, getPlayerMethod, getKeyNameMethod),
                plugin, true
            );
            plugin.getLogger().info("[Keybind] ClientKeyPressEvent 监听注册成功。");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[Keybind] 注册 ClientKeyPressEvent 监听失败: " + exception.getMessage());
        }
    }

    private void unregisterClientKeyPressListener() {
        if (keyPressListener != null) {
            HandlerList.unregisterAll(keyPressListener);
            keyPressListener = null;
        }
    }

    private void handleKeyPressEvent(Event event, Class<?> eventClass, Method getPlayerMethod, Method getKeyNameMethod) {
        if (!eventClass.isInstance(event)) {
            return;
        }
        try {
            Object rawPlayer = getPlayerMethod.invoke(event);
            Object rawKeyName = getKeyNameMethod.invoke(event);
            if (rawPlayer instanceof Player player && rawKeyName instanceof String keyName) {
                dispatch(player, keyName);
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[Keybind] 处理 ClientKeyPressEvent 失败: " + exception.getMessage());
        }
    }

    /**
     * 模块注册按键处理器。
     *
     * @param keyName  按键名（须与 config.yml keybinds 中的 name 匹配）
     * @param priority 优先级（越小越先）
     * @param handler  处理器
     */
    public void registerHandler(String keyName, int priority, KeybindHandler handler) {
        if (keyName == null || keyName.isBlank() || handler == null) {
            return;
        }
        CopyOnWriteArrayList<PrioritizedHandler> list = handlers.computeIfAbsent(
            keyName.toUpperCase(), ignored -> new CopyOnWriteArrayList<>()
        );
        list.add(new PrioritizedHandler(priority, handler));
        list.sort(null);
    }

    /**
     * 移除指定模块的按键处理器。
     *
     * @param handler 要移除的处理器实例
     */
    public void unregisterHandler(KeybindHandler handler) {
        if (handler == null) {
            return;
        }
        for (CopyOnWriteArrayList<PrioritizedHandler> list : handlers.values()) {
            list.removeIf(entry -> entry.handler == handler);
        }
    }

    /** 按优先级分发给处理器，任一返回 true 即停止。确保在主线程执行。 */
    private void dispatch(Player player, String keyName) {
        if (Bukkit.isPrimaryThread()) {
            doDispatch(player, keyName);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> doDispatch(player, keyName));
        }
    }

    private void doDispatch(Player player, String keyName) {
        CopyOnWriteArrayList<PrioritizedHandler> list = handlers.get(keyName.toUpperCase());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (PrioritizedHandler entry : list) {
            try {
                if (entry.handler.handleKeyPress(player, keyName)) {
                    return;
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("[Keybind] 处理器异常 (" + keyName + "): " + exception.getMessage());
            }
        }
    }

    /** 已注册的按键名列表（调试用） */
    public List<String> registeredKeyNames() {
        return List.copyOf(registeredKeys);
    }

    private record PrioritizedHandler(int priority, KeybindHandler handler) implements Comparable<PrioritizedHandler> {
        @Override
        public int compareTo(PrioritizedHandler other) {
            return Integer.compare(this.priority, other.priority);
        }
    }
}
