package xuanmo.arcartxsuite.api.condition;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ScriptConditionEvaluator {

    boolean passes(@Nullable Player player, @NotNull List<ScriptCondition> conditions);

    @Nullable
    ScriptCondition firstFailed(@Nullable Player player, @NotNull List<ScriptCondition> conditions);

    @NotNull
    String applyPlaceholders(@Nullable Player player, @NotNull String input);

    default boolean hasPermission(@Nullable Player player, @Nullable String permission) {
        return permission == null || permission.isBlank() || (player != null && player.hasPermission(permission));
    }

    static ScriptConditionEvaluator noop() {
        return NoopScriptConditionEvaluator.INSTANCE;
    }
}
