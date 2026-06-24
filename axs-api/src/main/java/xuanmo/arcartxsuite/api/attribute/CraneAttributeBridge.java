package xuanmo.arcartxsuite.api.attribute;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * CraneAttribute 属性桥接。
 * <p>
 * 核心操作:
 * <ul>
 *   <li>{@link #getAttrData(Player)} — 获取玩家属性数据对象</li>
 *   <li>{@link #addSource(Object, String, List)} — 添加命名来源（文本行）</li>
 *   <li>{@link #addStaticSource(Object, String, List)} — 添加静态命名来源</li>
 *   <li>{@link #removeSource(Object, String)} — 移除命名来源</li>
 *   <li>{@link #updateAttribute(Player)} — 刷新属性计算</li>
 * </ul>
 */
@ApiStability.Stable
public interface CraneAttributeBridge {

    boolean available();

    @Nullable Object getAttrData(Player player);

    /** 添加动态属性来源（优先使用 {@link #addStaticSource}，不可用时回退到此方法） */
    void addSource(Object attrData, String sourceName, List<String> lines);

    /** 添加静态属性来源（CraneAttribute 部分版本支持） */
    void addStaticSource(Object attrData, String sourceName, List<String> lines);

    /** 是否支持 addStaticSource */
    boolean supportsStaticSource();

    void removeSource(Object attrData, String sourceName);

    void updateAttribute(Player player);
}
