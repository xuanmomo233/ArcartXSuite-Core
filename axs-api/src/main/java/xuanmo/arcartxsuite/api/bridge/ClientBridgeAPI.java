package xuanmo.arcartxsuite.api.bridge;

import java.util.function.Consumer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ArcartX Client 桥接的公开 API 子集。
 * <p>
 * 提供伤害飘字、服务端变量下发、可见玩家遍历等能力。
 *
 * @since 1.1.0
 */
@ApiStability.Stable
public interface ClientBridgeAPI {

    /** 桥接是否可用 */
    boolean isAvailable();

    /** 初始化桥接（由宿主调用） */
    boolean initialize();

    /** 关闭桥接（由宿主调用） */
    void shutdown();

    /**
     * 向玩家发送伤害飘字显示。
     *
     * @param player   观察者玩家
     * @param configId 飘字配置 id
     * @param amount   伤害数值
     * @param target   受击实体
     */
    boolean sendDamageDisplay(@NotNull Player player, @NotNull String configId,
                              double amount, @NotNull Entity target);

    /**
     * 向玩家下发服务端变量。
     *
     * @param player       目标玩家
     * @param variableName 变量名
     * @param value        变量值（String / Number / Boolean）
     */
    boolean sendServerVariable(@NotNull Player player, @NotNull String variableName, @Nullable Object value);

    /**
     * 遍历能看到指定实体的所有玩家。
     *
     * @param entity   目标实体
     * @param consumer 对每个可见玩家执行的操作
     */
    boolean forEachSeenPlayer(@NotNull Entity entity, @NotNull Consumer<Player> consumer);
}
