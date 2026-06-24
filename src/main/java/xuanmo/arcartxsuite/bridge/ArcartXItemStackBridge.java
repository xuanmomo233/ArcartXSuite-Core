package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;

public final class ArcartXItemStackBridge implements ItemBridgeAPI {

    private final JavaPlugin plugin;

    private boolean available;
    private Object itemStackNms;
    private Method item2jsonMethod;
    private boolean failureWarned;

    public ArcartXItemStackBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        available = false;
        itemStackNms = null;
        item2jsonMethod = null;
        failureWarned = false;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> itemStackNmsClass = resolveItemBridgeClass(classLoader);
            Object instance = resolveInstance(itemStackNmsClass);
            Method method = findMethod(itemStackNmsClass, "item2json", ItemStack.class);
            if (method == null) {
                method = findMethod(itemStackNmsClass, "item2Json", ItemStack.class);
            }
            if (method == null && instance != null) {
                method = findMethod(instance.getClass(), "item2json", ItemStack.class);
                if (method == null) {
                    method = findMethod(instance.getClass(), "item2Json", ItemStack.class);
                }
            }
            if (method == null) {
                throw new NoSuchMethodException("ItemBridge.item2json/item2Json(ItemStack)");
            }

            itemStackNms = instance;
            item2jsonMethod = method;
            available = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("初始化 ArcartX ItemStack 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        itemStackNms = null;
        item2jsonMethod = null;
        failureWarned = false;
    }

    public boolean isAvailable() {
        return available;
    }

    public Optional<String> itemToJson(ItemStack itemStack) {
        if (!available || item2jsonMethod == null || itemStack == null) {
            return Optional.empty();
        }

        try {
            Object result = item2jsonMethod.invoke(
                Modifier.isStatic(item2jsonMethod.getModifiers()) ? null : itemStackNms,
                itemStack
            );
            if (result instanceof String json && !json.isBlank()) {
                return Optional.of(json);
            }
            return Optional.empty();
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            if (!failureWarned) {
                Throwable cause = exception instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getCause()
                    : exception;
                plugin.getLogger().warning(
                    "ArcartX ItemStack item2json 调用失败，将回退为纯文本拾取提示: "
                        + (cause == null ? exception.getClass().getSimpleName() : cause.getMessage())
                );
                failureWarned = true;
            }
            return Optional.empty();
        }
    }

    private static Object resolveInstance(Class<?> itemStackNmsClass) throws ReflectiveOperationException {
        try {
            Object companion = itemStackNmsClass.getField("Companion").get(null);
            if (companion != null) {
                Method getInstanceMethod = companion.getClass().getMethod("getINSTANCE");
                return getInstanceMethod.invoke(companion);
            }
        } catch (NoSuchFieldException ignored) {
            // ignore
        }

        try {
            return itemStackNmsClass.getField("INSTANCE").get(null);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Class<?> resolveItemBridgeClass(ClassLoader classLoader) throws ClassNotFoundException {
        try {
            return Class.forName("priv.seventeen.artist.arcartx.nms.ItemBridge", true, classLoader);
        } catch (ClassNotFoundException ignored) {
            return Class.forName("priv.seventeen.artist.arcartx.nms.ItemStackNMS", true, classLoader);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
