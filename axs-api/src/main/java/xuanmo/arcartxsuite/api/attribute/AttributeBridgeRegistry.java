package xuanmo.arcartxsuite.api.attribute;

import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 统一属性桥接注册表。
 * <p>
 * 宿主维护单例实例，涵盖所有已对接的属性插件（AttributePlus、CraneAttribute、MythicLib、Symphony）。
 * 模块通过 {@code context.attributeBridge()} 获取，不再各自创建反射桥接。
 * <p>
 * 每个子桥接独立初始化，对应插件未安装时 {@code available()} 返回 false，调用为空操作。
 */
@ApiStability.Stable
public interface AttributeBridgeRegistry {

    /** AttributePlus 桥接 */
    AttributePlusBridge attributePlus();

    /** CraneAttribute 桥接 */
    CraneAttributeBridge craneAttribute();

    /** MythicLib 桥接 */
    MythicLibBridge mythicLib();

    /** Symphony 桥接 */
    SymphonyBridge symphony();

    // ─── 通用属性伤害事件分发 ──────────────────────────────────

    /**
     * 是否有可用的属性伤害来源（任一属性插件已加载且桥接成功）。
     * <p>
     * 模块可用此判断是否需要注册 {@link AttributeDamageListener}。
     */
    boolean hasDamageSource();

    /**
     * 注册通用属性伤害事件监听器。
     * <p>
     * 当任一已对接属性插件触发伤害事件时，本体将其归一化为
     * {@link AttributeDamageEvent} 后调用此监听器。
     * 新增属性插件兼容时，模块无需调整。
     *
     * @param listener 监听器
     */
    void registerDamageListener(AttributeDamageListener listener);

    /**
     * 注销通用属性伤害事件监听器。
     *
     * @param listener 监听器
     */
    void unregisterDamageListener(AttributeDamageListener listener);

    // ─── 通用属性治疗事件分发 ──────────────────────────────────

    /**
     * 是否有可用的属性治疗来源（任一属性插件已加载且提供治疗事件桥接）。
     */
    boolean hasHealSource();

    /**
     * 注册通用属性治疗事件监听器。
     * <p>
     * 当已对接属性插件触发治疗事件时，本体将其归一化为
     * {@link AttributeHealEvent} 后调用此监听器。
     *
     * @param listener 监听器
     */
    void registerHealListener(AttributeHealListener listener);

    /**
     * 注销通用属性治疗事件监听器。
     *
     * @param listener 监听器
     */
    void unregisterHealListener(AttributeHealListener listener);
}
