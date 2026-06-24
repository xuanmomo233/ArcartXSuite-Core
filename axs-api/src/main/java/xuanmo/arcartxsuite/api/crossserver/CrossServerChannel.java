package xuanmo.arcartxsuite.api.crossserver;

/**
 * 模块跨服发布通道句柄。
 */
public interface CrossServerChannel extends AutoCloseable {

    /** 模块 id。 */
    String moduleId();

    /** 至少一种后端（Redis / Proxy）已成功启动。 */
    boolean isActive();

    /**
     * 发布跨服消息；宿主负责封装 {@link CrossServerEnvelope}、签名与双后端发送。
     *
     * @param payload 模块自定义 payload（JSON / YAML 等）
     */
    void publish(String payload);

    @Override
    void close();
}
