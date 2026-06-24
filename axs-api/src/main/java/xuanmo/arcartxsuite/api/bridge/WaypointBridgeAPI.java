package xuanmo.arcartxsuite.api.bridge;

import java.util.Set;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * ArcartX 路标（Waypoint）桥接。
 * <p>
 * 为玩家添加/删除/清理客户端路标，支持样式解析。
 *
 * @since 1.2.0
 */
@ApiStability.Internal
public interface WaypointBridgeAPI {

    /** 初始化桥接（模块调用一次） */
    boolean initialize(@NotNull String ownerLabel);

    /** 关闭桥接 */
    void shutdown();

    /** 桥接是否可用 */
    boolean available();

    /** 可用的路标样式 id 集合 */
    @NotNull Set<String> availableStyleIds();

    /** 解析最终使用的样式 id */
    @NotNull String resolveStyleId(String preferredStyleId, String fallbackStyleId, String ownerLabel);

    /** 为玩家添加路标 */
    boolean addWaypoint(
        @NotNull Player player,
        @NotNull String waypointId,
        @NotNull String title,
        @NotNull String styleId,
        double x, double y, double z
    );

    /** 移除玩家指定路标 */
    boolean removeWaypoint(@NotNull Player player, @NotNull String waypointId, boolean animated);

    /** 清理玩家所有路标 */
    boolean clearWaypoints(@NotNull Player player);
}
