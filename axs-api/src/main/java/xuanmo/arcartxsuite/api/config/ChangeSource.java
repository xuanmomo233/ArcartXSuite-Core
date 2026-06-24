package xuanmo.arcartxsuite.api.config;

/**
 * 配置变更来源标记。
 * <p>
 * 用于诊断报告中标识字段值的来源和变更类型，
 * 帮助管理员理解"为什么这个字段被标记为问题"。
 *
 * @author 墨墨啊
 * @since 1.0.2-beta
 */
public enum ChangeSource {

    /**
     * Jar 内默认配置提供的值（用户未修改）。
     * <p>
     * 表示该字段与模块 Jar 中的默认值完全一致，
     * 通常是新安装或未自定义的配置。
     */
    JAR_DEFAULT,

    /**
     * 本次 Jar 更新新增的配置字段。
     * <p>
     * 表示该字段在上次诊断时不存在，是本次更新新增的，
     * 尚未被用户配置。
     */
    JAR_NEW,

    /**
     * 用户手动修改过的值（与 Jar 默认值不同）。
     * <p>
     * 表示该字段被用户自定义过，可能包含：
     * <ul>
     *   <li>数值调整（如 pool-size 从 10 改为 20）</li>
     *   <li>功能开关（如 enable-feature 从 false 改为 true）</li>
     *   <li>自定义内容（如消息文本、颜色代码）</li>
     * </ul>
     */
    USER_MODIFIED,

    /**
     * 用户保留的已废弃字段。
     * <p>
     * 表示该字段在 Jar 默认配置中已被标记为 obsolete，
     * 但用户配置文件中仍然存在。
     */
    USER_DEPRECATED,

    /**
     * 用户自定义的动态节内容。
     * <p>
     * 表示该字段位于 SyncPolicy.dynamicSection() 声明的节内，
     * 是用户自由添加的自定义内容（如仓库列表、称号定义）。
     */
    USER_DYNAMIC,

    /**
     * 来源未知或无法确定。
     * <p>
     * 通常发生在首次诊断或无历史快照时。
     */
    UNKNOWN;

    /**
     * 获取来源的可读描述。
     */
    public String description() {
        return switch (this) {
            case JAR_DEFAULT -> "模块默认";
            case JAR_NEW -> "本次更新新增";
            case USER_MODIFIED -> "用户自定义";
            case USER_DEPRECATED -> "用户保留（已废弃）";
            case USER_DYNAMIC -> "用户动态添加";
            case UNKNOWN -> "来源未知";
        };
    }

    /**
     * 获取来源的图标标记。
     */
    public String icon() {
        return switch (this) {
            case JAR_DEFAULT -> "📦";
            case JAR_NEW -> "✨";
            case USER_MODIFIED -> "✏️";
            case USER_DEPRECATED -> "🗑️";
            case USER_DYNAMIC -> "🔧";
            case UNKNOWN -> "❓";
        };
    }

    /**
     * 是否需要用户关注。
     */
    public boolean needsAttention() {
        return this == USER_DEPRECATED || this == JAR_NEW;
    }
}
