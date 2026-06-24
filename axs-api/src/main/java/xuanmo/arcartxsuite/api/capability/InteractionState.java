package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 交互状态查询能力接口。
 * <p>
 * 由拥有交互式 HUD/Menu 的模块实现（如 Conversation），
 * 供其他模块（如 Pickup）在处理共享交互键（默认 F）时判断是否应让步。
 * <p>
 * 当 {@link #isPlayerInteracting(Player)} 返回 {@code true} 时，
 * 表示该玩家正在与当前模块交互，其他模块不应抢占按键。
 */
public interface InteractionState {

    /**
     * 查询指定玩家是否正在与本模块进行活跃交互。
     * <p>
     * "活跃交互"包括但不限于：正在对话、NPC 选择器已打开、正在浏览菜单等。
     *
     * @param player 目标玩家
     * @return {@code true} 表示玩家正在交互，其他模块应让步
     */
    boolean isPlayerInteracting(@NotNull Player player);
}
