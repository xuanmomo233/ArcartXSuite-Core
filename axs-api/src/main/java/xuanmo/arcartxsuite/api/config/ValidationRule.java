package xuanmo.arcartxsuite.api.config;

import java.util.Objects;
import java.util.Set;

/**
 * 单条配置项校验规则。
 * <p>
 * 字段为 null / 空集合表示该项不校验。
 *
 * @param path           YAML 路径（如 {@code "ui.attribute-line-color"}）
 * @param type           预期类型
 * @param required       是否必填（key 不存在 → ERROR）
 * @param min            数字下限（含），不限则 null
 * @param max            数字上限（含），不限则 null
 * @param allowedValues  允许的字符串值集合（用于枚举字段），不限则 null
 */
public record ValidationRule(
    String path,
    ValueType type,
    boolean required,
    Number min,
    Number max,
    Set<String> allowedValues
) {

    public ValidationRule {
        path = Objects.requireNonNull(path, "path");
        type = Objects.requireNonNull(type, "type");
        allowedValues = allowedValues == null ? null : Set.copyOf(allowedValues);
    }

    public static ValidationRule of(String path, ValueType type) {
        return new ValidationRule(path, type, false, null, null, null);
    }

    public static ValidationRule required(String path, ValueType type) {
        return new ValidationRule(path, type, true, null, null, null);
    }

    public ValidationRule withRange(Number min, Number max) {
        return new ValidationRule(path, type, required, min, max, allowedValues);
    }

    public ValidationRule withEnum(Set<String> values) {
        return new ValidationRule(path, type, required, min, max, values);
    }

    public ValidationRule asRequired() {
        return new ValidationRule(path, type, true, min, max, allowedValues);
    }
}
