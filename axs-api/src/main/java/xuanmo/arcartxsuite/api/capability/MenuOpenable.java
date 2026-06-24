package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 通用菜单打开能力，由 Menu 模块实现。
 */
public interface MenuOpenable {

    /**
     * 为玩家打开指定菜单。
     *
     * @param player 目标玩家
     * @param menuId 菜单定义 ID
     * @return 是否成功打开
     */
    boolean openMenu(@NotNull Player player, @NotNull String menuId);

    /**
     * 刷新玩家当前打开的菜单内容。
     */
    void refreshMenu(@NotNull Player player);
}
