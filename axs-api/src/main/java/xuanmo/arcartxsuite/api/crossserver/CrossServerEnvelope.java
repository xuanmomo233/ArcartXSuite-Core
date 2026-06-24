package xuanmo.arcartxsuite.api.crossserver;

/**
 * 跨服统一 wire 信封（JSON 序列化）。
 */
public record CrossServerEnvelope(
    int protocol,
    String module,
    String nodeId,
    String messageId,
    long timestamp,
    String payload,
    String signature
) {
    public static final int PROTOCOL_VERSION = 1;
}
