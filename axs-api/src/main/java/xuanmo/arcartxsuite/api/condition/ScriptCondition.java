package xuanmo.arcartxsuite.api.condition;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

public record ScriptCondition(
    ScriptConditionKind kind,
    String placeholder,
    ScriptConditionOperator operator,
    String value,
    String script,
    String raw
) {

    private static final Pattern INLINE_PAPI_PATTERN = Pattern.compile(
        "^(%[^%]+%)\\s+(==|!=|>=|<=|>|<|contains|regex)\\s+(.+)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INLINE_ARIA_PREFIX = Pattern.compile("^aria\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INLINE_JS_PREFIX = Pattern.compile("^js\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static ScriptCondition papi(String placeholder, ScriptConditionOperator operator, String value, String raw) {
        return new ScriptCondition(ScriptConditionKind.PAPI, placeholder, operator, value, null, raw);
    }

    public static ScriptCondition aria(String script, String raw) {
        return new ScriptCondition(ScriptConditionKind.ARIA, null, null, null, script, raw);
    }

    public static ScriptCondition js(String script, String raw) {
        return new ScriptCondition(ScriptConditionKind.JS, null, null, null, script, raw);
    }

    @Nullable
    public static ScriptCondition parseInline(String inline) {
        if (inline == null || inline.isBlank()) {
            return null;
        }
        String trimmed = inline.trim();
        Matcher jsMatcher = INLINE_JS_PREFIX.matcher(trimmed);
        if (jsMatcher.matches()) {
            String script = jsMatcher.group(1).trim();
            return script.isBlank() ? null : js(trimmed, trimmed);
        }
        Matcher ariaMatcher = INLINE_ARIA_PREFIX.matcher(trimmed);
        if (ariaMatcher.matches()) {
            String script = ariaMatcher.group(1).trim();
            return script.isBlank() ? null : aria(trimmed, trimmed);
        }
        Matcher papiMatcher = INLINE_PAPI_PATTERN.matcher(trimmed);
        if (!papiMatcher.matches()) {
            if (trimmed.contains("::")) {
                return deserialize(trimmed.replace("::", "\t"));
            }
            return null;
        }
        return papi(
            papiMatcher.group(1).trim(),
            ScriptConditionOperator.parse(papiMatcher.group(2).trim()),
            papiMatcher.group(3).trim(),
            trimmed
        );
    }

    @Nullable
    public static ScriptCondition fromSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String type = section.getString("type", section.getString("kind", "")).trim();
        if ("aria".equalsIgnoreCase(type)) {
            return parseAriaSection(section);
        }
        if ("js".equalsIgnoreCase(type)) {
            return parseJsSection(section);
        }
        String jsInline = firstNonBlank(
            section.getString("js"),
            section.getString("js-condition"),
            section.getString("jsCondition")
        );
        if (jsInline != null) {
            return js(jsInline, jsInline);
        }
        String ariaInline = firstNonBlank(
            section.getString("aria"),
            section.getString("aria-condition"),
            section.getString("ariaCondition")
        );
        if (ariaInline != null) {
            return aria(ariaInline, ariaInline);
        }
        String script = firstNonBlank(
            section.getString("script"),
            section.getString("expression"),
            section.getString("code")
        );
        if (script != null && isBlank(section.getString("placeholder")) && isBlank(section.getString("placeholders"))) {
            return aria(script, script);
        }
        String inline = firstNonBlank(section.getString("expr"), section.getString("expression"));
        if (inline != null) {
            ScriptCondition parsed = parseInline(inline);
            if (parsed != null) {
                return parsed;
            }
        }
        String placeholder = section.getString("placeholder", section.getString("placeholders", ""));
        if (placeholder == null || placeholder.isBlank()) {
            return null;
        }
        if (!placeholder.startsWith("%") || !placeholder.endsWith("%")) {
            placeholder = "%" + placeholder + "%";
        }
        String operator = section.getString("operator", section.getString("op", "=="));
        String value = section.getString("value", "");
        String raw = placeholder + " " + operator + " " + value;
        return papi(placeholder, ScriptConditionOperator.parse(operator), value, raw);
    }

    @Nullable
    private static ScriptCondition parseAriaSection(ConfigurationSection section) {
        String script = firstNonBlank(
            section.getString("script"),
            section.getString("expression"),
            section.getString("code"),
            section.getString("aria")
        );
        if (script == null) {
            return null;
        }
        return aria(script, script);
    }

    @Nullable
    private static ScriptCondition parseJsSection(ConfigurationSection section) {
        String script = firstNonBlank(
            section.getString("script"),
            section.getString("expression"),
            section.getString("code"),
            section.getString("js")
        );
        if (script == null) {
            return null;
        }
        return js(script, script);
    }

    public String serialize() {
        if (kind == ScriptConditionKind.JS) {
            String payload = script == null ? "" : script;
            return "js\t" + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        }
        if (kind == ScriptConditionKind.ARIA) {
            String payload = script == null ? "" : script;
            return "aria\t" + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        }
        String ph = placeholder == null ? "" : placeholder;
        String val = value == null ? "" : value;
        ScriptConditionOperator op = operator == null ? ScriptConditionOperator.EQ : operator;
        return ph + "\t" + op.configKey() + "\t" + val;
    }

    @Nullable
    public static ScriptCondition deserialize(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.startsWith("js\t") || trimmed.startsWith("js::")) {
            String encoded = trimmed.substring(trimmed.indexOf('\t') >= 0 ? trimmed.indexOf('\t') + 1 : 3);
            if (trimmed.startsWith("js::")) {
                encoded = trimmed.substring("js::".length());
            }
            try {
                String script = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                return js(script, trimmed);
            } catch (IllegalArgumentException exception) {
                return js(encoded, trimmed);
            }
        }
        if (trimmed.startsWith("aria\t") || trimmed.startsWith("aria::")) {
            String encoded = trimmed.substring(trimmed.indexOf('\t') >= 0 ? trimmed.indexOf('\t') + 1 : 6);
            if (trimmed.startsWith("aria::")) {
                encoded = trimmed.substring("aria::".length());
            }
            try {
                String script = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                return aria(script, trimmed);
            } catch (IllegalArgumentException exception) {
                return aria(encoded, trimmed);
            }
        }
        ScriptCondition inline = parseInline(trimmed);
        if (inline != null) {
            return inline;
        }
        String[] parts = trimmed.split("\t", 3);
        if (parts.length < 3) {
            parts = trimmed.split("::", 3);
        }
        if (parts.length < 3) {
            return null;
        }
        return papi(parts[0], ScriptConditionOperator.parse(parts[1]), parts[2], trimmed);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
