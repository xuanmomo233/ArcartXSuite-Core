package xuanmo.arcartxsuite.api.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 统一物品来源注册表。
 * <p>
 * 宿主维护单例实例，涵盖所有已对接的外部物品插件（MythicMobs、NeigeItems、Overture、MMOItems 等）。
 * 模块通过 {@code context.itemSourceRegistry()} 获取，不再各自创建桥接。
 */
@ApiStability.Stable
public interface ItemSourceRegistry {

    // ─── 物品识别 ─────────────────────────────────────────────────

    /** 获取 MythicMobs 物品 ID，非 Mythic 物品返回空串 */
    String mythicItemId(ItemStack itemStack);

    /** 获取 NeigeItems 物品 ID，非 Neige 物品返回空串 */
    String neigeItemId(ItemStack itemStack);

    /** 获取 Overture 物品 ID，非 Overture 物品返回空串 */
    String overtureItemId(ItemStack itemStack);

    /** 判断是否为 MythicMobs 物品 */
    boolean isMythicItem(ItemStack itemStack);

    /** 判断是否为 Overture 物品 */
    boolean isOvertureItem(ItemStack itemStack);

    // ─── 物品生成 ─────────────────────────────────────────────────

    /** 通过 MythicMobs ID 生成物品 */
    @Nullable ItemStack generateMythicItem(String itemId, int amount);

    /** 通过 NeigeItems ID 生成物品 */
    @Nullable ItemStack generateNeigeItem(String itemId, int amount);

    /** 通过 Overture ID 生成物品（需要玩家上下文） */
    @Nullable ItemStack generateOvertureItem(String itemId, @Nullable Player player, int amount);

    /** 通过 MMOItems 类型+ID 生成物品 */
    @Nullable ItemStack generateMmoItem(String typeId, String itemId, int amount);

    // ─── 可用性查询 ───────────────────────────────────────────────

    boolean mythicBridgeAvailable();

    boolean neigeBridgeAvailable();

    boolean overtureBridgeAvailable();

    boolean mmoBridgeAvailable();
}
