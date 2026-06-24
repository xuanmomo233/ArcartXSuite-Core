package xuanmo.arcartxsuite.api.config;

import java.time.Instant;
import java.util.*;

/**
 * 配置快照。
 * <p>
 * 记录某个时间点配置文件的完整状态，包括：
 * <ul>
 *   <li>文件元数据（路径、版本号、哈希）</li>
 *   <li>所有字段的值及其来源标记</li>
 * </ul>
 *
 * @param ownerId     模块/宿主 ID
 * @param filePath    配置文件相对路径
 * @param timestamp   快照时间
 * @param version     配置版本号
 * @param contentHash 文件内容哈希（MD5/SHA-256）
 * @param entries     字段快照条目
 *
 * @author 墨墨啊
 * @since 1.0.2-beta
 */
public record ConfigSnapshot(
    String ownerId,
    String filePath,
    Instant timestamp,
    int version,
    String contentHash,
    Map<String, SnapshotEntry> entries
) {

    public ConfigSnapshot {
        entries = entries == null ? Map.of() : Map.copyOf(entries);
    }

    /**
     * 获取指定路径的快照条目。
     */
    public Optional<SnapshotEntry> getEntry(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    /**
     * 判断与另一个快照是否相同（基于哈希）。
     */
    public boolean isSameAs(ConfigSnapshot other) {
        return other != null && this.contentHash.equals(other.contentHash);
    }

    /**
     * 单个字段的快照条目。
     *
     * @param path         字段路径
     * @param value        值（序列化为字符串）
     * @param type         值类型
     * @param source       来源标记
     * @param hash         该字段的局部哈希（用于快速比对）
     */
    public record SnapshotEntry(
        String path,
        String value,
        String type,
        ChangeSource source,
        String hash
    ) {
        /**
         * 从实际值创建条目。
         */
        public static SnapshotEntry fromValue(String path, Object value, ChangeSource source) {
            String strValue = value == null ? "null" : value.toString();
            String type = value == null ? "null" : value.getClass().getSimpleName();
            String hash = computeHash(path, strValue);
            return new SnapshotEntry(path, strValue, type, source, hash);
        }

        private static String computeHash(String path, String value) {
            // 简单的哈希计算，实际可使用 MD5
            return Integer.toHexString((path + "=" + value).hashCode());
        }
    }

    // 便捷工厂方法
    public static ConfigSnapshot create(
        String ownerId,
        String filePath,
        int version,
        String contentHash,
        Map<String, SnapshotEntry> entries
    ) {
        return new ConfigSnapshot(ownerId, filePath, Instant.now(), version, contentHash, entries);
    }

    public static ConfigSnapshot empty(String ownerId, String filePath) {
        return new ConfigSnapshot(ownerId, filePath, Instant.now(), -1, "", Map.of());
    }
}
