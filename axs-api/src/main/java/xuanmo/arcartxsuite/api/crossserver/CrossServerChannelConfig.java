package xuanmo.arcartxsuite.api.crossserver;

/**
 * 模块级跨服通道配置；连接参数与签名密钥由宿主 {@code config.yml} 统一管理。
 *
 * @param enabled      模块是否启用跨服
 * @param redisEnabled 是否走 Redis；{@code null} 表示继承宿主全局开关
 * @param proxyEnabled 是否走 Proxy Forward；{@code null} 表示继承宿主全局开关
 */
public record CrossServerChannelConfig(
    boolean enabled,
    Boolean redisEnabled,
    Boolean proxyEnabled
) {

    public static CrossServerChannelConfig enabledDefault() {
        return new CrossServerChannelConfig(true, null, null);
    }

    public static CrossServerChannelConfig disabled() {
        return new CrossServerChannelConfig(false, null, null);
    }

    public boolean redisEnabled(boolean globalRedisEnabled) {
        return enabled && (redisEnabled != null ? redisEnabled : globalRedisEnabled);
    }

    public boolean proxyEnabled(boolean globalProxyEnabled) {
        return enabled && (proxyEnabled != null ? proxyEnabled : globalProxyEnabled);
    }
}
