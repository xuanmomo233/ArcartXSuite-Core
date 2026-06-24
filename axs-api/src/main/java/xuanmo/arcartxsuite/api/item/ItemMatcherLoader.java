package xuanmo.arcartxsuite.api.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public final class ItemMatcherLoader {

    private ItemMatcherLoader() {
    }

    public static ItemMatcher load(ConfigurationSection section, Logger logger, String path) {
        if (section == null) {
            return ItemMatcher.empty();
        }
        return new ItemMatcher(
            normalizeStringList(section.getStringList("material-ids")),
            normalizeStringList(section.getStringList("mythic-item-ids")),
            normalizeStringList(section.getStringList("neige-item-ids")),
            normalizeStringList(section.getStringList("overture-item-ids")),
            normalizeStringList(section.getStringList("kinds")),
            normalizeStringList(section.getStringList("name-contains")),
            normalizeStringList(section.getStringList("lore-contains")),
            compilePatterns(section.getStringList("name-regex"), logger, path + ".name-regex"),
            compilePatterns(section.getStringList("lore-regex"), logger, path + ".lore-regex")
        );
    }

    public static List<String> normalizeStringList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            String normalizedValue = normalizeId(value);
            if (!normalizedValue.isBlank()) {
                normalized.add(normalizedValue);
            }
        }
        return List.copyOf(normalized);
    }

    public static List<Pattern> compilePatterns(List<String> values, Logger logger, String path) {
        List<Pattern> patterns = new ArrayList<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException exception) {
                logger.warning(path + " 存在无效正则 '" + value + "'，已跳过。");
            }
        }
        return List.copyOf(patterns);
    }

    public static String normalizeId(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }
}
