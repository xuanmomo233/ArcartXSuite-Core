package xuanmo.arcartxsuite.api.attribute;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * MythicLib 属性桥接。
 * <p>
 * 提供两种使用模式:
 * <ul>
 *   <li><b>临时修饰符</b>（Prop 使用）: {@link #registerTemporaryModifier} 自动过期</li>
 *   <li><b>持久修饰符</b>（Title 使用）: {@link #registerStatModifier} + {@link #removeStatModifier} + {@link #updateStat}</li>
 * </ul>
 */
@ApiStability.Stable
public interface MythicLibBridge {

    boolean available();

    /** 检查 statId 是否为已注册的 MythicLib 属性 */
    boolean isRegisteredStat(String statId);

    /** 获取 MMOPlayerData 对象 */
    @Nullable Object getPlayerData(Player player);

    /** 获取 StatMap 对象 */
    @Nullable Object getStatMap(Object playerData);

    // ─── 持久修饰符（Title 模式） ──────────────────────────────────

    /**
     * 创建并注册一个 StatModifier 到 PlayerData。
     * @return 修饰符句柄，用于后续 remove
     */
    @Nullable Object registerStatModifier(Object playerData, String modifierName, String statId, double value);

    /** 从 StatMap 中按 modifierName 移除 StatInstance 上的修饰符 */
    void removeStatModifier(Object statMap, String statId, String modifierName);

    /** 更新 StatMap 中指定 statId 的计算 */
    void updateStat(Object statMap, String statId);

    // ─── 临时修饰符（Prop 模式） ──────────────────────────────────

    /**
     * 注册一个自动过期的 TemporaryStatModifier。
     * @param durationMillis 持续时间（毫秒）
     * @return 修饰符句柄，可通过 {@link #closeTemporaryModifier} 提前关闭
     */
    @Nullable Object registerTemporaryModifier(Object playerData, String modifierName, String statId, double value, long durationMillis);

    /** 提前关闭一个 TemporaryStatModifier */
    void closeTemporaryModifier(Object handle);
}
