package xuanmo.arcartxsuite.lifecycle;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.util.ReflectionCache;

/**
 * ArcartX 客户端事件转发生命周期管理器。
 * <p>
 * 监听 `ClientCustomPacketEvent` 与 `ClientInitializedEvent$End`，
 * 将事件路由到 {@link ModuleRegistry} 中已加载的模块。
 */
public final class ClientEventLifecycleManager {

    private final JavaPlugin plugin;
    private final Supplier<ModuleRegistry> registrySupplier;
    private final ReflectionCache reflectionCache;
    private Listener clientCustomPacketListener;
    private Listener clientInitializedListener;

    public ClientEventLifecycleManager(JavaPlugin plugin, Supplier<ModuleRegistry> registrySupplier) {
        this.plugin = plugin;
        this.registrySupplier = registrySupplier;
        this.reflectionCache = new ReflectionCache(plugin.getClass().getClassLoader());
    }

    public void start() {
        registerClientCustomPacketListener();
        registerClientInitializedListener();
    }

    public void stop() {
        unregisterClientCustomPacketListener();
        unregisterClientInitializedListener();
    }

    @SuppressWarnings("unchecked")
    private void registerClientCustomPacketListener() {
        unregisterClientCustomPacketListener();

        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return;
        }

        try {
            Class<?> rawEventClass = reflectionCache.forName("priv.seventeen.artist.arcartx.event.client.ClientCustomPacketEvent");
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().warning("ArcartX ClientCustomPacketEvent 不是 Bukkit Event，已跳过监听。");
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayerMethod = reflectionCache.method(rawEventClass, "getPlayer");
            Method getIdMethod = reflectionCache.method(rawEventClass, "getId");
            Method getDataMethod = reflectionCache.method(rawEventClass, "getData");

            clientCustomPacketListener = new Listener() {};
            plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                clientCustomPacketListener,
                EventPriority.MONITOR,
                (listener, event) -> dispatchClientCustomPacket(event, rawEventClass, getPlayerMethod, getIdMethod, getDataMethod),
                plugin,
                true
            );
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("注册 ArcartX 客户端自定义包监听失败: " + exception.getMessage());
        }
    }

    private void unregisterClientCustomPacketListener() {
        if (clientCustomPacketListener == null) {
            return;
        }
        HandlerList.unregisterAll(clientCustomPacketListener);
        clientCustomPacketListener = null;
    }

    private void dispatchClientCustomPacket(
        Event event,
        Class<?> eventClass,
        Method getPlayerMethod,
        Method getIdMethod,
        Method getDataMethod
    ) {
        ModuleRegistry registry = registrySupplier.get();
        if (!eventClass.isInstance(event) || registry == null) {
            return;
        }
        try {
            Object rawPlayer = getPlayerMethod.invoke(event);
            Object rawId = getIdMethod.invoke(event);
            Object rawData = getDataMethod.invoke(event);
            if (!(rawPlayer instanceof Player player) || !(rawId instanceof String packetId)) {
                return;
            }
            List<String> data = rawData instanceof List<?> rawList
                ? rawList.stream().map(String::valueOf).toList()
                : List.of();
            Runnable route = () -> registry.routeClientPacket(player, packetId, data);
            if (Bukkit.isPrimaryThread()) {
                route.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, route);
            }
        } catch (ReflectiveOperationException ignored) {
            // ArcartX API 不兼容时已记录在注册阶段
        }
    }

    @SuppressWarnings("unchecked")
    private void registerClientInitializedListener() {
        unregisterClientInitializedListener();

        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return;
        }

        try {
            Class<?> rawEventClass = reflectionCache.forName("priv.seventeen.artist.arcartx.event.client.ClientInitializedEvent$End");
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                plugin.getLogger().warning("ArcartX ClientInitializedEvent$End 不是 Bukkit Event，已跳过监听。");
                return;
            }
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Method getPlayerMethod = reflectionCache.method(rawEventClass, "getPlayer");

            clientInitializedListener = new Listener() {};
            plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                clientInitializedListener,
                EventPriority.MONITOR,
                (listener, event) -> dispatchClientInitialized(event, rawEventClass, getPlayerMethod),
                plugin,
                true
            );
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("注册 ArcartX 客户端初始化监听失败: " + exception.getMessage());
        }
    }

    private void unregisterClientInitializedListener() {
        if (clientInitializedListener == null) {
            return;
        }
        HandlerList.unregisterAll(clientInitializedListener);
        clientInitializedListener = null;
    }

    private void dispatchClientInitialized(Event event, Class<?> eventClass, Method getPlayerMethod) {
        ModuleRegistry registry = registrySupplier.get();
        if (!eventClass.isInstance(event) || registry == null) {
            return;
        }
        try {
            Object rawPlayer = getPlayerMethod.invoke(event);
            if (rawPlayer instanceof Player player) {
                Runnable route = () -> registry.routeClientInitialized(player);
                if (Bukkit.isPrimaryThread()) {
                    route.run();
                } else {
                    Bukkit.getScheduler().runTask(plugin, route);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // 已在注册阶段告警
        }
    }
}
