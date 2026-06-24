package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;

public final class DefaultAriaBridge implements AriaBridge {

    private static final String HOST_PLUGIN_NAME = "BlinkAriaHost";
    private static final String MANAGER_CLASS = "priv.seventeen.artist.blink.script.AriaScriptManager";
    private static final String[] DISCOVERY_PLUGINS = {
        "Symphony", "Overture", "BlinkAriaHost"
    };

    private final JavaPlugin plugin;

    private boolean available;
    private Object managerInstance;
    private Method evalMethod;
    private Method isAvailableMethod;
    private Method versionGetter;
    private String version;

    public DefaultAriaBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();
        ClassLoader classLoader = resolveClassLoader();
        if (classLoader == null) {
            return;
        }
        try {
            Class<?> managerClass = Class.forName(MANAGER_CLASS, true, classLoader);
            managerInstance = resolveKotlinObject(managerClass);
            if (managerInstance == null) {
                return;
            }
            isAvailableMethod = managerClass.getMethod("isAvailable");
            Object availableFlag = isAvailableMethod.invoke(managerInstance);
            if (!(availableFlag instanceof Boolean bool) || !bool) {
                return;
            }
            evalMethod = managerClass.getMethod("eval", String.class, Map.class);
            versionGetter = managerClass.getMethod("getVersion");
            Object versionValue = versionGetter.invoke(managerInstance);
            version = versionValue == null ? null : String.valueOf(versionValue);
            available = true;
            plugin.getLogger().fine("[Aria] 桥接初始化成功 (v" + (version == null ? "?" : version) + ")");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[Aria] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public @Nullable String version() {
        return version;
    }

    @Override
    public @Nullable Object eval(@NotNull String code, @NotNull Map<String, Object> bindings) {
        if (!available || evalMethod == null || managerInstance == null) {
            return null;
        }
        try {
            return evalMethod.invoke(managerInstance, code, bindings);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().fine("[Aria] eval 失败: " + exception.getMessage());
            return null;
        }
    }

    private @Nullable ClassLoader resolveClassLoader() {
        Plugin host = Bukkit.getPluginManager().getPlugin(HOST_PLUGIN_NAME);
        if (host != null && host.isEnabled()) {
            try {
                Class.forName(MANAGER_CLASS, false, host.getClass().getClassLoader());
                return host.getClass().getClassLoader();
            } catch (ClassNotFoundException ignored) {
            }
        }
        for (String pluginName : DISCOVERY_PLUGINS) {
            Plugin candidate = Bukkit.getPluginManager().getPlugin(pluginName);
            if (candidate == null || !candidate.isEnabled()) {
                continue;
            }
            try {
                Class.forName(MANAGER_CLASS, false, candidate.getClass().getClassLoader());
                return candidate.getClass().getClassLoader();
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static @Nullable Object resolveKotlinObject(Class<?> managerClass) throws ReflectiveOperationException {
        Field instanceField = managerClass.getField("INSTANCE");
        return instanceField.get(null);
    }

    private void reset() {
        available = false;
        managerInstance = null;
        evalMethod = null;
        isAvailableMethod = null;
        versionGetter = null;
        version = null;
    }
}
