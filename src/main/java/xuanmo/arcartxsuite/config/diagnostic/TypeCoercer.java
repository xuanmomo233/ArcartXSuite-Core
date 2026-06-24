package xuanmo.arcartxsuite.config.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.config.ValueType;

/**
 * 类型安全转换器。
 * <p>
 * 不做"激进"转换：
 * <ul>
 *     <li>{@code "5" → 5} 允许（数字字符串可解析）</li>
 *     <li>{@code "yes" → true} 仅在严格集合内允许（true/false/yes/no/on/off）</li>
 *     <li>{@code 5 → "5"} 不允许（避免破坏用户原意，类型不符即报 ERROR）</li>
 *     <li>单值 → list 不允许</li>
 * </ul>
 * 转不动返回 {@link Optional#empty()}。
 */
public final class TypeCoercer {

    private TypeCoercer() {
    }

    /**
     * 检查 {@code value} 是否已是目标类型；若否，尝试安全转换。
     *
     * @return 若已合规则返回包含原值的 Optional；可安全转换则返回新值；否则 empty。
     */
    public static Optional<Object> coerce(Object value, ValueType target) {
        if (value == null) {
            return Optional.empty();
        }
        return switch (target) {
            case STRING -> value instanceof String ? Optional.of(value) : Optional.empty();
            case INT -> coerceInt(value);
            case LONG -> coerceLong(value);
            case DOUBLE -> coerceDouble(value);
            case BOOLEAN -> coerceBoolean(value);
            case STRING_LIST -> coerceStringList(value);
            case SECTION -> value instanceof ConfigurationSection ? Optional.of(value) : Optional.empty();
        };
    }

    /** 判断 {@code value} 是否已经是目标类型（不触发转换）。 */
    public static boolean matches(Object value, ValueType target) {
        if (value == null) {
            return false;
        }
        return switch (target) {
            case STRING -> value instanceof String;
            case INT -> value instanceof Integer;
            case LONG -> value instanceof Long || value instanceof Integer;
            case DOUBLE -> value instanceof Double || value instanceof Float
                || value instanceof Long || value instanceof Integer;
            case BOOLEAN -> value instanceof Boolean;
            case STRING_LIST -> value instanceof List<?> list && list.stream().allMatch(String.class::isInstance);
            case SECTION -> value instanceof ConfigurationSection;
        };
    }

    private static Optional<Object> coerceInt(Object value) {
        if (value instanceof Integer) {
            return Optional.of(value);
        }
        if (value instanceof Number n) {
            long longValue = n.longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE && n.doubleValue() == longValue) {
                return Optional.of((int) longValue);
            }
            return Optional.empty();
        }
        if (value instanceof String s) {
            try {
                return Optional.of(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> coerceLong(Object value) {
        if (value instanceof Long) {
            return Optional.of(value);
        }
        if (value instanceof Number n) {
            if (n.doubleValue() == n.longValue()) {
                return Optional.of(n.longValue());
            }
            return Optional.empty();
        }
        if (value instanceof String s) {
            try {
                return Optional.of(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> coerceDouble(Object value) {
        if (value instanceof Double) {
            return Optional.of(value);
        }
        if (value instanceof Number n) {
            return Optional.of(n.doubleValue());
        }
        if (value instanceof String s) {
            try {
                return Optional.of(Double.parseDouble(s.trim()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> coerceBoolean(Object value) {
        if (value instanceof Boolean) {
            return Optional.of(value);
        }
        if (value instanceof String s) {
            String trimmed = s.trim().toLowerCase();
            return switch (trimmed) {
                case "true", "yes", "on", "1" -> Optional.of(Boolean.TRUE);
                case "false", "no", "off", "0" -> Optional.of(Boolean.FALSE);
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    private static Optional<Object> coerceStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item == null) {
                    return Optional.empty();
                }
                result.add(String.valueOf(item));
            }
            return Optional.of(List.copyOf(result));
        }
        return Optional.empty();
    }
}
