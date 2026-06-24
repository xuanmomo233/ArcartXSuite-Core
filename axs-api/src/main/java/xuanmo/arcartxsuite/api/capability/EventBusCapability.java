package xuanmo.arcartxsuite.api.capability;

import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 模块间解耦事件总线能力接口（pub/sub 模式）。
 * <p>
 * 任何模块都可以发布事件（publish），其他模块订阅感兴趣的事件主题。
 * 事件主题以字符串标识（如 {@code "market.purchase"}、{@code "mail.received"}）。
 * <p>
 * 与 {@link SignalDispatchable} 的区别：
 * <ul>
 *   <li>SignalDispatchable 面向 EventPacket 规则引擎（1 对 1）</li>
 *   <li>EventBusCapability 面向所有模块（1 对多 pub/sub）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 发布方（Market 模块）
 * eventBus.publish("market.purchase", player, Map.of("price", "1000", "item", "钻石剑"));
 *
 * // 订阅方（QQBot 模块）
 * eventBus.subscribe("market.purchase", (event) -> {
 *     qqBot.sendToAllGroups("[交易] " + event.player().getName() + " 购买了物品");
 * });
 * }</pre>
 */
public interface EventBusCapability {

    /**
     * 发布一个事件到总线。
     *
     * @param topic     事件主题
     * @param player    关联玩家（可能为 null，如系统事件）
     * @param payload   附加数据
     */
    void publish(@NotNull String topic, @Nullable Player player, @NotNull Map<String, String> payload);

    /**
     * 订阅一个事件主题。
     *
     * @param topic    事件主题（支持通配符 {@code "market.*"}）
     * @param handler  处理器
     * @return 订阅 ID（用于取消订阅）
     */
    String subscribe(@NotNull String topic, @NotNull EventHandler handler);

    /**
     * 取消订阅。
     *
     * @param subscriptionId 订阅 ID
     */
    void unsubscribe(@NotNull String subscriptionId);

    /**
     * 事件数据载体。
     */
    record BusEvent(
        @NotNull String topic,
        @Nullable Player player,
        @NotNull Map<String, String> payload,
        long timestamp
    ) {}

    /**
     * 事件处理器。
     */
    @FunctionalInterface
    interface EventHandler {
        void handle(@NotNull BusEvent event);
    }
}
