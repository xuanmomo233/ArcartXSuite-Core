package xuanmo.arcartxsuite.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;

/**
 * {@link PlaceholderExpansionRegistry} 的强引用实现。
 * <p>
 * 依赖 PlaceholderAPI 直接调用 {@link PlaceholderExpansion#register()} / {@link PlaceholderExpansion#unregister()}，
 * 避免反射带来的性能和类型安全问题。
 * <p>
 * 仅在服务器已安装 PlaceholderAPI 时使用；否则回退到 {@link DefaultPlaceholderExpansionRegistry}。
 */
public final class DirectPlaceholderExpansionRegistry implements PlaceholderExpansionRegistry {

    private final Logger logger;
    private final List<PlaceholderExpansion> registeredExpansions = new ArrayList<>();

    public DirectPlaceholderExpansionRegistry(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean register(@NotNull Object expansion) {
        if (!(expansion instanceof PlaceholderExpansion placeholderExpansion)) {
            logger.warning("DirectPlaceholderExpansionRegistry 只接受 PlaceholderExpansion 实例，" +
                "实际类型: " + expansion.getClass().getName());
            return false;
        }
        try {
            boolean success = placeholderExpansion.register();
            if (success) {
                registeredExpansions.add(placeholderExpansion);
                return true;
            }
            logger.warning("PlaceholderAPI 扩展注册失败，register() 返回 false: " + placeholderExpansion.getIdentifier());
            return false;
        } catch (LinkageError | RuntimeException exception) {
            logger.warning("PlaceholderAPI 扩展注册失败 [" + placeholderExpansion.getIdentifier() + "]: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void unregisterAll() {
        for (PlaceholderExpansion expansion : List.copyOf(registeredExpansions)) {
            try {
                expansion.unregister();
            } catch (LinkageError | RuntimeException ignored) {
            }
        }
        registeredExpansions.clear();
    }
}
