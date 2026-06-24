package xuanmo.arcartxsuite.config.diagnostic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * 清理过期的 diagnosis / backup 目录。
 */
public final class RetentionCleaner {

    private final Logger logger;
    private final Duration diagnosisRetention;
    private final Duration backupRetention;
    private final int maxDiagnosisCount;

    public RetentionCleaner(Logger logger, Duration diagnosisRetention, Duration backupRetention, int maxDiagnosisCount) {
        this.logger = logger;
        this.diagnosisRetention = diagnosisRetention;
        this.backupRetention = backupRetention;
        this.maxDiagnosisCount = maxDiagnosisCount;
    }

    public void cleanup(File pluginDataFolder) {
        cleanDir(new File(pluginDataFolder, "diagnosis"), diagnosisRetention, maxDiagnosisCount);
        cleanDir(new File(pluginDataFolder, "backup"), backupRetention, 0);
    }

    private void cleanDir(File root, Duration retention, int maxCount) {
        if (!root.isDirectory()) {
            return;
        }
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        // 1. 按时间清理过期目录
        Instant cutoff = Instant.now().minus(retention);
        List<File> survivors = new ArrayList<>();
        for (File child : children) {
            try {
                Instant lastModified = Instant.ofEpochMilli(child.lastModified());
                if (lastModified.isBefore(cutoff)) {
                    deleteRecursive(child.toPath());
                    logger.fine("已清理过期目录: " + child);
                } else {
                    survivors.add(child);
                }
            } catch (IOException exception) {
                logger.warning("清理失败 " + child + ": " + exception.getMessage());
            }
        }
        // 2. 按数量上限清理（保留最新的）
        if (maxCount > 0 && survivors.size() > maxCount) {
            survivors.sort(Comparator.comparingLong(File::lastModified).reversed());
            for (int i = maxCount; i < survivors.size(); i++) {
                File child = survivors.get(i);
                try {
                    deleteRecursive(child.toPath());
                    logger.fine("已清理超量目录: " + child);
                } catch (IOException exception) {
                    logger.warning("清理失败 " + child + ": " + exception.getMessage());
                }
            }
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walker = Files.walk(path)) {
            walker.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
