package xuanmo.arcartxsuite.api.script;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AriaBridge {

    boolean available();

    @Nullable
    String version();

    @Nullable
    Object eval(@NotNull String code, @NotNull Map<String, Object> bindings);

    default boolean evalBoolean(@NotNull String code, @NotNull Map<String, Object> bindings) {
        return toBoolean(eval(code, bindings));
    }

    static boolean toBoolean(@Nullable Object result) {
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean bool) {
            return bool;
        }
        if (result instanceof Number number) {
            return number.doubleValue() != 0.0D;
        }
        if (result instanceof String text) {
            if (text.isBlank()) {
                return false;
            }
            return !"false".equalsIgnoreCase(text) && !"0".equals(text);
        }
        return true;
    }
}
