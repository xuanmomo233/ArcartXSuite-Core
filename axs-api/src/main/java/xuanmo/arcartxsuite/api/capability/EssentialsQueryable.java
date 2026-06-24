package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Essentials 模块状态查询能力接口。
 * <p>
 * 由 Essentials 模块实现，供 Tab、Chat 等模块查询玩家 AFK/Vanish/Mute/Nick 状态。
 */
public interface EssentialsQueryable {

    /**
     * 查询玩家是否处于 AFK 状态。
     */
    boolean isAfk(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否处于隐身状态。
     */
    boolean isVanished(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否被禁言（Essentials 侧）。
     */
    boolean isMuted(@NotNull UUID playerUuid);

    /**
     * 获取玩家自定义昵称，未设置时返回 null。
     */
    @Nullable
    String getNickname(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否处于飞行模式。
     */
    boolean isFlying(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否处于无敌模式。
     */
    boolean isGodMode(@NotNull UUID playerUuid);
}
