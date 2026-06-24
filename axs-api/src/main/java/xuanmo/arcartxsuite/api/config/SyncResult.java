package xuanmo.arcartxsuite.api.config;

import java.io.File;
import java.util.List;

/**
 * 单个配置文件的同步结果。
 *
 * @param resourcePath   模块 Jar 内的资源路径
 * @param targetFile     宿主数据目录下的目标文件
 * @param created        本次是否创建了文件（不存在 → 写出默认值）
 * @param changed        本次是否对已有文件做出了修改（补缺失键 / 删废弃键）
 * @param skipped        本次是否因异常被跳过（保持原样）
 * @param message        说明信息（一般在 skipped 时填充）
 * @param addedPaths     本次新增的键路径列表
 * @param removedPaths   本次删除的键路径列表
 * @param backupFile     升级时自动备份生成的旧文件副本，无修改时为 null
 */
public record SyncResult(
    String resourcePath,
    File targetFile,
    boolean created,
    boolean changed,
    boolean skipped,
    String message,
    List<String> addedPaths,
    List<String> removedPaths,
    File backupFile
) {

    public SyncResult {
        addedPaths = addedPaths == null ? List.of() : List.copyOf(addedPaths);
        removedPaths = removedPaths == null ? List.of() : List.copyOf(removedPaths);
        message = message == null ? "" : message;
    }

    public static SyncResult skipped(String resourcePath, File targetFile, String message) {
        return new SyncResult(resourcePath, targetFile, false, false, true, message, List.of(), List.of(), null);
    }

    public static SyncResult unchanged(String resourcePath, File targetFile) {
        return new SyncResult(resourcePath, targetFile, false, false, false, "", List.of(), List.of(), null);
    }

    public int addedCount() {
        return addedPaths.size();
    }

    public int removedCount() {
        return removedPaths.size();
    }
}
