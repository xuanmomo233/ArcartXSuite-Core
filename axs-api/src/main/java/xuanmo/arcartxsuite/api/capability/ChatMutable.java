package xuanmo.arcartxsuite.api.capability;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 聊天禁言能力接口。
 * <p>
 * 由 Chat 模块实现并注册，供 Essentials 等模块通过 capability 调用以执行禁言/解禁操作。
 * 当 Chat 模块未启用时，调用方应做降级处理。
 */
public interface ChatMutable {

    /**
     * 禁言指定玩家。
     *
     * @param playerName 目标玩家名称
     * @param expiresAt  过期时间，null 表示永久禁言
     * @param reason     禁言原因，可为 null
     * @param mutedBy    执行者名称，可为 null
     * @return 操作结果消息（成功或失败原因）
     */
    @NotNull
    String mutePlayer(@NotNull String playerName, @Nullable Instant expiresAt, @Nullable String reason, @Nullable String mutedBy);

    /**
     * 解除指定玩家的禁言。
     *
     * @param playerName 目标玩家名称
     * @return 操作结果消息（成功或失败原因）
     */
    @NotNull
    String unmutePlayer(@NotNull String playerName);

    /**
     * 查询玩家是否当前处于禁言状态。
     *
     * @param playerUuid 目标玩家 UUID
     * @return 是否被禁言
     */
    boolean isMuted(@NotNull UUID playerUuid);
}
