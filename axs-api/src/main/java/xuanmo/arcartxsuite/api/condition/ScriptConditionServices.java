package xuanmo.arcartxsuite.api.condition;

/**
 * 全局条件评估服务定位器。
 * <p>
 * 宿主在初始化时通过 {@link #install(ScriptConditionEvaluator)} 注入实现，
 * 模块通过 {@link #evaluator()} 获取当前已注册的条件评估器。
 */
public final class ScriptConditionServices {

    private static volatile ScriptConditionEvaluator evaluator = ScriptConditionEvaluator.noop();

    private ScriptConditionServices() {
    }

    public static ScriptConditionEvaluator evaluator() {
        return evaluator;
    }

    public static void install(ScriptConditionEvaluator installed) {
        evaluator = installed == null ? ScriptConditionEvaluator.noop() : installed;
    }

    public static void reset() {
        evaluator = ScriptConditionEvaluator.noop();
    }
}
