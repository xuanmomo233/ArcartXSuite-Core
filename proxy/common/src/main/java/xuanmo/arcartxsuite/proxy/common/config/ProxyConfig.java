package xuanmo.arcartxsuite.proxy.common.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

/**
 * 代理端插件共享配置读取器。
 * 支持从 classpath 默认配置 + 运行时文件覆写。
 */
public class ProxyConfig {

    private final Logger logger;
    private final File dataFolder;

    private boolean debug = false;
    private boolean denyOffline = true;
    private String kickOfflineMessage = "&c本服务器仅支持正版/LittleSkin 账号登录";
    private boolean autoAssignUuid = true;
    private final List<YggdrasilSource> sources = new ArrayList<>();

    public ProxyConfig(Logger logger, File dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    public void loadDefaults() {
        sources.clear();
        sources.add(new YggdrasilSource(
            "Mojang",
            "https://sessionserver.mojang.com/",
            true,
            false,
            null
        ));
        sources.add(new YggdrasilSource(
            "LittleSkin",
            "https://littleskin.cn/api/yggdrasil",
            true,
            false,
            null
        ));
    }

    /**
     * 释放默认配置并加载 dataFolder 下的 YAML 文件。
     */
    public void load(String configFileName) {
        extractDefaultConfig(configFileName);
        File target = new File(dataFolder, configFileName);
        if (!target.exists()) {
            logger.warning("配置文件不存在，使用内置默认值: " + configFileName);
            loadDefaults();
            return;
        }
        try {
            loadFromYaml(target);
        } catch (IOException exception) {
            logger.warning("读取 " + configFileName + " 失败，使用内置默认值: " + exception.getMessage());
            loadDefaults();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromYaml(File file) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(file.toPath())) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> map)) {
                loadDefaults();
                return;
            }
            root = (Map<String, Object>) map;
        }

        debug = readBoolean(root, "debug", debug);
        denyOffline = readBoolean(root, "deny-offline", denyOffline);
        kickOfflineMessage = readString(root, "kick-offline-message", kickOfflineMessage);
        autoAssignUuid = readBoolean(root, "auto-assign-uuid", autoAssignUuid);

        sources.clear();
        Object sourcesNode = root.get("sources");
        if (sourcesNode instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> sourceMap) {
                    sources.add(parseSource((Map<String, Object>) sourceMap));
                }
            }
        }
        if (sources.isEmpty()) {
            logger.warning("proxy-config.yml 未配置 sources，回退内置默认值。");
            loadDefaults();
        }
    }

    private static YggdrasilSource parseSource(Map<String, Object> section) {
        return new YggdrasilSource(
            readString(section, "name", "source"),
            readString(section, "api-url", ""),
            readBoolean(section, "enabled", true),
            readBoolean(section, "allow-offline-fallback", false),
            readString(section, "server-id", null)
        );
    }

    private static String readString(Map<String, Object> section, String key, String fallback) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static boolean readBoolean(Map<String, Object> section, String key, boolean fallback) {
        Object value = section.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return fallback;
    }

    public boolean debug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean denyOffline() {
        return denyOffline;
    }

    public void setDenyOffline(boolean denyOffline) {
        this.denyOffline = denyOffline;
    }

    public String kickOfflineMessage() {
        return kickOfflineMessage;
    }

    public void setKickOfflineMessage(String kickOfflineMessage) {
        this.kickOfflineMessage = kickOfflineMessage;
    }

    public boolean autoAssignUuid() {
        return autoAssignUuid;
    }

    public void setAutoAssignUuid(boolean autoAssignUuid) {
        this.autoAssignUuid = autoAssignUuid;
    }

    @NotNull
    public List<YggdrasilSource> sources() {
        return new ArrayList<>(sources);
    }

    public void addSource(YggdrasilSource source) {
        sources.add(source);
    }

    public void clearSources() {
        sources.clear();
    }

    /**
     * 根据玩家名判断应该路由到哪个 Yggdrasil 源。
     * 简单实现：优先尝试 LittleSkin，失败则 fallback 到 Mojang。
     * 可扩展为基于玩家名前缀、数据库映射等复杂规则。
     */
    public List<YggdrasilSource> resolveSourcesFor(String username) {
        List<YggdrasilSource> result = new ArrayList<>();
        for (YggdrasilSource source : sources) {
            if (source.enabled()) {
                result.add(source);
            }
        }
        return result;
    }

    /**
     * 从 classpath 复制默认配置到 dataFolder。
     */
    public void extractDefaultConfig(String resourceName) {
        File target = new File(dataFolder, resourceName);
        if (target.exists()) {
            return;
        }
        try (InputStream in = ProxyConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                logger.warning("默认配置未找到: " + resourceName);
                return;
            }
            dataFolder.mkdirs();
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("已释放默认配置: " + target.getAbsolutePath());
        } catch (IOException e) {
            logger.warning("释放默认配置失败: " + e.getMessage());
        }
    }
}
