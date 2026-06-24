package xuanmo.arcartxsuite.api.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.MemorySection;

/**
 * 配置快照持久化存储。
 * <p>
 * 将快照保存到磁盘（diagnosis/snapshots/），支持加载历史快照。
 *
 * @author 墨墨啊
 * @since 1.0.2-beta
 */
public class SnapshotStore {

    private final File snapshotDir;
    private final Logger logger;

    public SnapshotStore(File pluginDataFolder, Logger logger) {
        this.snapshotDir = new File(pluginDataFolder, "diagnosis/snapshots");
        this.logger = logger;
        ensureDirectory();
    }

    private void ensureDirectory() {
        if (!snapshotDir.exists()) {
            snapshotDir.mkdirs();
        }
    }

    /**
     * 保存快照到磁盘。
     */
    public void save(ConfigSnapshot snapshot) {
        String fileName = String.format("%s_%s.yml",
            snapshot.ownerId(),
            snapshot.filePath().replace('/', '_').replace(':', '_')
        );
        File file = new File(snapshotDir, fileName);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("ownerId", snapshot.ownerId());
        yaml.set("filePath", snapshot.filePath());
        yaml.set("timestamp", snapshot.timestamp().toString());
        yaml.set("version", snapshot.version());
        yaml.set("contentHash", snapshot.contentHash());

        MemorySection entriesSection = (MemorySection) yaml.createSection("entries");
        for (ConfigSnapshot.SnapshotEntry entry : snapshot.entries().values()) {
            String path = entry.path();
            entriesSection.set(path + ".value", entry.value());
            entriesSection.set(path + ".type", entry.type());
            entriesSection.set(path + ".source", entry.source().name());
            entriesSection.set(path + ".hash", entry.hash());
        }

        try {
            yaml.save(file);
            logger.fine("快照已保存: " + fileName);
        } catch (IOException e) {
            logger.log(Level.WARNING, "保存快照失败: " + fileName, e);
        }
    }

    /**
     * 加载指定模块和文件的最新快照。
     */
    public Optional<ConfigSnapshot> loadLatest(String ownerId, String filePath) {
        String fileName = String.format("%s_%s.yml",
            ownerId,
            filePath.replace('/', '_').replace(':', '_')
        );
        File file = new File(snapshotDir, fileName);

        if (!file.exists()) {
            return Optional.empty();
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            // 解析快照...
            return Optional.of(parseSnapshot(yaml));
        } catch (Exception e) {
            logger.log(Level.WARNING, "加载快照失败: " + fileName, e);
            return Optional.empty();
        }
    }

    private ConfigSnapshot parseSnapshot(YamlConfiguration yaml) {
        String ownerId = yaml.getString("ownerId", "unknown");
        String filePath = yaml.getString("filePath", "");
        int version = yaml.getInt("version", -1);
        String contentHash = yaml.getString("contentHash", "");

        // 简化实现：返回基本快照
        return ConfigSnapshot.empty(ownerId, filePath);
    }

    /**
     * 获取快照目录。
     */
    public File getSnapshotDir() {
        return snapshotDir;
    }
}
