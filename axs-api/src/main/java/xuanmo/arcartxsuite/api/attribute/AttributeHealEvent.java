package xuanmo.arcartxsuite.api.attribute;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 通用属性治疗事件。
 * <p>
 * 由 {@link AttributeBridgeRegistry} 统一从各属性插件（CraneAttribute、Symphony 等）
 * 的治疗事件归一化后分发。模块无需关心底层属性插件种类，只需订阅此事件。
 *
 * @param source 治疗来源实体（如施法者/治疗者），可能为 null（如环境回血、药水等）
 * @param target 接受治疗的目标实体
 * @param amount 治疗数值
 * @param sourceType 治疗来源类型
 */
@ApiStability.Stable
public record AttributeHealEvent(
    @Nullable Player source,
    @NotNull LivingEntity target,
    double amount,
    @NotNull SourceType sourceType
) {

    /** 属性治疗来源类型 */
    @ApiStability.Stable
    public enum SourceType {
        /** CraneAttribute 恢复生命触发器 */
        CRANE_ATTRIBUTE,
        /** Symphony 治疗事件 */
        SYMPHONY,
        /** Bukkit 原版（兜底） */
        BUKKIT,
        /** 未知/其他 */
        OTHER
    }
}
