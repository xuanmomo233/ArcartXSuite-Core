package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 称号授予能力接口。
 * <p>
 * 由 Title 模块实现，供 EventPacket 等模块跨模块调用。
 */
public interface TitleGrantable {

    /**
     * 给予玩家称号。
     *
     * @param playerId 玩家 UUID
     * @param titleId  称号 id
     * @param duration 持续时间描述（如 "permanent", "7d"）
     * @param source   来源标识（如 "EventPacket"）
     * @return {@code true} 表示授予成功
     */
    boolean giveTitle(@NotNull UUID playerId, @NotNull String titleId, @NotNull String duration, @NotNull String source);
}
