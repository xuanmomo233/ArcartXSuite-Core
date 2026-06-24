package xuanmo.arcartxsuite.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端初始化完成回调。
 * <p>
 * 模块实现此接口后通过 {@link ModuleContext#registerClientInitializedHandler} 注册，
 * 当 ArcartX 客户端初始化完成时宿主会通知所有已注册的处理器。
 */
@FunctionalInterface
public interface ClientInitializedHandler {

    /**
     * 当指定玩家的 ArcartX 客户端初始化完成时调用。
     *
     * @param player 已初始化的玩家
     */
    void onClientInitialized(@NotNull Player player);
}
