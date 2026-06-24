package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 仓库自动入库能力接口。
 * <p>
 * 由 Warehouse 模块实现，供 Pickup 等模块在不打开 UI 的情况下
 * 尝试将玩家手中的物品直接存入个人仓库。
 */
public interface WarehouseAutoDepositable {

    /**
     * 尝试将物品存入玩家的个人仓库。
     *
     * @param player 目标玩家
     * @param itemStack 待存入物品
     * @return 存入结果
     */
    @NotNull DepositResult depositToPersonalWarehouse(@NotNull Player player, @NotNull ItemStack itemStack);

    /**
     * 自动入库结果。
     *
     * @param success         本次调用是否成功执行到仓库存储逻辑
     * @param storedAmount    实际成功存入的数量
     * @param remainingAmount 未能存入、应继续由调用方处理的数量
     * @param message         附加说明
     */
    record DepositResult(boolean success, long storedAmount, int remainingAmount, @NotNull String message) {
    }
}
