package xuanmo.arcartxsuite.crossserver;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 宿主 {@code config.yml} 中 {@code cross-server} 节。
 */
public record CrossServerGlobalConfiguration(
    String nodeId,
    boolean redisEnabled,
    String redisHost,
    int redisPort,
    String redisPassword,
    int redisDatabase,
    String redisChannel,
    int redisConnectTimeoutMs,
    boolean proxyEnabled,
    String proxyMessengerChannel,
    String proxyForwardTarget,
    boolean signatureEnabled,
    String signatureSecret,
    boolean signatureVerify,
    long dedupeTtlMs,
    int maxPayloadChars
) {

    public static final int DEFAULT_MAX_PAYLOAD_CHARS = 512 * 1024;

    public static CrossServerGlobalConfiguration from(FileConfiguration configuration) {
        var section = configuration.getConfigurationSection("cross-server");
        if (section == null) {
            return disabled();
        }
        String nodeId = nullToEmpty(section.getString("node-id", "default")).trim();
        if (nodeId.isBlank()) {
            nodeId = "default";
        }
        var redis = section.getConfigurationSection("redis");
        var proxy = section.getConfigurationSection("proxy");
        var signature = section.getConfigurationSection("signature");
        boolean sigEnabled = signature != null && signature.getBoolean("enabled", false);
        return new CrossServerGlobalConfiguration(
            nodeId,
            redis != null && redis.getBoolean("enabled", false),
            redis == null ? "127.0.0.1" : nullToEmpty(redis.getString("host", "127.0.0.1")),
            redis == null ? 6379 : Math.max(1, redis.getInt("port", 6379)),
            redis == null ? "" : nullToEmpty(redis.getString("password", "")),
            redis == null ? 0 : Math.max(0, redis.getInt("database", 0)),
            redis == null ? "AXS:CROSS" : nullToEmpty(redis.getString("channel", "AXS:CROSS")),
            redis == null ? 5000 : Math.max(1000, redis.getInt("connect-timeout-ms", 5000)),
            proxy != null && proxy.getBoolean("enabled", false),
            proxy == null ? "AXS_CROSS" : nullToEmpty(proxy.getString("messenger-channel", "AXS_CROSS")),
            proxy == null ? "ALL" : nullToEmpty(proxy.getString("forward-target", "ALL")),
            sigEnabled,
            signature == null ? "" : nullToEmpty(signature.getString("secret", "")),
            signature == null || signature.getBoolean("verify", sigEnabled),
            Math.max(1000L, section.getLong("dedupe-ttl-ms", 60_000L)),
            Math.max(1024, section.getInt("max-payload-chars", DEFAULT_MAX_PAYLOAD_CHARS))
        );
    }

    public static CrossServerGlobalConfiguration disabled() {
        return new CrossServerGlobalConfiguration(
            "default", false, "127.0.0.1", 6379, "", 0, "AXS:CROSS", 5000,
            false, "AXS_CROSS", "ALL", false, "", false, 60_000L, DEFAULT_MAX_PAYLOAD_CHARS
        );
    }

    public boolean shouldSign() {
        return signatureEnabled && signatureSecret != null && !signatureSecret.isBlank();
    }

    public boolean shouldVerify() {
        return signatureVerify && signatureSecret != null && !signatureSecret.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
