package xuanmo.arcartxsuite.api.config;

/**
 * 配置 issue 的严重程度。
 * <ul>
 *     <li>{@link #INFO}：可应用的安全修复（如补默认键）。</li>
 *     <li>{@link #WARN}：建议应用，但非阻塞（如类型转换可恢复）。</li>
 *     <li>{@link #ERROR}：危险或转换失败，apply 默认拒绝，需 {@code --force}。</li>
 * </ul>
 */
public enum ConfigIssueSeverity {
    INFO,
    WARN,
    ERROR
}
