package xuanmo.arcartxsuite.api.message;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 模块消息提供者：从 messages.yml 加载可自定义消息文本。
 * <p>
 * 使用方式：
 * <pre>{@code
 * MessageProvider msg = new MessageProvider(dataFolder, "messages.yml", getClass().getClassLoader(), logger);
 * msg.load();
 * String text = msg.get("purge.confirm", "10");  // 用 {0} 占位符
 * }</pre>
 */
public final class MessageProvider {

    private final File file;
    private final String resourcePath;
    private final ClassLoader classLoader;
    private final Logger logger;
    private final Map<String, String> messages = new HashMap<>();

    public MessageProvider(@NotNull File dataFolder, @NotNull String fileName,
                           @NotNull ClassLoader classLoader, @NotNull Logger logger) {
        this.file = new File(dataFolder, fileName);
        this.resourcePath = fileName;
        this.classLoader = classLoader;
        this.logger = logger;
    }

    /**
     * 加载或重载消息文件。先导出默认文件（若不存在），再读取用户自定义版本。
     */
    public void load() {
        messages.clear();
        exportDefaultIfAbsent();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                messages.put(key, ChatColor.translateAlternateColorCodes('&', yaml.getString(key, "")));
            }
        }
        logger.fine("[Messages] 加载 " + messages.size() + " 条消息 from " + file.getName());
    }

    /**
     * 获取消息，支持 {0} {1} ... 占位符替换。
     *
     * @param key  消息键（YAML 路径，如 "purge.confirm"）
     * @param args 替换参数
     * @return 处理后的消息文本；键不存在时返回原始键名
     */
    @NotNull
    public String get(@NotNull String key, @Nullable Object... args) {
        String template = messages.get(key);
        if (template == null) {
            return key;
        }
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                template = template.replace("{" + i + "}", args[i] == null ? "" : args[i].toString());
            }
        }
        return template;
    }

    /**
     * 检查消息键是否存在。
     */
    public boolean has(@NotNull String key) {
        return messages.containsKey(key);
    }

    /**
     * 消息总数。
     */
    public int size() {
        return messages.size();
    }

    private void exportDefaultIfAbsent() {
        if (file.exists()) return;
        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
            if (input != null) {
                file.getParentFile().mkdirs();
                Files.copy(input, file.toPath());
            }
        } catch (IOException e) {
            logger.warning("[Messages] 导出默认消息文件失败: " + e.getMessage());
        }
    }
}
