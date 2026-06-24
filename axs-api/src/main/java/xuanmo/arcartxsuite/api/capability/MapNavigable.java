package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 地图导航能力接口。
 * <p>
 * 由 Map 模块实现，供 QuestGps 等模块跨模块设置/清理外部导航点。
 */
public interface MapNavigable {

    /**
     * 设置/更新外部导航点。
     *
     * @param player      目标玩家
     * @param targetId    外部目标 id
     * @param source      来源标识（例如 "questgps"）
     * @param worldId     世界名
     * @param x x 坐标
     * @param y y 坐标
     * @param z z 坐标
     * @param displayName 显示名称
     * @param iconId      图标 id（可空）
     * @param select      是否同时设置为当前选中
     */
    void upsertExternalTarget(
        @NotNull Player player,
        @NotNull String targetId,
        @NotNull String source,
        @NotNull String worldId,
        double x, double y, double z,
        @NotNull String displayName,
        @Nullable String iconId,
        boolean select
    );

    /**
     * 清除指定来源的所有外部导航点。
     *
     * @param player   目标玩家
     * @param source   来源标识
     * @param syncView 是否立即同步视图
     */
    void clearExternalTargets(@NotNull Player player, @NotNull String source, boolean syncView);

    /**
     * 为玩家打开地图菜单。
     *
     * @param player    目标玩家
     * @param waypointId 默认选中的航点 id（可为空字符串）
     */
    void openMenuFor(@NotNull Player player, @NotNull String waypointId);
}
