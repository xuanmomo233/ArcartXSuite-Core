package xuanmo.arcartxsuite.api.condition;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NoopScriptConditionEvaluator implements ScriptConditionEvaluator {

    static final NoopScriptConditionEvaluator INSTANCE = new NoopScriptConditionEvaluator();

    private NoopScriptConditionEvaluator() {
    }

    @Override
    public boolean passes(@Nullable Player player, @NotNull List<ScriptCondition> conditions) {
        return firstFailed(player, conditions) == null;
    }

    @Override
    public @Nullable ScriptCondition firstFailed(@Nullable Player player, @NotNull List<ScriptCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        if (player == null) {
            return conditions.get(0);
        }
        for (ScriptCondition condition : conditions) {
            if (condition.kind() == ScriptConditionKind.ARIA) {
                return condition;
            }
            String actual = condition.placeholder() == null ? "" : condition.placeholder();
            ScriptConditionOperator operator = condition.operator() == null
                ? ScriptConditionOperator.EQ
                : condition.operator();
            if (!operator.evaluate(actual, condition.value())) {
                return condition;
            }
        }
        return null;
    }

    @Override
    public @NotNull String applyPlaceholders(@Nullable Player player, @NotNull String input) {
        if (player == null) {
            return input;
        }
        return input.replace("{player}", player.getName());
    }
}
