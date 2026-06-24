package xuanmo.arcartxsuite.api.crossserver;

/**
 * 经宿主跨服层校验、去重后投递给模块的消息。
 *
 * @param moduleId  模块 id（如 {@code tab}、{@code chat}）
 * @param nodeId    来源子服节点 id
 * @param messageId 信封 message-id（UUID）
 * @param payload   模块自定义 payload 字符串
 */
public record CrossServerDelivery(
    String moduleId,
    String nodeId,
    String messageId,
    String payload
) {
}
