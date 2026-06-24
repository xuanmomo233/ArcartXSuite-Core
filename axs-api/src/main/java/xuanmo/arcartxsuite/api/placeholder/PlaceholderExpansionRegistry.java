package xuanmo.arcartxsuite.api.placeholder;

import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI 扩展注册表（模块级）。
 * <p>
 * 每个模块的 {@link xuanmo.arcartxsuite.api.ModuleContext} 持有一个独立实例，
 * 模块通过它注册/注销自己的 {@code PlaceholderExpansion}。
 * <p>
 * 注册参数使用 {@link Object} 而非 {@code PlaceholderExpansion}，避免 {@code axs-api}
 * 直接依赖 PlaceholderAPI 的类。
 */
public interface PlaceholderExpansionRegistry {

    /**
     * 注册一个 PlaceholderAPI 扩展。
     * <p>
     * 模块 onDisable 时会自动调用 {@link #unregisterAll()}。
     *
     * @param expansion PlaceholderExpansion 实例
     * @return {@code true} 表示注册成功
     */
    boolean register(@NotNull Object expansion);

    /**
     * 注销当前模块通过本注册表注册的所有扩展。
     */
    void unregisterAll();
}
