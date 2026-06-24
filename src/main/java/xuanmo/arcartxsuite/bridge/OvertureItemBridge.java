package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class OvertureItemBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private Object overtureApiInstance;
    private Method isOvertureItemMethod;
    private Method getOvertureIdMethod;
    private Method generateItemMethod;
    private Method getItemIdsMethod;

    public OvertureItemBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        overtureApiInstance = null;
        isOvertureItemMethod = null;
        getOvertureIdMethod = null;
        generateItemMethod = null;
        getItemIdsMethod = null;

        Plugin overture = Bukkit.getPluginManager().getPlugin("Overture");
        if (overture == null) {
            return;
        }

        try {
            ClassLoader classLoader = overture.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("priv.seventeen.artist.overture.api.OvertureAPI", true, classLoader);
            overtureApiInstance = apiClass.getField("INSTANCE").get(null);
            isOvertureItemMethod = apiClass.getMethod("isOvertureItem", ItemStack.class);
            getOvertureIdMethod = apiClass.getMethod("getOvertureId", ItemStack.class);
            generateItemMethod = apiClass.getMethod("generateItem", String.class, Player.class);
            getItemIdsMethod = apiClass.getMethod("getItemIds");
            available = true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("初始化 Overture 物品桥接失败: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String overtureItemId(ItemStack itemStack) {
        if (!available || overtureApiInstance == null || itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        try {
            Object result = getOvertureIdMethod.invoke(overtureApiInstance, itemStack);
            return result instanceof String value && !value.isBlank() ? value : "";
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    public boolean isOvertureItem(ItemStack itemStack) {
        if (!available || overtureApiInstance == null || itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            Object result = isOvertureItemMethod.invoke(overtureApiInstance, itemStack);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public ItemStack generateItem(String itemId, Player player, int amount) {
        if (!available || overtureApiInstance == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Object raw = generateItemMethod.invoke(overtureApiInstance, itemId, player);
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

    @SuppressWarnings("unchecked")
    public java.util.List<String> getItemIds() {
        if (!available || overtureApiInstance == null || getItemIdsMethod == null) {
            return java.util.List.of();
        }
        try {
            Object result = getItemIdsMethod.invoke(overtureApiInstance);
            if (result instanceof java.util.List<?> list) {
                return (java.util.List<String>) list;
            }
            return java.util.List.of();
        } catch (ReflectiveOperationException exception) {
            return java.util.List.of();
        }
    }
}
