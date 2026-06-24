package xuanmo.arcartxsuite.api.config;

import java.util.List;
import java.util.Objects;

/**
 * 一组从 {@link #fromVersion()} 到 {@link #toVersion()} 的迁移操作。
 *
 * @param fromVersion 起始版本号（含）
 * @param toVersion   目标版本号（含），必须大于 fromVersion
 * @param operations  按顺序执行的迁移操作
 * @param description 描述（可空），用于报告
 */
public record ConfigMigrationDescriptor(
    int fromVersion,
    int toVersion,
    List<MigrationOperation> operations,
    String description
) {

    public ConfigMigrationDescriptor {
        if (toVersion <= fromVersion) {
            throw new IllegalArgumentException(
                "toVersion 必须大于 fromVersion: " + fromVersion + " -> " + toVersion);
        }
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        description = description == null ? "" : description;
    }
}
