package xuanmo.arcartxsuite.api.config;

import java.util.*;

/**
 * 快照对比引擎。
 * <p>
 * 对比两个配置快照，生成详细的差异报告，
 * 标识每个变更的来源（Jar 新增、用户修改、字段删除等）。
 *
 * @author 墨墨啊
 * @since 1.0.2-beta
 */
public class SnapshotDiffEngine {

    /**
     * 对比两个快照生成差异。
     *
     * @param oldSnapshot  旧快照（上次诊断时）
     * @param newSnapshot  新快照（本次诊断时）
     * @return 差异结果
     */
    public DiffResult compare(ConfigSnapshot oldSnapshot, ConfigSnapshot newSnapshot) {
        if (oldSnapshot == null || newSnapshot == null) {
            return DiffResult.empty();
        }

        List<FieldChange> changes = new ArrayList<>();
        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(oldSnapshot.entries().keySet());
        allPaths.addAll(newSnapshot.entries().keySet());

        for (String path : allPaths) {
            Optional<ConfigSnapshot.SnapshotEntry> oldEntry = oldSnapshot.getEntry(path);
            Optional<ConfigSnapshot.SnapshotEntry> newEntry = newSnapshot.getEntry(path);

            if (oldEntry.isPresent() && newEntry.isPresent()) {
                // 字段存在，检查是否修改
                ConfigSnapshot.SnapshotEntry old = oldEntry.get();
                ConfigSnapshot.SnapshotEntry ne = newEntry.get();

                if (!old.hash().equals(ne.hash())) {
                    // 值被修改
                    ChangeType type = detectChangeType(old, ne);
                    changes.add(new FieldChange(
                        path, type, old.value(), ne.value(),
                        old.source(), ne.source(),
                        generateSuggestion(type, path, ne.value())
                    ));
                }
            } else if (oldEntry.isPresent()) {
                // 字段被删除
                ConfigSnapshot.SnapshotEntry old = oldEntry.get();
                changes.add(new FieldChange(
                    path, ChangeType.REMOVED, old.value(), null,
                    old.source(), ChangeSource.UNKNOWN,
                    "该字段在本次诊断中已不存在"
                ));
            } else {
                // 新增字段
                ConfigSnapshot.SnapshotEntry ne = newEntry.get();
                ChangeType type = ne.source() == ChangeSource.JAR_DEFAULT
                    ? ChangeType.JAR_ADDED
                    : ChangeType.USER_ADDED;
                changes.add(new FieldChange(
                    path, type, null, ne.value(),
                    ChangeSource.UNKNOWN, ne.source(),
                    type == ChangeType.JAR_ADDED
                        ? "本次更新新增字段，已使用默认值"
                        : "用户新增自定义字段"
                ));
            }
        }

        return new DiffResult(oldSnapshot.timestamp(), newSnapshot.timestamp(), changes);
    }

    /**
     * 通过对比 Jar 默认配置和用户配置，推断变更类型。
     */
    public static List<FieldChange> compareWithDefault(
        ConfigSnapshot jarSnapshot,
        ConfigSnapshot userSnapshot,
        SyncPolicy policy
    ) {
        List<FieldChange> changes = new ArrayList<>();

        for (String path : userSnapshot.entries().keySet()) {
            ConfigSnapshot.SnapshotEntry userEntry = userSnapshot.entries().get(path);
            ConfigSnapshot.SnapshotEntry jarEntry = jarSnapshot.entries().get(path);

            if (jarEntry == null) {
                // 用户有，Jar 没有
                boolean isDynamic = isDynamicSection(path, policy);
                changes.add(new FieldChange(
                    path,
                    isDynamic ? ChangeType.USER_DYNAMIC : ChangeType.USER_DEPRECATED,
                    null, userEntry.value(),
                    ChangeSource.UNKNOWN, userEntry.source(),
                    isDynamic
                        ? "用户自定义内容（位于动态节）"
                        : "该字段在最新版本中已不存在，建议删除"
                ));
            } else if (!jarEntry.hash().equals(userEntry.hash())) {
                // 两者都有，但值不同 → 用户修改过
                changes.add(new FieldChange(
                    path,
                    ChangeType.USER_MODIFIED,
                    jarEntry.value(), userEntry.value(),
                    ChangeSource.JAR_DEFAULT, ChangeSource.USER_MODIFIED,
                    "用户自定义值（默认值: " + jarEntry.value() + "）"
                ));
            }
            // 相同则无需报告
        }

        // 检查 Jar 有但用户没有的字段（缺失）
        for (String path : jarSnapshot.entries().keySet()) {
            if (!userSnapshot.entries().containsKey(path)) {
                ConfigSnapshot.SnapshotEntry jarEntry = jarSnapshot.entries().get(path);
                changes.add(new FieldChange(
                    path,
                    ChangeType.MISSING,
                    jarEntry.value(), null,
                    ChangeSource.JAR_DEFAULT, ChangeSource.UNKNOWN,
                    "配置缺失，将从默认值合并: " + jarEntry.value()
                ));
            }
        }

        return changes;
    }

