package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MythicItemBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private Object itemManager;
    private Method getMythicTypeFromItemMethod;
    private Method isMythicItemMethod;
    private Method getItemStackMethod;
    private Method getItemStackWithAmountMethod;

    public MythicItemBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        itemManager = null;
        getMythicTypeFromItemMethod = null;
        isMythicItemMethod = null;
        getItemStackMethod = null;
        getItemStackWithAmountMethod = null;

        Plugin mythic = plugin.getServer().getPluginManager().getPlugin("MythicBukkit");
        if (mythic == null) {
            mythic = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        }
        if (mythic == null) {
            return;
        }

        try {
            ClassLoader classLoader = mythic.getClass().getClassLoader();
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, classLoader);
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            itemManager = mythicBukkitClass.getMethod("getItemManager").invoke(mythicBukkit);
            getMythicTypeFromItemMethod = itemManager.getClass().getMethod("getMythicTypeFromItem", ItemStack.class);
            isMythicItemMethod = itemManager.getClass().getMethod("isMythicItem", ItemStack.class);
            getItemStackMethod = itemManager.getClass().getMethod("getItemStack", String.class);
            getItemStackWithAmountMethod = itemManager.getClass().getMethod("getItemStack", String.class, int.class);
            available = true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("初始化 MythicMobs 物品桥接失败: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String mythicItemId(ItemStack itemStack) {
        if (!available || itemManager == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        try {
            Object raw = getMythicTypeFromItemMethod.invoke(itemManager, itemStack);
            return raw instanceof String value && !value.isBlank() ? value : "";
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    public boolean isMythicItem(ItemStack itemStack) {
        if (!available || itemManager == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            Object raw = isMythicItemMethod.invoke(itemManager, itemStack);
            return raw instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public ItemStack getItemStack(String itemId, int amount) {
        if (!available || itemManager == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Object raw;
            if (getItemStackWithAmountMethod != null) {
                raw = getItemStackWithAmountMethod.invoke(itemManager, itemId, Math.max(1, amount));
            } else if (getItemStackMethod != null) {
                raw = getItemStackMethod.invoke(itemManager, itemId);
            } else {
                return null;
            }
            if (!(raw instanceof ItemStack itemStack) || itemStack.getType().isAir()) {
                return null;
            }
            ItemStack cloned = itemStack.clone();
            cloned.setAmount(Math.max(1, amount));
            return cloned;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
