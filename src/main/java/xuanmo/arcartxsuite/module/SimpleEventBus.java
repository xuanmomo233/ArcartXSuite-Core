package xuanmo.arcartxsuite.module;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;

/**
 * 简单内存事件总线实现，支持精确匹配和通配符订阅。
 */
public final class SimpleEventBus implements EventBusCapability {

    private final Logger logger;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscription>> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Subscription> subscriptionById = new ConcurrentHashMap<>();

    public SimpleEventBus(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void publish(@NotNull String topic, @Nullable Player player, @NotNull Map<String, String> payload) {
        BusEvent event = new BusEvent(topic, player, payload, System.currentTimeMillis());
        // 精确匹配
        CopyOnWriteArrayList<Subscription> exact = subscriptions.get(topic);
        if (exact != null) {
            for (Subscription sub : exact) {
                dispatch(sub, event);
            }
        }
        // 通配符匹配（"market.*" 匹配 "market.purchase"）
        for (var entry : subscriptions.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.endsWith(".*") && !pattern.equals(topic)) {
                String prefix = pattern.substring(0, pattern.length() - 1); // "market."
                if (topic.startsWith(prefix)) {
                    for (Subscription sub : entry.getValue()) {
                        dispatch(sub, event);
                    }
                }
            }
        }
    }

    @Override
    public String subscribe(@NotNull String topic, @NotNull EventHandler handler) {
        String id = UUID.randomUUID().toString();
        Subscription sub = new Subscription(id, topic, handler);
        subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(sub);
        subscriptionById.put(id, sub);
        return id;
    }

    @Override
    public void unsubscribe(@NotNull String subscriptionId) {
        Subscription sub = subscriptionById.remove(subscriptionId);
        if (sub != null) {
            CopyOnWriteArrayList<Subscription> list = subscriptions.get(sub.topic);
            if (list != null) {
                list.remove(sub);
                if (list.isEmpty()) {
                    subscriptions.remove(sub.topic, list);
                }
            }
        }
    }

    public int subscriptionCount() {
        return subscriptionById.size();
    }

    public void clear() {
        subscriptions.clear();
        subscriptionById.clear();
    }

    private void dispatch(Subscription sub, BusEvent event) {
        try {
            sub.handler.handle(event);
        } catch (Exception e) {
            logger.warning("[EventBus] 处理器异常 topic=" + event.topic() + " sub=" + sub.id + " : " + e.getMessage());
        }
    }

    private record Subscription(String id, String topic, EventHandler handler) {}
}
