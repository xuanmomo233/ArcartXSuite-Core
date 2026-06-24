package xuanmo.arcartxsuite.api.item;

import org.bukkit.inventory.ItemStack;

/**
 * 物品匹配 API。
 * <p>
 * 根据 {@link ItemMatcher} 的规则判断 {@link ItemStack} 是否匹配。
 */
public interface ItemMatcherAPI {

    /**
     * 判断物品是否匹配给定的匹配规则。
     *
     * @param matcher   匹配规则
     * @param itemStack 待检测物品
     * @return true 如果匹配
     */
    boolean matches(ItemMatcher matcher, ItemStack itemStack);
}
