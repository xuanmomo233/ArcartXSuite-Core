package xuanmo.arcartxsuite.api.bridge;

import org.bukkit.Location;

/**
 * Adyeshach 附近 NPC 查询结果。
 *
 * @param npcId          NPC id
 * @param label          显示标签
 * @param location       NPC 位置
 * @param rawEntity      原始 Adyeshach 实体对象
 * @param conversationEntity 会话关联实体（可能为 null）
 * @param distanceSquared 与查询玩家的距离平方
 * @since 1.2.0
 */
public record AdyeshachNearbyNpc(
    String npcId,
    String label,
    Location location,
    Object rawEntity,
    Object conversationEntity,
    double distanceSquared
) {
}
