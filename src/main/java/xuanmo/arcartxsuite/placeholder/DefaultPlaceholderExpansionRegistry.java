package xuanmo.arcartxsuite.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;

/**
 * {@link PlaceholderExpansionRegistry} 的模块级实现。
 * <p>
 * 每个模块的上下文持有独立实例，通过反射调用 PlaceholderExpansion 的
 * {@code register()} / {@code unregister()} 方法，避免 axs-api 直接依赖 PlaceholderAPI。
 */
public final class DefaultPlaceholderExpansionRegistry implements PlaceholderExpansionRegistry {

    private final Logger logger;
    private final List<Object> registeredExpansions = new ArrayList<>();

    public DefaultPlaceholderExpansionRegistry(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean register(@NotNull Object expansion) {
        try {
            Object result = expansion.getClass().getMethod("register").invoke(expansion);
            if (result instanceof Boolean registered && registered) {
                registeredExpansions.add(expansion);
                return true;
            }
            logger.warning("PlaceholderAPI 扩展注册失败，register() 返回 false");
            return false;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logger.warning("PlaceholderAPI 扩展注册失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void unregisterAll() {
        for (Object expansion : List.copyOf(registeredExpansions)) {
            try {
                expansion.getClass().getMethod("unregister").invoke(expansion);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        registeredExpansions.clear();
    }
}
