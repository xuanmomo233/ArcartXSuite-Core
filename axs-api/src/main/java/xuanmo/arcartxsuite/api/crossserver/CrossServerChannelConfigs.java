package xuanmo.arcartxsuite.api.crossserver;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 从模块 YAML {@code cross-server} 节解析通道配置。
 */
public final class CrossServerChannelConfigs {

    private CrossServerChannelConfigs() {
    }

    public static CrossServerChannelConfig fromSection(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return CrossServerChannelConfig.disabled();
        }
        Boolean redisEnabled = readOptionalBoolean(section.getConfigurationSection("redis"), "enabled");
        Boolean proxyEnabled = readOptionalBoolean(section.getConfigurationSection("proxy"), "enabled");
        return new CrossServerChannelConfig(true, redisEnabled, proxyEnabled);
    }

    private static Boolean readOptionalBoolean(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) {
            return null;
        }
        return section.getBoolean(path);
    }
}
