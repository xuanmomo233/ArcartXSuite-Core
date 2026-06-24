package xuanmo.arcartxsuite.api.config;

/**
 * 配置项的预期值类型，用于 {@link ValidationRule} 和 {@code TypeCoercer}。
 * <p>
 * 阶段 A 仅覆盖通用基础类型；后续可扩展 {@code DURATION / COLOR_CODE / MATERIAL} 等业务类型。
 */
public enum ValueType {
    STRING,
    INT,
    LONG,
    DOUBLE,
    BOOLEAN,
    STRING_LIST,
    /** 该路径必须是一个 YAML section（嵌套对象）。 */
    SECTION
}
