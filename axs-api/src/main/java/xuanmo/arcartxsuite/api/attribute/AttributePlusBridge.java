package xuanmo.arcartxsuite.api.attribute;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * AttributePlus 属性桥接。
 * <p>
 * 宿主通过反射初始化，模块通过 {@code context.attributeBridge().attributePlus()} 获取。
 * <p>
 * 核心操作:
 * <ul>
 *   <li>{@link #getAttrData(Player)} — 获取玩家属性数据对象</li>
 *   <li>{@link #getAttributeSource(List)} — 将文本行转换为 AttributeSource 对象</li>
 *   <li>{@link #addSource(Object, String, Object)} — 为属性数据添加命名来源</li>
 *   <li>{@link #removeSource(Object, String)} — 移除命名来源</li>
 * </ul>
 */
@ApiStability.Stable
public interface AttributePlusBridge {

    /** 桥接是否可用（插件已加载且 API 反射成功） */
    boolean available();

    /** 获取玩家 AttributeData 对象 */
    @Nullable Object getAttrData(Player player);

    /** 将属性文本行列表转换为 AttributeSource 对象 */
    @Nullable Object getAttributeSource(List<String> lines);

    /** 向 AttributeData 添加命名属性来源（Prop 模式：先 getAttributeSource 再传入） */
    void addSource(Object attrData, String sourceId, Object attributeSource);

    /**
     * 直接以文本行列表添加命名属性来源（Title 模式）。
     * <p>
     * 优先使用 4 参数 {@code addSourceAttribute(data, name, lines, false)}，
     * 不可用时回退到 3 参数 {@code addStaticAttributeSource(data, name, lines)}。
     */
    void addSourceLines(Object attrData, String sourceName, List<String> lines);

    /** 从 AttributeData 移除命名属性来源 */
    void removeSource(Object attrData, String sourceId);

    /** 刷新玩家属性计算（部分 AP 版本支持） */
    void updateAttribute(Player player, Object attrData);
}
