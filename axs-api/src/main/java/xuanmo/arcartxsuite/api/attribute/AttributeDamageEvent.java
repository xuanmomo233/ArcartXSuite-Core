package xuanmo.arcartxsuite.api.attribute;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 通用属性伤害事件。
 * <p>
 * 由 {@link AttributeBridgeRegistry} 统一从各属性插件（AttributePlus、CraneAttribute、MythicLib 等）
 * 的伤害事件归一化后分发。模块无需关心底层属性插件种类，只需订阅此事件。
 *
 * @param attacker 攻击者（玩家），可能为 null（如环境伤害、NPC 伤害等）
 * @param target   被击中的实体
 * @param damage   伤害数值
 * @param source   伤害来源类型
 */
@ApiStability.Stable
public record AttributeDamageEvent(
    @Nullable Player attacker,
    @NotNull Entity target,
    double damage,
    @NotNull Source source
) {

    /** 属性伤害来源类型 */
    @ApiStability.Stable
    public enum Source {
        /** AttributePlus */
        ATTRIBUTE_PLUS,
        /** CraneAttribute */
        CRANE_ATTRIBUTE,
        /** MythicLib / MMOItems */
        MYTHIC_LIB,
        /** Symphony */
        SYMPHONY,
        /** Bukkit 原版（兜底） */
        BUKKIT,
        /** 未知/其他 */
        OTHER
    }
}
