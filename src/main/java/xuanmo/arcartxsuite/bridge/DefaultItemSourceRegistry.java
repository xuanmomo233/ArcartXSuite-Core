package xuanmo.arcartxsuite.bridge;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;

/**
 * 宿主维护的全局物品来源注册表单例。
 * 持有所有外部物品插件桥接，统一初始化和生命周期。
 */
public final class DefaultItemSourceRegistry implements ItemSourceRegistry {

    private final MythicItemBridge mythicItemBridge;
    private final NeigeItemsBridge neigeItemsBridge;
    private final OvertureItemBridge overtureItemBridge;
    private final MmoItemsBridge mmoItemsBridge;

    public DefaultItemSourceRegistry(JavaPlugin plugin) {
        this.mythicItemBridge = new MythicItemBridge(plugin);
        this.neigeItemsBridge = new NeigeItemsBridge(plugin);
        this.overtureItemBridge = new OvertureItemBridge(plugin);
        this.mmoItemsBridge = new MmoItemsBridge(plugin);
    }

    public void initialize() {
        mythicItemBridge.initialize();
        neigeItemsBridge.initialize();
        overtureItemBridge.initialize();
        mmoItemsBridge.initialize();
    }

    public void shutdown() {
        // 当前桥接无需特殊清理，保留扩展点
    }

    // ─── 物品识别 ─────────────────────────────────────────────────

    @Override
    public String mythicItemId(ItemStack itemStack) {
        return mythicItemBridge.mythicItemId(itemStack);
    }

    @Override
    public String neigeItemId(ItemStack itemStack) {
        return neigeItemsBridge.neigeItemId(itemStack);
    }

    @Override
    public String overtureItemId(ItemStack itemStack) {
        return overtureItemBridge.overtureItemId(itemStack);
    }

    @Override
    public boolean isMythicItem(ItemStack itemStack) {
        return mythicItemBridge.isMythicItem(itemStack);
    }

    @Override
    public boolean isOvertureItem(ItemStack itemStack) {
        return overtureItemBridge.isOvertureItem(itemStack);
    }

    // ─── 物品生成 ─────────────────────────────────────────────────

    @Override
    public ItemStack generateMythicItem(String itemId, int amount) {
        return mythicItemBridge.getItemStack(itemId, amount);
    }

    @Override
    public ItemStack generateNeigeItem(String itemId, int amount) {
        return neigeItemsBridge.getItemStack(itemId, amount);
    }

    @Override
    public ItemStack generateOvertureItem(String itemId, Player player, int amount) {
        return overtureItemBridge.generateItem(itemId, player, amount);
    }

    @Override
    public ItemStack generateMmoItem(String typeId, String itemId, int amount) {
        return mmoItemsBridge.getItemStack(typeId, itemId, amount);
    }

    // ─── 可用性查询 ───────────────────────────────────────────────

    @Override
    public boolean mythicBridgeAvailable() {
        return mythicItemBridge.isAvailable();
    }

    @Override
    public boolean neigeBridgeAvailable() {
        return neigeItemsBridge.isAvailable();
    }

    @Override
    public boolean overtureBridgeAvailable() {
        return overtureItemBridge.isAvailable();
    }

    @Override
    public boolean mmoBridgeAvailable() {
        return mmoItemsBridge.isAvailable();
    }
}
