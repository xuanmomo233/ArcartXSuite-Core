package xuanmo.arcartxsuite.api.config;

import java.util.Map;
import java.util.Objects;

/**
 * 单次配置迁移操作。
 * <p>
 * 由 {@link ConfigMigrationDescriptor} 携带，按声明顺序执行。
 */
public sealed interface MigrationOperation
    permits MigrationOperation.Rename,
            MigrationOperation.Remove,
            MigrationOperation.Move,
            MigrationOperation.SetIfMissing,
            MigrationOperation.ValueMap {

    /** 同层级重命名一个键（{@code from} 与 {@code to} 在同一父节点下）。 */
    record Rename(String from, String to) implements MigrationOperation {
        public Rename {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    /** 删除指定路径。 */
    record Remove(String path) implements MigrationOperation {
        public Remove {
            Objects.requireNonNull(path, "path");
        }
    }

    /** 跨层级移动键值（{@code from} 路径整体迁到 {@code to}）。 */
    record Move(String from, String to) implements MigrationOperation {
        public Move {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    /** 仅当目标路径不存在时设置默认值。 */
    record SetIfMissing(String path, Object value) implements MigrationOperation {
        public SetIfMissing {
            Objects.requireNonNull(path, "path");
        }
    }

    /** 把指定字符串值按映射表替换（用于枚举重命名）。值不在表中则保持原样。 */
    record ValueMap(String path, Map<String, String> mapping) implements MigrationOperation {
        public ValueMap {
            Objects.requireNonNull(path, "path");
            mapping = Map.copyOf(Objects.requireNonNull(mapping, "mapping"));
        }
    }
}
