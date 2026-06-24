package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

/**
 * 数据库跨源一键迁移能力接口。
 * <p>
 * 各有持久化存储的模块实现此接口并注册为 capability，
 * 由宿主的 {@code /axs migrate} 命令统一调度。
 */
public interface DatabaseMigratable {

    /**
     * 模块标识（如 "warehouse", "mail", "chat" 等）。
     */
    @NotNull String moduleId();

    /**
     * 将当前模块的数据库，迁移/无损同步到目标数据库描述符上。
     *
     * @param targetDescriptor 目标数据库描述符（目标 MySQL 或 SQLite 方言）
     * @param overwriteTarget  是否覆盖目标表上的数据
     * @return 迁移报告
     */
    @NotNull MigrationResult migrateDatabase(@NotNull StorageDescriptor targetDescriptor, boolean overwriteTarget);

    /**
     * 获取当前模块数据库正在使用的底层连接描述符（供克隆和克制方言映射）。
     */
    @NotNull StorageDescriptor currentDescriptor();
}
