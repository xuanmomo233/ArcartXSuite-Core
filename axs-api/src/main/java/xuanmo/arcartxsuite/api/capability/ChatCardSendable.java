package xuanmo.arcartxsuite.api.capability;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 聊天卡片发送能力接口。
 * <p>
 * 由宿主桥接或 Chat 模块实现，供 EventPacket 等模块跨模块调用。
 */
public interface ChatCardSendable {

    /**
     * 向指定玩家发送聊天卡片。
     *
     * @param player 目标玩家
     * @param cardId 卡片 id
     * @param data   附加数据
     * @return {@code true} 表示发送成功
     */
    boolean sendChatCard(@NotNull Player player, @NotNull String cardId, @NotNull Map<String, String> data);
}
