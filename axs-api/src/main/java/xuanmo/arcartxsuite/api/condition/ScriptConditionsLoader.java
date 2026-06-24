package xuanmo.arcartxsuite.api.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;

public final class ScriptConditionsLoader {

    private ScriptConditionsLoader() {
    }

    public static @NotNull List<ScriptCondition> load(ConfigurationSection section, String... keys) {
        if (section == null || keys == null || keys.length == 0) {
            return List.of();
        }
        List<ScriptCondition> conditions = new ArrayList<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            boolean ariaKey = isAriaKey(key);
            boolean jsKey = isJsKey(key);
            appendInlineLines(conditions, section.getStringList(key), ariaKey, jsKey);
            appendMapList(conditions, section.getMapList(key));
            ConfigurationSection nested = section.getConfigurationSection(key);
            if (nested != null) {
                appendInlineLines(conditions, nested.getStringList("list"), ariaKey, jsKey);
                for (String childKey : nested.getKeys(false)) {
                    ConfigurationSection child = nested.getConfigurationSection(childKey);
                    if (child != null) {
                        ScriptCondition mapped = ScriptCondition.fromSection(child);
                        if (mapped != null) {
                            conditions.add(mapped);
                        }
                    }
                }
            }
        }
        return List.copyOf(conditions);
    }

    public static @NotNull List<ScriptCondition> loadInlineLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ScriptCondition> conditions = new ArrayList<>();
        appendInlineLines(conditions, lines, false, false);
        return List.copyOf(conditions);
    }

    private static void appendInlineLines(List<ScriptCondition> target, List<String> lines, boolean forceAria, boolean forceJs) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (forceJs) {
                target.add(ScriptCondition.js(line.trim(), line.trim()));
                continue;
            }
            if (forceAria) {
                target.add(ScriptCondition.aria(line.trim(), line.trim()));
                continue;
            }
            ScriptCondition condition = ScriptCondition.parseInline(line);
            if (condition != null) {
                target.add(condition);
            }
        }
    }

    private static void appendMapList(List<ScriptCondition> target, List<Map<?, ?>> maps) {
        if (maps == null) {
            return;
        }
        for (Map<?, ?> map : maps) {
            if (map == null || map.isEmpty()) {
                continue;
            }
            MemoryConfiguration memory = new MemoryConfiguration();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    memory.set(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            ScriptCondition inline = ScriptCondition.parseInline(
                memory.getString("expr", memory.getString("expression", ""))
            );
            if (inline != null) {
                target.add(inline);
                continue;
            }
            ScriptCondition structured = ScriptCondition.fromSection(memory);
            if (structured != null) {
                target.add(structured);
            }
        }
    }

    private static boolean isAriaKey(String key) {
        String normalized = key.trim().toLowerCase();
        return normalized.equals("aria")
            || normalized.equals("aria-conditions")
            || normalized.equals("ariaconditions")
            || normalized.equals("aria-condition")
            || normalized.equals("ariacondition");
    }

    private static boolean isJsKey(String key) {
        String normalized = key.trim().toLowerCase();
        return normalized.equals("js")
            || normalized.equals("js-conditions")
            || normalized.equals("jsconditions")
            || normalized.equals("js-condition")
            || normalized.equals("jscondition");
    }
}
