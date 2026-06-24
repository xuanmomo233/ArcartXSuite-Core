package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;

public class ArcartXClientBridge implements ClientBridgeAPI {

    private final JavaPlugin plugin;

    private boolean available;
    private Method getNetworkSenderMethod;
    private Object networkSender;
    private Method sendDamageDisplayMethod;
    private List<Method> sendServerVariableMethods = List.of();
    private Method doWithSeenByMethod;
    private Class<?> playerCallbackType;

    public ArcartXClientBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        available = false;
        getNetworkSenderMethod = null;
        networkSender = null;
        sendDamageDisplayMethod = null;
        sendServerVariableMethods = List.of();
        doWithSeenByMethod = null;
        playerCallbackType = null;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI", true, classLoader);
            Class<?> entityUtilsClass = Class.forName("priv.seventeen.artist.arcartx.util.EntityUtils", true, classLoader);
            playerCallbackType = Class.forName("priv.seventeen.artist.arcartx.util.collections.PlayerCallBack", true, classLoader);
            if (!playerCallbackType.isInterface()) {
                throw new IllegalStateException("ArcartX PlayerCallBack 不是接口，无法创建兼容代理。");
            }

            getNetworkSenderMethod = apiClass.getMethod("getNetworkSender");
            networkSender = getNetworkSenderMethod.invoke(null);
            if (networkSender == null) {
                throw new IllegalStateException("ArcartXNetworkSender 不可用。");
            }

            Class<?> senderType = networkSender.getClass();
            sendDamageDisplayMethod = senderType.getMethod(
                "sendDamageDisplay",
                Player.class,
                String.class,
                double.class,
                Entity.class
            );
            sendServerVariableMethods = findSendServerVariableMethods(senderType);
            if (sendServerVariableMethods.isEmpty()) {
                throw new NoSuchMethodException("未找到 ArcartXNetworkSender.sendServerVariable(Player, String, ?)");
            }

            doWithSeenByMethod = entityUtilsClass.getMethod("doWithSeenBy", Entity.class, playerCallbackType);
            available = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("初始化 ArcartX 客户端桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        getNetworkSenderMethod = null;
        networkSender = null;
        sendDamageDisplayMethod = null;
        sendServerVariableMethods = List.of();
        doWithSeenByMethod = null;
        playerCallbackType = null;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean sendDamageDisplay(Player player, String configId, double amount, Entity target) {
        if (!available || player == null || configId == null || configId.isBlank() || sendDamageDisplayMethod == null) {
            return false;
        }
        try {
            sendDamageDisplayMethod.invoke(resolveNetworkSender(), player, configId, amount, target);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("ArcartX 伤害数字发送失败: " + describeException(exception));
            return false;
        }
    }

    public boolean sendServerVariable(Player player, String variableName, Object value) {
        if (!available || player == null || variableName == null || variableName.isBlank()) {
            return false;
        }

        Method method = findBestServerVariableMethod(value);
        if (method == null) {
            plugin.getLogger().warning(
                "ArcartX 未找到兼容的 sendServerVariable 签名，变量="
                    + variableName
                    + "，类型="
                    + describeType(value)
            );
            return false;
        }

        try {
            method.invoke(resolveNetworkSender(), player, variableName, value);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("ArcartX 服务器变量发送失败: " + describeException(exception));
            return false;
        }
    }

    public boolean forEachSeenPlayer(Entity entity, Consumer<Player> consumer) {
        if (!available || entity == null || consumer == null || doWithSeenByMethod == null || playerCallbackType == null) {
            return false;
        }

        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "toString":
                    return "ArcartXPlayerCallbackProxy";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == (args == null || args.length == 0 ? null : args[0]);
                default:
                    if (args != null) {
                        for (Object arg : args) {
                            if (arg instanceof Player player) {
                                consumer.accept(player);
                                break;
                            }
                        }
                    }
                    return defaultValue(method.getReturnType());
            }
        };

        Object callback = Proxy.newProxyInstance(
            playerCallbackType.getClassLoader(),
            new Class<?>[] {playerCallbackType},
            handler
        );
        try {
            doWithSeenByMethod.invoke(null, entity, callback);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("ArcartX 可视玩家遍历失败: " + describeException(exception));
            return false;
        }
    }

    private Object resolveNetworkSender() throws IllegalAccessException, InvocationTargetException {
        if (networkSender != null) {
            return networkSender;
        }
        networkSender = getNetworkSenderMethod.invoke(null);
        return networkSender;
    }

    private Method findBestServerVariableMethod(Object value) {
        Class<?> valueType = value == null ? Object.class : value.getClass();
        Method bestMatch = null;
        int bestScore = -1;
        for (Method method : sendServerVariableMethods) {
            int score = compatibilityScore(method.getParameterTypes()[2], valueType, value == null);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = method;
            }
        }
        return bestScore >= 0 ? bestMatch : null;
    }

    private static List<Method> findSendServerVariableMethods(Class<?> senderType) {
        List<Method> methods = new ArrayList<>();
        for (Method method : senderType.getMethods()) {
            if (!"sendServerVariable".equals(method.getName())) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 3) {
                continue;
            }
            if (parameterTypes[0] != Player.class || parameterTypes[1] != String.class) {
                continue;
            }
            methods.add(method);
        }
        return List.copyOf(methods);
    }

    private static int compatibilityScore(Class<?> expectedType, Class<?> actualType, boolean nullValue) {
        Class<?> wrappedExpected = wrap(expectedType);
        if (nullValue) {
            return expectedType.isPrimitive() ? -1 : (Object.class.equals(wrappedExpected) ? 1 : 0);
        }

        Class<?> wrappedActual = wrap(actualType);
        if (wrappedExpected.equals(wrappedActual)) {
            return 4;
        }
        if (wrappedExpected.isAssignableFrom(wrappedActual)) {
            return Object.class.equals(wrappedExpected) ? 1 : 3;
        }
        return -1;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        return null;
    }

    private static String describeType(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String describeException(Exception exception) {
        Throwable cause = exception instanceof InvocationTargetException invocationTargetException
            ? invocationTargetException.getCause()
            : exception;
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
