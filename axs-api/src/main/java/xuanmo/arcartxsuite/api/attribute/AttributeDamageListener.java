package xuanmo.arcartxsuite.api.attribute;

import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 通用属性伤害事件监听器。
 * <p>
 * 模块通过 {@link AttributeBridgeRegistry#registerDamageListener(AttributeDamageListener)}
 * 注册此监听器，即可接收来自所有已对接属性插件的归一化伤害事件，无需自行反射。
 */
@ApiStability.Stable
@FunctionalInterface
public interface AttributeDamageListener {

    /**
     * 收到属性伤害事件时回调。
     *
     * @param event 归一化后的伤害事件
     */
    void onAttributeDamage(AttributeDamageEvent event);
}
