package xuanmo.arcartxsuite.api.attribute;

import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * Symphony 属性桥接。
 * <p>
 * 核心操作:
 * <ul>
 *   <li>{@link #setAttribute(Player, String, boolean, double, String)} — 设置属性</li>
 *   <li>{@link #removeAttribute(Player, String)} — 移除属性源</li>
 *   <li>{@link #recalculate(Player)} — 重新计算属性</li>
 * </ul>
 */
@ApiStability.Stable
public interface SymphonyBridge {

    boolean available();

    /**
     * 设置属性修饰符。
     * @param percent true=百分比(PERCENT)，false=固定值(FLAT)
     * @param sourceKey 来源标识，用于后续移除
     */
    void setAttribute(Player player, String attributeId, boolean percent, double value, String sourceKey);

    /** 移除指定 sourceKey 的属性 */
    void removeAttribute(Player player, String sourceKey);

    /** 重新计算玩家全部属性 */
    void recalculate(Player player);
}
