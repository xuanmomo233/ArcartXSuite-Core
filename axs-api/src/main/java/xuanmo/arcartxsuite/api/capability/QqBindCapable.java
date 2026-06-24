package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * QQ 绑定查询与操作能力接口。
 * <p>
 * 由 QQBot 模块实现并注册，供 LoginView 等模块在玩家进服后
 * 查询绑定状态或在 UI 面板中完成绑定确认。
 */
public interface QqBindCapable {

    /**
     * 检查玩家是否已绑定 QQ。
     *
     * @param playerUuid 玩家 UUID
     * @return true 如果已绑定
     */
    boolean isBound(@NotNull UUID playerUuid);

    /**
     * 获取玩家绑定的 QQ 号。
     *
     * @param playerUuid 玩家 UUID
     * @return QQ 号，未绑定返回 null
     */
    @Nullable Long getBoundQqId(@NotNull UUID playerUuid);

    /**
     * 玩家在游戏内输入验证码确认绑定。
     *
     * @param player 玩家实例
     * @param code   6 位绑定验证码
     * @return 绑定结果
     */
    @NotNull BindResult confirmBind(@NotNull Player player, @NotNull String code);

    /**
     * 绑定结果。
     *
     * @param success true 表示绑定成功
     * @param qqId    绑定的 QQ 号（失败时为 null）
     * @param message 结果提示消息
     */
    record BindResult(boolean success, @Nullable Long qqId, @NotNull String message) {
    }
}
