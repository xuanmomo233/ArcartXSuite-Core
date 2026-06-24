package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 字幕播放能力接口。
 * <p>
 * 由 Announcer/Subtitle 模块实现，供 EventPacket 等模块跨模块调用。
 */
public interface SubtitlePlayable {

    /**
     * 为指定玩家播放字幕组。
     *
     * @param player  目标玩家
     * @param groupId 字幕组 id
     * @return {@code true} 表示播放成功
     */
    boolean playGroup(@NotNull Player player, @NotNull String groupId);
}
