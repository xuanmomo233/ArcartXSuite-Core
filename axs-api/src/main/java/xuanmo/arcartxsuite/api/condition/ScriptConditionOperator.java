package xuanmo.arcartxsuite.api.condition;

import java.util.Locale;
import java.util.regex.Pattern;

public enum ScriptConditionOperator {
    EQ("=="),
    NE("!="),
    GTE(">="),
    LTE("<="),
    GT(">"),
    LT("<"),
    CONTAINS("contains"),
    REGEX("regex");

    private final String symbol;

    ScriptConditionOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public String configKey() {
        return name();
    }

    public boolean evaluate(String actual, String expected) {
        if (actual == null) {
            actual = "";
        }
        if (expected == null) {
            expected = "";
        }
        return switch (this) {
            case EQ -> actual.equalsIgnoreCase(expected);
            case NE -> !actual.equalsIgnoreCase(expected);
            case GTE -> compareNumeric(actual, expected) >= 0;
            case LTE -> compareNumeric(actual, expected) <= 0;
            case GT -> compareNumeric(actual, expected) > 0;
            case LT -> compareNumeric(actual, expected) < 0;
            case CONTAINS -> actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case REGEX -> {
                try {
                    yield Pattern.compile(expected, Pattern.CASE_INSENSITIVE).matcher(actual).find();
                } catch (Exception exception) {
                    yield false;
                }
            }
        };
    }

    public static ScriptConditionOperator parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EQ;
        }
        String trimmed = raw.trim();
        for (ScriptConditionOperator operator : values()) {
            if (operator.symbol.equalsIgnoreCase(trimmed) || operator.name().equalsIgnoreCase(trimmed)) {
                return operator;
            }
        }
        return EQ;
    }

    private static int compareNumeric(String actual, String expected) {
        try {
            double actualValue = Double.parseDouble(actual.trim());
            double expectedValue = Double.parseDouble(expected.trim());
            return Double.compare(actualValue, expectedValue);
        } catch (NumberFormatException exception) {
            return actual.compareToIgnoreCase(expected);
        }
    }
}
