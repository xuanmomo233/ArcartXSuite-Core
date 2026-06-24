package xuanmo.arcartxsuite.config.diagnostic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.module.PluginConsoleLogger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.ConfigMigrationDescriptor;
import xuanmo.arcartxsuite.api.config.MigrationOperation;

/**
 * 从 ClassLoader 加载模块 jar 内的迁移声明文件。
 * <p>
 * 文件命名约定：{@code <migrationFolder>/<from>-<to>.yml}（如 {@code migrations/0-1.yml}）。
 *
 * <h3>YAML 格式</h3>
 * <pre>{@code
 * from-version: 0
 * to-version: 1
 * description: "重命名 chat 前缀字段"
 * operations:
 *   - type: rename
 *     from: legacy-key
 *     to: new-key
 *   - type: remove
 *     path: obsolete.key
 *   - type: move
 *     from: a.b
 *     to: c.d
 *   - type: set-if-missing
 *     path: ui.flag
 *     value: true
 *   - type: value-map
 *     path: status
 *     mapping:
 *       OLD: NEW
 * }</pre>
 */
public final class MigrationLoader {

    private static final Logger LOGGER = new PluginConsoleLogger("AXS-MigrationLoader", null);

    private MigrationLoader() {
    }

    /**
     * 在 {@code migrationFolder} 下扫描所有 {@code <from>-<to>.yml}，按 fromVersion 升序返回。
     */
    public static List<ConfigMigrationDescriptor> loadAll(
        String ownerId,
        String migrationFolder,
        ClassLoader classLoader,
        ProtectedResourceOpener opener
    ) {
        if (migrationFolder == null || migrationFolder.isBlank()) {
            return List.of();
        }
        // 阶段 A：通过列出 minVersion..maxVersion 范围内可能的文件名探测；
        // 由于无法在加密 axb 列表中扫描目录，调用方在阶段 B 可显式声明版本表。
        // 这里先尝试 0-1, 1-2, ... 直到连续 5 次未命中。
        List<ConfigMigrationDescriptor> all = new ArrayList<>();
        int from = 0;
        int missesInRow = 0;
        while (missesInRow < 5 && from < 1000) {
            String path = migrationFolder.replace('\\', '/');
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            String resourcePath = path + from + "-" + (from + 1) + ".yml";
            ConfigMigrationDescriptor descriptor = tryLoad(ownerId, resourcePath, classLoader, opener);
            if (descriptor == null) {
                missesInRow++;
            } else {
                all.add(descriptor);
                missesInRow = 0;
            }
            from++;
        }
        all.sort(Comparator.comparingInt(ConfigMigrationDescriptor::fromVersion));
        return List.copyOf(all);
    }

    private static ConfigMigrationDescriptor tryLoad(
        String ownerId, String resourcePath, ClassLoader classLoader, ProtectedResourceOpener opener) {
        try (InputStream input = opener.open(ownerId, resourcePath, classLoader)) {
            if (input == null) {
                return null;
            }
            byte[] bytes = input.readAllBytes();
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(bytes, StandardCharsets.UTF_8));
            return parse(yaml, resourcePath);
        } catch (IOException | InvalidConfigurationException exception) {
            LOGGER.warning("迁移文件加载失败 " + resourcePath + ": " + exception.getMessage());
            return null;
        }
    }

    private static ConfigMigrationDescriptor parse(YamlConfiguration yaml, String resourcePath) {
        int fromVersion = yaml.getInt("from-version", -1);
        int toVersion = yaml.getInt("to-version", -1);
        if (fromVersion < 0 || toVersion <= fromVersion) {
            LOGGER.warning("迁移文件版本字段无效 " + resourcePath + " (from=" + fromVersion + ", to=" + toVersion + ")");
            return null;
        }
        String description = yaml.getString("description", "");

        List<?> rawOperations = yaml.getList("operations");
        if (rawOperations == null) {
            return new ConfigMigrationDescriptor(fromVersion, toVersion, List.of(), description);
        }

        List<MigrationOperation> operations = new ArrayList<>();
        for (Object rawOp : rawOperations) {
            if (!(rawOp instanceof Map<?, ?> map)) {
                continue;
            }
            try {
                MigrationOperation op = parseOperation(map);
                if (op != null) {
                    operations.add(op);
                }
            } catch (RuntimeException exception) {
                LOGGER.warning("迁移操作解析失败 " + resourcePath + ": " + exception.getMessage());
            }
        }
        return new ConfigMigrationDescriptor(fromVersion, toVersion, operations, description);
    }

    private static MigrationOperation parseOperation(Map<?, ?> map) {
        Object type = map.get("type");
        if (!(type instanceof String typeStr)) {
            return null;
        }
        return switch (typeStr) {
            case "rename" -> new MigrationOperation.Rename(
                requireString(map, "from"), requireString(map, "to"));
            case "remove" -> new MigrationOperation.Remove(requireString(map, "path"));
            case "move" -> new MigrationOperation.Move(
                requireString(map, "from"), requireString(map, "to"));
            case "set-if-missing" -> new MigrationOperation.SetIfMissing(
                requireString(map, "path"), map.get("value"));
            case "value-map" -> new MigrationOperation.ValueMap(
                requireString(map, "path"), parseMapping(map.get("mapping")));
            default -> null;
        };
    }

    private static String requireString(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("缺少字段 " + key);
        }
        return String.valueOf(v);
    }

    private static Map<String, String> parseMapping(Object raw) {
        if (raw instanceof ConfigurationSection section) {
            Map<String, String> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                map.put(key, value == null ? "" : String.valueOf(value));
            }
            return map;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
            return result;
        }
        return Map.of();
    }

    /**
     * 资源打开钩子，便于 axs-core 根据 ownerId 路由到 {@code ProtectedResourceStore}。
     */
    @FunctionalInterface
    public interface ProtectedResourceOpener {
        /**
         * @param ownerId 调用方的逻辑 ID：可为 {@code "axs-core"} 表示宿主资源，
         *                或其他模块 ID。
         */
        InputStream open(String ownerId, String resourcePath, ClassLoader classLoader) throws IOException;
    }
}
