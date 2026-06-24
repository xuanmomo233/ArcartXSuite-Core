package xuanmo.arcartxsuite.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 全局按键事件处理器。
 * <p>
 * 模块通过 {@link ModuleContext#registerKeybindHandler(String, int, KeybindHandler)} 注册，
 * 宿主按优先级分发按键事件。数值越小越优先；返回 {@code true} 表示已消费，后续处理器不再收到。
 */
@FunctionalInterface
public interface KeybindHandler {

    /**
     * 处理按键事件。
     *
     * @param player  按下按键的玩家
     * @param keyName 按键注册名（如 {@code AXS_INTERACT}）
     * @return {@code true} 表示已消费此事件，后续低优先级处理器不再收到
     */
    boolean handleKeyPress(@NotNull Player player, @NotNull String keyName);
}
