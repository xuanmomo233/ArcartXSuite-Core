package xuanmo.arcartxsuite.api.config;

/**
 * 配置 issue 的种类（来源）。
 */
public enum ConfigIssueKind {
    /** 玩家 yml 缺失内置默认值的键。 */
    MISSING_DEFAULT,
    /** 玩家 yml 中存在已废弃且不在默认值与策略豁免内的键。 */
    OBSOLETE_KEY,
    /** 字段类型与预期不符。 */
    TYPE_MISMATCH,
    /** 数字超出 {@code [min, max]} 范围。 */
    VALUE_OUT_OF_RANGE,
    /** 字符串不在 {@code allowedValues} 集合中。 */
    VALUE_NOT_IN_ENUM,
    /** 检测到版本号低于当前内置版本，存在待应用的 migration。 */
    VERSION_UPGRADE,
    /** 迁移操作执行失败。 */
    MIGRATION_FAILED
}
