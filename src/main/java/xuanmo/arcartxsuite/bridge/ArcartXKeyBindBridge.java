package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArcartXKeyBindBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private Object keyBindRegistry;
    private Method registerMethod;
    private Method unregisterMethod;

    public ArcartXKeyBindBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        available = false;
        keyBindRegistry = null;
        registerMethod = null;
        unregisterMethod = null;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI", true, classLoader);
            keyBindRegistry = apiClass.getMethod("getKeyBindRegistry").invoke(null);
            if (keyBindRegistry == null) {
                throw new IllegalStateException("ArcartX KeyBindRegistry 不可用。");
            }
            registerMethod = findCompatibleMethod(
                keyBindRegistry.getClass(),
                "registerClientKeyBind",
                String.class,
                String.class,
                String.class
            );
            unregisterMethod = findCompatibleMethod(
                keyBindRegistry.getClass(),
                "unRegisterClientKeyBind",
                String.class
            );
            if (unregisterMethod == null) {
                unregisterMethod = findCompatibleMethod(
                    keyBindRegistry.getClass(),
                    "unregisterClientKeyBind",
                    String.class
                );
            }
            if (registerMethod == null || unregisterMethod == null) {
                throw new NoSuchMethodException("未找到兼容的 KeyBindRegistry 注册/注销方法。");
            }
            available = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("初始化 ArcartX KeyBind 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        keyBindRegistry = null;
        registerMethod = null;
        unregisterMethod = null;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean registerClientKeyBind(String keyName, String category, String defaultKey) {
        return invoke(registerMethod, keyName, category, defaultKey);
    }

    public boolean unregisterClientKeyBind(String keyName) {
        return invoke(unregisterMethod, keyName);
    }

    private boolean invoke(Method method, Object... arguments) {
        if (!available || keyBindRegistry == null || method == null) {
            return false;
        }
        try {
            Object result = method.invoke(keyBindRegistry, arguments);
            return !(result instanceof Boolean booleanResult) || booleanResult.booleanValue();
        } catch (IllegalArgumentException | ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocationTargetException
                ? invocationTargetException.getCause()
                : exception;
            String message = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
            plugin.getLogger().warning("ArcartX KeyBind 调用失败: " + message);
            return false;
        }
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != parameterTypes.length) {
                    continue;
                }
                Class<?>[] actual = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < actual.length; index++) {
                    if (!wrap(actual[index]).isAssignableFrom(wrap(parameterTypes[index]))) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    return method;
                }
            }
            return null;
        }
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        return Void.class;
    }
}
