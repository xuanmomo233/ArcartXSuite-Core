package xuanmo.arcartxsuite.api.attribute;

import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 通用属性治疗事件监听器。
 * <p>
 * 模块通过 {@link AttributeBridgeRegistry#registerHealListener(AttributeHealListener)}
 * 注册此监听器，即可接收来自所有已对接属性插件的归一化治疗事件，无需自行反射。
 */
@ApiStability.Stable
@FunctionalInterface
public interface AttributeHealListener {

    /**
     * 收到属性治疗事件时回调。
     *
     * @param event 归一化后的治疗事件
     */
    void onAttributeHeal(AttributeHealEvent event);
}
