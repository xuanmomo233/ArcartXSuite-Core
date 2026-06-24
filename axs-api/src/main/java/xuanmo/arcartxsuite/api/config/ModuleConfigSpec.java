package xuanmo.arcartxsuite.api.config;

import java.util.List;
import java.util.Objects;

/**
 * 模块（或宿主）声明的"内置默认配置 → 玩家服务器配置"完整规约。
 * <p>
 * 由 {@code ConfigDiagnosticEngine} 消费：
 * <ol>
 *     <li>读 {@link #sync()} 拷贝默认值或合并差异（结构层）</li>
 *     <li>读 {@link #versionPath()} 比较 {@link #currentVersion()}，从 {@link #migrationFolder()} 加载迁移（迁移层）</li>
 *     <li>用 {@link #validations()} 做类型 / 范围 / 枚举校验（值层）</li>
 * </ol>
 *
 * @param ownerId         报告归属（如 {@code "axs-core"}、{@code "title"}）
 * @param sync            主同步规约
 * @param currentVersion  代码内置的当前版本号
 * @param versionPath     存储版本号的 YAML 路径，常用 {@code "config-version"}
 * @param migrationFolder 模块 jar 内迁移目录（如 {@code "migrations"}），可空表示无迁移
 * @param validations     校验规则列表，可空
 */
public record ModuleConfigSpec(
    String ownerId,
    ConfigSyncSpec sync,
    int currentVersion,
    String versionPath,
    String migrationFolder,
    List<ValidationRule> validations
) {

    public ModuleConfigSpec {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        sync = Objects.requireNonNull(sync, "sync");
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion 必须 >= 0: " + currentVersion);
        }
        versionPath = (versionPath == null || versionPath.isBlank()) ? "config-version" : versionPath;
        migrationFolder = migrationFolder == null ? "" : migrationFolder;
        validations = validations == null ? List.of() : List.copyOf(validations);
    }

    /** 最简快捷构造：默认 version=1，无 migration，无 validation。 */
    public static ModuleConfigSpec basic(String ownerId, ConfigSyncSpec sync) {
        return new ModuleConfigSpec(ownerId, sync, 1, "config-version", "", List.of());
    }
}
