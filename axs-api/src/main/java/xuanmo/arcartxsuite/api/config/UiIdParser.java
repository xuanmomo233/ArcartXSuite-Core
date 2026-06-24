package xuanmo.arcartxsuite.api.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 通用 ui-id 解析工具：支持字符串或列表两种配置格式。
 * <pre>
 * # 旧格式（单 UI）
 * ui-id: "AXS:my_ui"
 *
 * # 新格式（多 UI）
 * ui-id:
 *   - "AXS:my_ui"
 *   - "AXS:my_ui_alt"
 * </pre>
 */
public final class UiIdParser {

    private UiIdParser() {}

    /**
     * 从 ConfigurationSection 中读取指定 key 的 ui-id，返回非空列表。
     * 支持字符串和列表两种格式；无有效值时返回仅含 fallback 的列表。
     */
    public static List<String> readUiIds(ConfigurationSection section, String key, String fallback) {
        if (section == null) {
            return List.of(fallback);
        }

        // 尝试列表
        List<?> rawList = section.getList(key);
        if (rawList != null && !rawList.isEmpty()) {
            List<String> ids = new ArrayList<>(rawList.size());
            for (Object raw : rawList) {
                if (raw == null) continue;
                String value = String.valueOf(raw).trim();
                if (!value.isBlank()) {
                    ids.add(value);
                }
            }
            if (!ids.isEmpty()) {
                return List.copyOf(ids);
            }
        }

        // 回退：字符串
        String single = section.getString(key);
        if (single != null && !single.trim().isBlank()) {
            return List.of(single.trim());
        }

        return List.of(fallback);
    }

    /**
     * 无 fallback 版本；无有效值时返回空列表。
     */
    public static List<String> readUiIds(ConfigurationSection section, String key) {
        if (section == null) {
            return List.of();
        }

        List<?> rawList = section.getList(key);
        if (rawList != null && !rawList.isEmpty()) {
            List<String> ids = new ArrayList<>(rawList.size());
            for (Object raw : rawList) {
                if (raw == null) continue;
                String value = String.valueOf(raw).trim();
                if (!value.isBlank()) {
                    ids.add(value);
                }
            }
            if (!ids.isEmpty()) {
                return List.copyOf(ids);
            }
        }

        String single = section.getString(key);
        if (single != null && !single.trim().isBlank()) {
            return List.of(single.trim());
        }

        return List.of();
    }
}
