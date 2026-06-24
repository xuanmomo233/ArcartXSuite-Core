package xuanmo.arcartxsuite.api;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端自定义包处理器。
 * <p>
 * 模块实现此接口后通过 {@link ModuleContext#registerClientPacketHandler} 注册，
 * 宿主按优先级顺序遍历注册表分发客户端回包。
 */
@FunctionalInterface
public interface ClientPacketHandler {

    /**
     * 尝试处理客户端发来的自定义数据包。
     *
     * @param player   发包玩家
     * @param packetId 数据包 id
     * @param data     数据负载
     * @return {@code true} 表示已消费此包，后续处理器不再接收
     */
    boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data);
}
