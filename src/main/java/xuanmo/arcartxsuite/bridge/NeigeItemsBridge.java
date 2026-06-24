package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeigeItemsBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private Object itemManager;
    private Method getItemIdMethod;
    private Method getItemStackMethod;

    public NeigeItemsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        itemManager = null;
        getItemIdMethod = null;
        getItemStackMethod = null;

        if (Bukkit.getPluginManager().getPlugin("NeigeItems") == null) {
            return;
        }

        try {
            Class<?> itemManagerClass = Class.forName("pers.neige.neigeitems.manager.ItemManager");
            Field instanceField = itemManagerClass.getField("INSTANCE");
            itemManager = instanceField.get(null);
            getItemIdMethod = itemManagerClass.getMethod("getItemId", ItemStack.class);
            getItemStackMethod = itemManagerClass.getMethod("getItemStack", String.class);
            available = true;
        } catch (Exception exception) {
            plugin.getLogger().warning("初始化 NeigeItems 物品桥接失败: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String neigeItemId(ItemStack itemStack) {
        if (!available || itemManager == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        try {
            Object result = getItemIdMethod.invoke(itemManager, itemStack);
            return result instanceof String value && !value.isBlank() ? value : "";
        } catch (Exception exception) {
            return "";
        }
    }

    public ItemStack getItemStack(String itemId, int amount) {
        if (!available || itemManager == null || getItemStackMethod == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Object raw = getItemStackMethod.invoke(itemManager, itemId);
            if (!(raw instanceof ItemStack itemStack) || itemStack.getType().isAir()) {
                return null;
            }
            ItemStack cloned = itemStack.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        } catch (Exception exception) {
            return null;
        }
    }
}
