package xuanmo.arcartxsuite.api.config;

import java.util.Objects;

/**
 * 单条配置 issue。
 *
 * @param ownerId         所属 spec 的 ownerId
 * @param resourcePath    资源路径（默认 yml 在 jar 内的位置）
 * @param configPath      YAML 路径（如 {@code "ui.attribute-line-color"}）；结构层 issue 可填 {@code ""}
 * @param kind            issue 种类
 * @param severity        严重程度
 * @param currentValue    现值（可空）
 * @param suggestedValue  建议值（可空）
 * @param message         可读说明
 * @param source          变更来源（可选，用于标识该字段的来源）
 * @param defaultValue    Jar 默认值（可选，用于对比用户修改）
 */
public record ConfigIssue(
    String ownerId,
    String resourcePath,
    String configPath,
    ConfigIssueKind kind,
    ConfigIssueSeverity severity,
    Object currentValue,
    Object suggestedValue,
    String message,
    ChangeSource source,
    Object defaultValue
) {

    public ConfigIssue {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
        configPath = configPath == null ? "" : configPath;
        kind = Objects.requireNonNull(kind, "kind");
        severity = Objects.requireNonNull(severity, "severity");
        message = message == null ? "" : message;
        source = source == null ? ChangeSource.UNKNOWN : source;
    }

    /**
     * 向后兼容的构造方法（不带 source 和 defaultValue）。
     */
    public ConfigIssue(
        String ownerId,
        String resourcePath,
        String configPath,
        ConfigIssueKind kind,
        ConfigIssueSeverity severity,
        Object currentValue,
        Object suggestedValue,
        String message
    ) {
        this(ownerId, resourcePath, configPath, kind, severity,
             currentValue, suggestedValue, message, ChangeSource.UNKNOWN, null);
    }

    /**
     * 带来源标记的构造方法。
     */
    public ConfigIssue(
        String ownerId,
        String resourcePath,
        String configPath,
        ConfigIssueKind kind,
        ConfigIssueSeverity severity,
        Object currentValue,
        Object suggestedValue,
        String message,
        ChangeSource source
    ) {
        this(ownerId, resourcePath, configPath, kind, severity,
             currentValue, suggestedValue, message, source, null);
    }

    /**
     * 获取带图标和来源的问题描述。
     */
    public String detailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(source.icon()).append(" ");
        sb.append("[").append(source.description()).append("] ");
        sb.append(message);
        if (defaultValue != null && !defaultValue.equals(currentValue)) {
            sb.append(" (默认值: ").append(defaultValue).append(")");
        }
        return sb.toString();
    }
}