    private static boolean isDynamicSection(String path, SyncPolicy policy) {
        if (policy == null) return false;
        // 检查路径是否匹配动态节
        return policy.isDynamicSection(path);
    }

    private ChangeType detectChangeType(ConfigSnapshot.SnapshotEntry old, ConfigSnapshot.SnapshotEntry ne) {
        if (old.source() == ChangeSource.JAR_DEFAULT && ne.source() == ChangeSource.USER_MODIFIED) {
            return ChangeType.USER_MODIFIED;
        }
        if (ne.source() == ChangeSource.JAR_NEW) {
            return ChangeType.JAR_ADDED;
        }
        return ChangeType.MODIFIED;
    }

    private String generateSuggestion(ChangeType type, String path, String newValue) {
        return switch (type) {
            case USER_MODIFIED -> "用户已自定义此字段";
            case JAR_ADDED -> "本次更新新增，当前值: " + newValue;
            case REMOVED -> "该字段已移除";
            default -> "字段值已变更";
        };
    }

    // 变更类型枚举
    public enum ChangeType {
        USER_MODIFIED,  // 用户修改了默认值
        USER_ADDED,     // 用户新增字段
        USER_DYNAMIC,   // 用户在动态节添加的内容
        USER_DEPRECATED, // 用户保留了废弃字段
        JAR_ADDED,      // Jar 新增字段（用户尚未配置）
        MISSING,        // Jar 有但用户配置缺失
        REMOVED,        // 上次有这次没了
        MODIFIED        // 其他修改
    }

    // 字段变更记录
    public record FieldChange(
        String path,
        ChangeType type,
        String oldValue,
        String newValue,
        ChangeSource oldSource,
        ChangeSource newSource,
        String suggestion
    ) {
        public boolean isUserChange() {
            return type == ChangeType.USER_MODIFIED
                || type == ChangeType.USER_ADDED
                || type == ChangeType.USER_DYNAMIC;
        }

        public boolean needsAttention() {
            return type == ChangeType.USER_DEPRECATED
                || type == ChangeType.MISSING
                || type == ChangeType.REMOVED;
        }

        public String summary() {
            return switch (type) {
                case USER_MODIFIED -> "✏️ 用户修改: " + path;
                case USER_ADDED -> "🔧 用户新增: " + path;
                case USER_DYNAMIC -> "🔧 动态内容: " + path;
                case USER_DEPRECATED -> "🗑️ 废弃字段: " + path;
                case JAR_ADDED -> "✨ Jar新增: " + path;
                case MISSING -> "❌ 配置缺失: " + path;
                case REMOVED -> "🗑️ 字段移除: " + path;
                case MODIFIED -> "📝 内容变更: " + path;
            };
        }
    }

    // 对比结果
    public record DiffResult(
        java.time.Instant oldTimestamp,
        java.time.Instant newTimestamp,
        List<FieldChange> changes
    ) {
        public static DiffResult empty() {
            return new DiffResult(null, null, List.of());
        }

        public boolean hasChanges() {
            return !changes.isEmpty();
        }

        public long userChangeCount() {
            return changes.stream().filter(FieldChange::isUserChange).count();
        }

        public long attentionCount() {
            return changes.stream().filter(FieldChange::needsAttention).count();
        }
    }
}
