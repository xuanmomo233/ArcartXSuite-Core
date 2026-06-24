package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;

/**
 * 拾取通知能力接口。
 * <p>
 * 由 Pickup 模块（通知模式）实现，供 Warehouse 等模块查询
 * 某玩家是否已有 HUD 拾取通知，以避免重复的聊天栏提示。
 */
public interface PickupNotifiable {

    /**
     * 查询该玩家的拾取 HUD 通知是否处于活跃状态。
     *
     * @param playerId 玩家 UUID
     * @return {@code true} 表示通知模式已启用且该玩家未关闭
     */
    boolean isNotificationActive(UUID playerId);
}
