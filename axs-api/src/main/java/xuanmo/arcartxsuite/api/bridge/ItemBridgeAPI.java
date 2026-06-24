package xuanmo.arcartxsuite.api.bridge;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * ArcartX ItemStack 桥接的公开 API 子集。
 * <p>
 * 提供 ItemStack → JSON 序列化能力（用于客户端 UI 物品展示）。
 *
 * @since 1.1.0
 */
@ApiStability.Stable
public interface ItemBridgeAPI {

    /** 桥接是否可用 */
    boolean isAvailable();

    /** 初始化桥接（由宿主调用） */
    boolean initialize();

    /** 关闭桥接（由宿主调用） */
    void shutdown();

    /**
     * 将 ItemStack 序列化为 ArcartX 客户端可识别的 JSON 字符串。
     *
     * @param itemStack Bukkit 物品栈
     * @return JSON 字符串，不可用时返回 empty
     */
    @NotNull Optional<String> itemToJson(@NotNull ItemStack itemStack);
}
