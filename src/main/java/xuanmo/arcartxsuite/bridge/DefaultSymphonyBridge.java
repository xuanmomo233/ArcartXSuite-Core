package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;

public final class DefaultSymphonyBridge implements SymphonyBridge {

    private static final String PLUGIN_NAME = "Symphony";
    private static final String API_CLASS_NAME = "priv.seventeen.artist.symphony.api.SymphonyAPI";
    private static final String OPERATION_CLASS_NAME = "priv.seventeen.artist.symphony.api.attribute.Operation";

    private final JavaPlugin plugin;

    private boolean available;
    private Object symphonyApiInstance;
    private Method setAttributeMethod;
    private Method removeAttributeMethod;
    private Method recalculateMethod;
    private Object flatOperation;
    private Object percentOperation;

    public DefaultSymphonyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void initialize() {
        reset();

        Plugin symphony = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (symphony == null || !symphony.isEnabled()) {
            return;
        }

        try {
            ClassLoader cl = symphony.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS_NAME, true, cl);
            Object companion = apiClass.getField("Companion").get(null);
            Method getInstanceMethod = companion.getClass().getMethod("getInstance");
            symphonyApiInstance = getInstanceMethod.invoke(companion);
            if (symphonyApiInstance == null) {
                return;
            }

            Class<?> operationClass = Class.forName(OPERATION_CLASS_NAME, true, cl);
            flatOperation = Enum.valueOf((Class<? extends Enum>) operationClass.asSubclass(Enum.class), "FLAT");
            percentOperation = Enum.valueOf((Class<? extends Enum>) operationClass.asSubclass(Enum.class), "PERCENT");

            Class<?> livingEntityClass = Class.forName("org.bukkit.entity.LivingEntity");
            setAttributeMethod = symphonyApiInstance.getClass().getMethod("setAttribute", livingEntityClass, String.class, operationClass, double.class, String.class);
            removeAttributeMethod = symphonyApiInstance.getClass().getMethod("removeAttribute", livingEntityClass, String.class);
            recalculateMethod = symphonyApiInstance.getClass().getMethod("recalculate", livingEntityClass);

            available = true;
            plugin.getLogger().fine("[Symphony] 桥接初始化成功");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[Symphony] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    @Override
    public void setAttribute(Player player, String attributeId, boolean percent, double value, String sourceKey) {
        if (!available || player == null || attributeId == null || sourceKey == null) return;
        try {
            Object operation = percent ? percentOperation : flatOperation;
            setAttributeMethod.invoke(symphonyApiInstance, player, attributeId, operation, value, sourceKey);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[Symphony] setAttribute 失败: " + e.getMessage());
        }
    }

    @Override
    public void removeAttribute(Player player, String sourceKey) {
        if (!available || player == null || sourceKey == null) return;
        try {
            removeAttributeMethod.invoke(symphonyApiInstance, player, sourceKey);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void recalculate(Player player) {
        if (!available || player == null) return;
        try {
            recalculateMethod.invoke(symphonyApiInstance, player);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void reset() {
        available = false;
        symphonyApiInstance = null;
        setAttributeMethod = null;
        removeAttributeMethod = null;
        recalculateMethod = null;
        flatOperation = null;
        percentOperation = null;
    }
}
