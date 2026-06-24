package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmoItemsBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private Object mmoItemsPlugin;
    private Object typeRegistry;
    private Object itemRegistry;
    private Method typeRegistryGetMethod;
    private Method itemRegistryGetItemMethod;
    private Method itemRegistryGetMmoItemMethod;
    private Method mmoItemNewBuilderMethod;
    private Method builderBuildMethod;

    public MmoItemsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        available = false;
        mmoItemsPlugin = null;
        typeRegistry = null;
        itemRegistry = null;
        typeRegistryGetMethod = null;
        itemRegistryGetItemMethod = null;
        itemRegistryGetMmoItemMethod = null;
        mmoItemNewBuilderMethod = null;
        builderBuildMethod = null;

        Plugin mmoItems = plugin.getServer().getPluginManager().getPlugin("MMOItems");
        if (mmoItems == null) {
            return;
        }

        try {
            ClassLoader classLoader = mmoItems.getClass().getClassLoader();
            Class<?> pluginClass = Class.forName("net.Indyuce.mmoitems.MMOItems", true, classLoader);
            Field pluginField = pluginClass.getField("plugin");
            mmoItemsPlugin = pluginField.get(null);
            typeRegistry = pluginClass.getMethod("getTypes").invoke(mmoItemsPlugin);
            itemRegistry = invokeNoArgsIfPresent(pluginClass, mmoItemsPlugin, "getItems");
            if (itemRegistry == null) {
                itemRegistry = invokeNoArgsIfPresent(pluginClass, mmoItemsPlugin, "getItemManager");
            }
            if (typeRegistry == null || itemRegistry == null) {
                return;
            }
            typeRegistryGetMethod = typeRegistry.getClass().getMethod("get", String.class);
            itemRegistryGetItemMethod = findMethod(itemRegistry.getClass(), "getItem", 2);
            itemRegistryGetMmoItemMethod = findMethod(itemRegistry.getClass(), "getMMOItem", 3);
            available = typeRegistryGetMethod != null && (itemRegistryGetItemMethod != null || itemRegistryGetMmoItemMethod != null);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("初始化 MMOItems 物品桥接失败: " + exception.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public ItemStack getItemStack(String typeId, String itemId, int amount) {
        if (!available || typeRegistry == null || itemRegistry == null || blank(typeId) || blank(itemId)) {
            return null;
        }
        try {
            Object type = typeRegistryGetMethod.invoke(typeRegistry, normalize(typeId));
            if (type == null) {
                type = typeRegistryGetMethod.invoke(typeRegistry, typeId.trim());
            }
            if (type == null) {
                return null;
            }
            ItemStack direct = resolveDirectItem(type, itemId);
            if (direct != null && !direct.getType().isAir()) {
                ItemStack cloned = direct.clone();
                cloned.setAmount(Math.max(1, amount));
                return cloned;
            }
        } catch (ReflectiveOperationException exception) {
            return null;
        }
        return null;
    }

    private ItemStack resolveDirectItem(Object type, String itemId) throws ReflectiveOperationException {
        if (itemRegistryGetItemMethod != null) {
            Object raw = itemRegistryGetItemMethod.invoke(itemRegistry, type, itemId);
            if (raw instanceof ItemStack itemStack) {
                return itemStack;
            }
            ItemStack built = tryBuildFromMmoItem(raw);
            if (built != null) {
                return built;
            }
        }
        if (itemRegistryGetMmoItemMethod != null) {
            Object raw = itemRegistryGetMmoItemMethod.invoke(itemRegistry, type, itemId, null);
            return tryBuildFromMmoItem(raw);
        }
        return null;
    }

    private ItemStack tryBuildFromMmoItem(Object raw) throws ReflectiveOperationException {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ItemStack itemStack) {
            return itemStack;
        }
        if (mmoItemNewBuilderMethod == null) {
            mmoItemNewBuilderMethod = findMethod(raw.getClass(), "newBuilder", 0);
        }
        Object builder = mmoItemNewBuilderMethod == null ? null : mmoItemNewBuilderMethod.invoke(raw);
        if (builder == null) {
            return null;
        }
        if (builderBuildMethod == null) {
            builderBuildMethod = findMethod(builder.getClass(), "build", 0);
        }
        Object built = builderBuildMethod == null ? null : builderBuildMethod.invoke(builder);
        return built instanceof ItemStack itemStack ? itemStack : null;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        return null;
    }

    private static Object invokeNoArgsIfPresent(Class<?> type, Object instance, String name) {
        try {
            return type.getMethod(name).invoke(instance);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
