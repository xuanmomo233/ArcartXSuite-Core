package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AfkReward 挂机状态查询与控制能力接口。
 * <p>
 * 由 AfkReward 模块实现，供 Essentials 等模块跨模块查询玩家挂机状态或触发原地挂机。
 */
public interface AfkRewardDispatchable {

    /**
     * 查询玩家是否正在挂机（REGION 或 MANUAL 模式）。
     */
    boolean isAfk(@NotNull UUID playerUuid);

    /**
     * 获取玩家当前挂机区域名，未挂机时返回 null。
     */
    @Nullable
    String getAreaName(@NotNull UUID playerUuid);

    /**
     * 获取玩家本次挂机已持续秒数，未挂机时返回 0。
     */
    int getAfkSeconds(@NotNull UUID playerUuid);

    /**
     * 获取玩家当前挂机模式，未挂机时返回 null。
     * @return "REGION" / "MANUAL" / null
     */
    @Nullable
    String getAfkMode(@NotNull UUID playerUuid);

    /**
     * 让指定玩家开始原地挂机到指定区域。
     * <p>
     * 供 Essentials 等模块在 AFK 超时后自动触发原地挂机。
     *
     * @param player   目标玩家
     * @param areaName 区域名
     * @return {@code true} 表示成功启动
     */
    boolean startManualAfk(@NotNull Player player, @NotNull String areaName);
}
