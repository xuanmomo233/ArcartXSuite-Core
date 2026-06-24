package xuanmo.arcartxsuite.bridge;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;

public final class ArcartXPacketBridge implements PacketBridgeAPI {

    private final JavaPlugin plugin;

    private boolean available;
    private Object uiRegistry;
    private Method registerMethod;
    private Method reloadMethod;
    private Method unregisterMethod;
    private Method getUiMethod;
    private Method openMethod;
    private Method openWithCallbackMethod;
    private Method closeMethod;
    private Method openUnsafeMethod;
    private Method openUnsafeWithCallbackMethod;
    private Method closeUnsafeMethod;
    private Method sendPacketUnsafeMethod;
    private List<Method> sendPacketMethods = List.of();
    private Object entityManager;
    private Method getArcartXHandlerMethod;
    private Class<?> callbackInterface;
    private Object closeCallbackType;
    private Class<?> uiCallbackInterface;
    private Method callDataPlayerMethod;
    private final ConcurrentMap<String, Object> uiAdapters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Consumer<Player>> closeCallbacks = new ConcurrentHashMap<>();
    private final Set<String> closeCallbackRegisteredUiIds = ConcurrentHashMap.newKeySet();
    private final Set<Object> oneShotCallbacks = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, List<Object>> callbackKeepAlive = new ConcurrentHashMap<>();
    private boolean unsafeFallbackWarned;
    private boolean openCallbackFallbackWarned;
    private int successfulUiRegistrationCount;

    public ArcartXPacketBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        available = false;
        uiRegistry = null;
        registerMethod = null;
        reloadMethod = null;
        unregisterMethod = null;
        getUiMethod = null;
        openMethod = null;
        openWithCallbackMethod = null;
        closeMethod = null;
        openUnsafeMethod = null;
        openUnsafeWithCallbackMethod = null;
        closeUnsafeMethod = null;
        sendPacketUnsafeMethod = null;
        sendPacketMethods = List.of();
        getArcartXHandlerMethod = null;
        callbackInterface = null;
        closeCallbackType = null;
        uiCallbackInterface = null;
        callDataPlayerMethod = null;
        uiAdapters.clear();
        closeCallbacks.clear();
        closeCallbackRegisteredUiIds.clear();
        oneShotCallbacks.clear();
        callbackKeepAlive.clear();
        unsafeFallbackWarned = false;
        openCallbackFallbackWarned = false;
        successfulUiRegistrationCount = 0;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            plugin.getLogger().severe("arcartxsuite 需要 ArcartX。");
            return false;
        }

        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI", true, classLoader);
            Class<?> entityManagerClass = Class.forName("priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager", true, classLoader);

            uiRegistry = apiClass.getMethod("getUIRegistry").invoke(null);
            initializeOpenCallbackBridge(classLoader);
            registerMethod = findRequiredMethod(uiRegistry.getClass(), "register", String.class, File.class);
            reloadMethod = findMethod(uiRegistry.getClass(), "reload", String.class, File.class);
            unregisterMethod = findMethod(uiRegistry.getClass(), "unregister", String.class);
            getUiMethod = findMethod(uiRegistry.getClass(), "get", String.class);
            openMethod = findRequiredMethod(uiRegistry.getClass(), "open", Player.class, String.class);
            openWithCallbackMethod = callbackInterface == null
                ? null
                : findMethod(uiRegistry.getClass(), "open", Player.class, String.class, callbackInterface);
            closeMethod = findRequiredMethod(uiRegistry.getClass(), "close", Player.class, String.class);
            openUnsafeMethod = findMethod(uiRegistry.getClass(), "openUnsafe", Player.class, String.class);
            openUnsafeWithCallbackMethod = callbackInterface == null
                ? null
                : findMethod(uiRegistry.getClass(), "openUnsafe", Player.class, String.class, callbackInterface);
            closeUnsafeMethod = findMethod(uiRegistry.getClass(), "closeUnsafe", Player.class, String.class);
            sendPacketUnsafeMethod = findMethod(uiRegistry.getClass(), "sendPacketUnsafe", Player.class, String.class, String.class, Object.class);
            sendPacketMethods = findSendPacketMethods(uiRegistry.getClass());
            entityManager = apiClass.getMethod("getEntityManager").invoke(null);
            getArcartXHandlerMethod = findMethod(entityManagerClass, "getPlayer", Player.class);
            initializeUiCallbackBridge(classLoader);

            if (sendPacketMethods.isEmpty()) {
                throw new NoSuchMethodException("未找到兼容的 UIRegistry.sendPacket 方法");
            }

            available = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().severe("初始化 ArcartX 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        uiRegistry = null;
        openWithCallbackMethod = null;
        openUnsafeMethod = null;
        openUnsafeWithCallbackMethod = null;
        closeUnsafeMethod = null;
        sendPacketUnsafeMethod = null;
        sendPacketMethods = List.of();
        entityManager = null;
        getArcartXHandlerMethod = null;
        uiAdapters.clear();
        closeCallbacks.clear();
        closeCallbackRegisteredUiIds.clear();
        oneShotCallbacks.clear();
        callbackKeepAlive.clear();
        unsafeFallbackWarned = false;
        openCallbackFallbackWarned = false;
    }

    public boolean isAvailable() {
        return available;
    }

    public String describePacketMode() {
        if (sendPacketMethods.isEmpty()) {
            return "unavailable";
        }

        Set<String> payloadTypes = new LinkedHashSet<>();
        for (Method method : sendPacketMethods) {
            payloadTypes.add(wrap(method.getParameterTypes()[3]).getSimpleName());
        }
        return "sendPacket(" + String.join("/", payloadTypes) + ")";
    }

    public void resetUiRegistrationCount() {
        successfulUiRegistrationCount = 0;
    }

    public int successfulUiRegistrationCount() {
        return successfulUiRegistrationCount;
    }

    public PacketBridgeAPI.UiRegistrationResult registerOrReloadUi(String configuredUiId, File uiFile) {
        if (!available || uiRegistry == null) {
            return PacketBridgeAPI.UiRegistrationResult.failure(normalizeUiId(configuredUiId, uiFile), "ArcartX 桥接未就绪。");
        }

        Set<String> candidateUiIds = buildCandidateUiIds(configuredUiId, uiFile);
        String lastFailure = "未知错误";

        for (String candidateUiId : candidateUiIds) {
            try {
                RegistryCallResult reloadResult = tryReloadUi(candidateUiId, uiFile);
                if (reloadResult.success()) {
                    logRegisteredUi(configuredUiId, candidateUiId, uiFile, reloadResult.action());
                    return uiSuccess(candidateUiId, candidateUiId, reloadResult.action());
                }
                lastFailure = reloadResult.message();

                RegistryCallResult registerResult = tryRegisterUi(candidateUiId, uiFile);
                if (registerResult.success()) {
                    logRegisteredUi(configuredUiId, candidateUiId, uiFile, registerResult.action());
                    return uiSuccess(candidateUiId, candidateUiId, registerResult.action());
                }
                lastFailure = registerResult.message();
            } catch (ReflectiveOperationException exception) {
                lastFailure = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            }
        }

        plugin.getLogger().severe(
            "ArcartX UI 注册失败，配置 UI 标识为 '" + configuredUiId + "'，"
                + "导出文件为 " + uiFile.getAbsolutePath() + "，最后一次失败原因: " + lastFailure
        );
        return PacketBridgeAPI.UiRegistrationResult.failure(normalizeUiId(configuredUiId, uiFile), lastFailure);
    }

    public boolean unregisterUi(String uiId) {
        if (!available || uiRegistry == null || unregisterMethod == null || uiId == null || uiId.isBlank()) {
            return false;
        }
        try {
            RegistryCallResult result = invokeRegistryMethod(unregisterMethod, uiId);
            uiAdapters.remove(uiId);
            closeCallbacks.remove(uiId);
            closeCallbackRegisteredUiIds.remove(uiId);
            callbackKeepAlive.remove(uiId);
            if (!result.success() && result.exceptionThrown()) {
                plugin.getLogger().warning("ArcartX UI 注销失败(" + uiId + "): " + result.message());
            }
            return result.success();
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public boolean openUi(Player player, String uiId) {
        return invokeBridgeMethod(openMethod, player, uiId);
    }

    public boolean openUiWithCallback(Player player, String uiId, Runnable callback) {
        if (openWithCallbackMethod == null) {
            warnOpenCallbackFallback("open");
            return false;
        }
        return invokeOpenCallbackBridgeMethod("open", openWithCallbackMethod, player, uiId, callback);
    }

    public boolean openUiUnsafe(Player player, String uiId) {
        if (openUnsafeMethod == null) {
            warnUnsafeFallback("openUnsafe");
            return openUi(player, uiId);
        }
        return invokeUnsafeBridgeMethod("openUnsafe", openUnsafeMethod, player, uiId);
    }

    public boolean openUiUnsafeWithCallback(Player player, String uiId, Runnable callback) {
        if (openUnsafeWithCallbackMethod == null) {
            warnOpenCallbackFallback("openUnsafe");
            return false;
        }
        return invokeOpenCallbackBridgeMethod("openUnsafe", openUnsafeWithCallbackMethod, player, uiId, callback);
    }

    public boolean closeUi(Player player, String uiId) {
        return invokeBridgeMethod(closeMethod, player, uiId);
    }

    public boolean closeUiUnsafe(Player player, String uiId) {
        if (closeUnsafeMethod == null) {
            warnUnsafeFallback("closeUnsafe");
            return closeUi(player, uiId);
        }
        return invokeUnsafeBridgeMethod("closeUnsafe", closeUnsafeMethod, player, uiId);
    }

    public boolean sendPacket(Player player, String uiId, String handlerName, Object payload) {
        if (!available || uiRegistry == null) {
            return false;
        }

        Method method = findBestMethod(payload);
        if (method == null) {
            plugin.getLogger().warning(
                "ArcartX 未找到可用于 payload 类型 " + describePayloadType(payload) + " 的 sendPacket 签名。"
            );
            return false;
        }

        try {
            Object result = method.invoke(uiRegistry, player, uiId, handlerName, payload);
            if (result instanceof Boolean booleanResult) {
                return booleanResult.booleanValue();
            }
            return true;
        } catch (IllegalArgumentException | ReflectiveOperationException exception) {
            plugin.getLogger().warning("ArcartX 发包失败: " + describeInvocationFailure(exception));
            return false;
        }
    }

    public boolean sendPacketToAll(Player player, List<String> uiIds, String handlerName, Object payload) {
        boolean anySuccess = false;
        for (String uiId : uiIds) {
            anySuccess |= sendPacket(player, uiId, handlerName, payload);
        }
        return anySuccess;
    }

    public boolean openUiAll(Player player, List<String> uiIds) {
        boolean anySuccess = false;
        for (String uiId : uiIds) {
            anySuccess |= openUi(player, uiId);
        }
        return anySuccess;
    }

    public boolean closeUiAll(Player player, List<String> uiIds) {
        boolean anySuccess = false;
        for (String uiId : uiIds) {
            anySuccess |= closeUi(player, uiId);
        }
        return anySuccess;
    }

    public boolean sendPacketUnsafe(Player player, String uiId, String handlerName, Object payload) {
        if (sendPacketUnsafeMethod == null) {
            warnUnsafeFallback("sendPacketUnsafe");
            return sendPacket(player, uiId, handlerName, payload);
        }
        return invokeUnsafeBridgeMethod("sendPacketUnsafe", sendPacketUnsafeMethod, player, uiId, handlerName, payload);
    }

    public boolean registerUiCloseCallback(String uiId, Consumer<Player> callback) {
        if (!available || uiId == null || uiId.isBlank() || callback == null) {
            return false;
        }
        if (closeCallbackType == null || uiCallbackInterface == null || callDataPlayerMethod == null) {
            return false;
        }

        closeCallbacks.put(uiId, callback);
        if (closeCallbackRegisteredUiIds.contains(uiId)) {
            return true;
        }

        Object adapter = uiAdapter(uiId);
        if (adapter == null) {
            closeCallbacks.remove(uiId, callback);
            return false;
        }

        Method registerCallBackMethod = findMethod(adapter.getClass(), "registerCallBack", closeCallbackType.getClass(), uiCallbackInterface);
        if (registerCallBackMethod == null) {
            closeCallbacks.remove(uiId, callback);
            return false;
        }

        InvocationHandler handler = (proxy, method, args) -> {
            if (args == null || args.length != 1 || args[0] == null) {
                return null;
            }
            try {
                Object rawPlayer = callDataPlayerMethod.invoke(args[0]);
                if (!(rawPlayer instanceof Player player)) {
                    return null;
                }
                Consumer<Player> currentCallback = closeCallbacks.get(uiId);
                if (currentCallback != null && plugin.isEnabled()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> currentCallback.accept(player));
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("ArcartX UI 关闭回调处理失败(" + uiId + "): " + describeInvocationFailure(exception));
            }
            return null;
        };

        Object proxy = Proxy.newProxyInstance(
            uiCallbackInterface.getClassLoader(),
            new Class<?>[]{uiCallbackInterface},
            handler
        );

        try {
            registerCallBackMethod.invoke(adapter, closeCallbackType, proxy);
            callbackKeepAlive.computeIfAbsent(uiId, ignored -> Collections.synchronizedList(new ArrayList<>())).add(proxy);
            closeCallbackRegisteredUiIds.add(uiId);
            return true;
        } catch (IllegalArgumentException | ReflectiveOperationException exception) {
            closeCallbacks.remove(uiId, callback);
            plugin.getLogger().warning("注册 ArcartX UI CLOSE 回调失败(" + uiId + "): " + describeInvocationFailure(exception));
            return false;
        }
    }

    public void unregisterUiCloseCallback(String uiId) {
        if (uiId == null || uiId.isBlank()) {
            return;
        }
        closeCallbacks.remove(uiId);
    }

    @SuppressWarnings("unchecked")
    public boolean sendChatCard(Player player, String cardId, Map<String, String> data) {
        if (!available || cardId == null || cardId.isBlank() || getArcartXHandlerMethod == null || entityManager == null) {
            return false;
        }
        try {
            Object handler = getArcartXHandlerMethod.invoke(entityManager, player);
            if (handler == null) {
                return false;
            }
            Method sendChatCard = findRequiredMethod(handler.getClass(), "sendChatCard", String.class, Map.class);
            sendChatCard.invoke(handler, cardId, data);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("发送 ArcartX 聊天卡片 '" + cardId + "' 失败: " + exception.getMessage());
            return false;
        }
    }

    public static String normalizeUiId(String configuredUiId, File uiFile) {
        if (configuredUiId != null && !configuredUiId.isBlank()) {
            return configuredUiId.trim();
        }
        String fileName = uiFile.getName();
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    private void initializeOpenCallbackBridge(ClassLoader classLoader) {
        try {
            callbackInterface = Class.forName(
                "priv.seventeen.artist.arcartx.util.collections.CallBack",
                true,
                classLoader
            );
        } catch (ReflectiveOperationException exception) {
            callbackInterface = null;
            plugin.getLogger().warning("当前 ArcartX 未提供 UI OPEN 回调桥接，相关 HUD 将使用降级延迟发包模式。");
        }
    }

    private void initializeUiCallbackBridge(ClassLoader classLoader) {
        try {
            Class<?> callbackTypeClass = Class.forName(
                "priv.seventeen.artist.arcartx.core.ui.adapter.CallBackType",
                true,
                classLoader
            );
            closeCallbackType = callbackTypeClass.getField("CLOSE").get(null);
            uiCallbackInterface = Class.forName(
                "priv.seventeen.artist.arcartx.util.collections.UICallBack",
                true,
                classLoader
            );
            Class<?> callDataClass = Class.forName(
                "priv.seventeen.artist.arcartx.util.collections.CallData",
                true,
                classLoader
            );
            callDataPlayerMethod = findRequiredMethod(callDataClass, "player");
        } catch (ReflectiveOperationException exception) {
            closeCallbackType = null;
            uiCallbackInterface = null;
            callDataPlayerMethod = null;
            plugin.getLogger().warning("当前 ArcartX 未提供 UI CLOSE 回调桥接，部分 Menu 生命周期将使用降级模式。");
        }
    }

    private RegistryCallResult tryReloadUi(String uiId, File uiFile) throws ReflectiveOperationException {
        if (reloadMethod == null) {
            return RegistryCallResult.failure("reload", "ArcartX UIRegistry 未提供 reload(String, File) 方法。");
        }
        Object previousAdapter = uiAdapters.get(uiId);
        RegistryCallResult result = invokeRegistryMethod(reloadMethod, uiId, uiFile);
        if (result.success()) {
            Object currentAdapter = refreshUiAdapter(uiId);
            if (currentAdapter != previousAdapter) {
                closeCallbackRegisteredUiIds.remove(uiId);
                callbackKeepAlive.remove(uiId);
            }
            return RegistryCallResult.success("reload", "reload 成功");
        }
        return result;
    }

    private RegistryCallResult tryRegisterUi(String uiId, File uiFile) throws ReflectiveOperationException {
        if (unregisterMethod != null) {
            RegistryCallResult unregisterResult = invokeRegistryMethod(unregisterMethod, uiId);
            if (!unregisterResult.success() && unregisterResult.exceptionThrown()) {
                plugin.getLogger().warning("ArcartX UI 注销失败(" + uiId + "): " + unregisterResult.message());
            }
        }

        RegistryCallResult result = invokeRegistryMethod(registerMethod, uiId, uiFile);
        if (result.success()) {
            closeCallbackRegisteredUiIds.remove(uiId);
            callbackKeepAlive.remove(uiId);
            refreshUiAdapter(uiId);
            return RegistryCallResult.success("register", "register 成功");
        }
        return result;
    }

    private Object uiAdapter(String uiId) {
        Object adapter = uiAdapters.get(uiId);
        if (adapter != null) {
            return adapter;
        }
        return refreshUiAdapter(uiId);
    }

    private Object refreshUiAdapter(String uiId) {
        if (!available || uiRegistry == null || getUiMethod == null || uiId == null || uiId.isBlank()) {
            uiAdapters.remove(uiId);
            return null;
        }
        try {
            Object adapter = getUiMethod.invoke(uiRegistry, uiId);
            if (adapter == null) {
                uiAdapters.remove(uiId);
            } else {
                uiAdapters.put(uiId, adapter);
            }
            return adapter;
        } catch (IllegalArgumentException | ReflectiveOperationException exception) {
            uiAdapters.remove(uiId);
            return null;
        }
    }

    private void logRegisteredUi(String configuredUiId, String actualUiId, File uiFile, String action) {
        successfulUiRegistrationCount++;
        if (configuredUiId != null && !configuredUiId.isBlank() && !actualUiId.equals(configuredUiId)) {
            plugin.getLogger().warning(
                "ArcartX UI 标识 '" + configuredUiId + "' 不可用，已自动回退为 '" + actualUiId + "'。"
            );
        }
        plugin.getLogger().fine(
            "ArcartX UI 已" + ("reload".equals(action) ? "重载" : "注册")
                + ": " + actualUiId + " <- " + uiFile.getAbsolutePath()
        );
    }

    private boolean invokeBridgeMethod(Method method, Object... args) {
        if (!available || method == null) {
            return false;
        }
        try {
            Object result = method.invoke(uiRegistry, args);
            if (result instanceof Boolean booleanResult) {
                return booleanResult.booleanValue();
            }
            return true;
        } catch (IllegalArgumentException | ReflectiveOperationException exception) {
            plugin.getLogger().warning("ArcartX 桥接调用失败: " + describeInvocationFailure(exception));
            return false;
        }
    }

    private boolean invokeUnsafeBridgeMethod(String action, Method method, Object... args) {
        if (!available || uiRegistry == null || method == null) {
            return false;
        }
        try {
            Object result = method.invoke(uiRegistry, args);
            if (result instanceof Boolean booleanResult) {
                return booleanResult.booleanValue();
            }
            return true;
        } catch (IllegalArgumentException | ReflectiveOperationException exception) {
            plugin.getLogger().warning("ArcartX unsafe 桥接调用失败(" + action + "): " + describeInvocationFailure(exception));
            return false;
        }
    }

    private boolean invokeOpenCallbackBridgeMethod(String action, Method method, Player player, String uiId, Runnable callback) {
        if (!available || uiRegistry == null || method == null || callbackInterface == null || callback == null) {
            return false;
        }
        AtomicBoolean called = new AtomicBoolean(false);
        InvocationHandler handler = (proxy, invokedMethod, args) -> {
            if (invokedMethod.getDeclaringClass() == Object.class) {
                return switch (invokedMethod.getName()) {
                    case "toString" -> "ArcartXPacketBridgeOpenCallback(" + uiId + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                    default -> null;
                };
            }
            if (called.compareAndSet(false, true)) {
                oneShotCallbacks.remove(proxy);
                if (plugin.isEnabled()) {
                    plugin.getServer().getScheduler().runTask(plugin, callback);
                }
            }
            return null;
        };

        Object proxy = Proxy.newProxyInstance(
            callbackInterface.getClassLoader(),
            new Class<?>[]{callbackInterface},
            handler
        );
        oneShotCallbacks.add(proxy);

        try {
            Object result = method.invoke(uiRegistry, player, uiId, proxy);
            if (result instanceof Boolean booleanResult && !booleanResult.booleanValue()) {
                oneShotCallbacks.remove(proxy);
                return false;
            }
            return true;
        } catch (IllegalArgumentException | ReflectiveOperationException exception) {
            oneShotCallbacks.remove(proxy);
            plugin.getLogger().warning("ArcartX 打开 UI 回调桥接调用失败(" + action + "): " + describeInvocationFailure(exception));
            return false;
        }
    }

    private RegistryCallResult invokeRegistryMethod(Method method, Object... args) throws ReflectiveOperationException {
        try {
            Object result = method.invoke(uiRegistry, args);
            if (result instanceof Boolean booleanResult && !booleanResult.booleanValue()) {
                return RegistryCallResult.failure(method.getName(), "ArcartX 返回 false");
            }
            return RegistryCallResult.success(method.getName(), "调用成功");
        } catch (InvocationTargetException exception) {
            return RegistryCallResult.failure(method.getName(), describeInvocationFailure(exception), true);
        }
    }

    private Method findBestMethod(Object payload) {
        if (sendPacketMethods.isEmpty()) {
            return null;
        }

        Class<?> payloadType = payload == null ? Object.class : payload.getClass();
        Method bestMatch = null;
        int bestScore = -1;
        for (Method method : sendPacketMethods) {
            int score = compatibilityScore(method.getParameterTypes()[3], payloadType);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = method;
            }
        }
        return bestScore >= 0 ? bestMatch : null;
    }


    private void warnUnsafeFallback(String methodName) {
        if (unsafeFallbackWarned) {
            return;
        }

        plugin.getLogger().warning(
            "当前 ArcartX 未提供 " + methodName + "，Conversation 将回退到普通 UIRegistry 调用。"
        );
        unsafeFallbackWarned = true;
    }

    private void warnOpenCallbackFallback(String methodName) {
        if (openCallbackFallbackWarned) {
            return;
        }

        plugin.getLogger().warning(
            "当前 ArcartX 未提供 " + methodName + "(Player,String,CallBack)，相关 HUD 将回退到延迟发包模式。"
        );
        openCallbackFallbackWarned = true;
    }

    private static String describeInvocationFailure(Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException invocationTargetException
            ? invocationTargetException.getCause()
            : throwable;
        if (cause == null) {
            return throwable.getClass().getSimpleName();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }

    private static Set<String> buildCandidateUiIds(String configuredUiId, File uiFile) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeUiId(configuredUiId, uiFile);
        candidates.add(normalized);

        int namespaceSeparator = normalized.lastIndexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            candidates.add(normalized.substring(namespaceSeparator + 1));
        }

        return candidates;
    }

    private static List<Method> findSendPacketMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("sendPacket") || method.getParameterCount() != 4) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (compatibilityScore(parameterTypes[0], Player.class) < 0) {
                continue;
            }
            if (compatibilityScore(parameterTypes[1], String.class) < 0) {
                continue;
            }
            if (compatibilityScore(parameterTypes[2], String.class) < 0) {
                continue;
            }

            methods.add(method);
        }
        return List.copyOf(methods);
    }



    private static String describePayloadType(Object payload) {
        return payload == null ? "null" : payload.getClass().getSimpleName();
    }

    private static Method findRequiredMethod(Class<?> type, String name, Class<?>... expectedParameterTypes) throws NoSuchMethodException {
        Method method = findMethod(type, name, expectedParameterTypes);
        if (method != null) {
            return method;
        }
        throw new NoSuchMethodException("No compatible method " + name + " on " + type.getName());
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            Method bestMatch = null;
            int bestScore = -1;
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                int score = compatibilityScore(method.getParameterTypes(), parameterTypes);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = method;
                }
            }
            return bestMatch;
        }
    }

    private static int compatibilityScore(Class<?>[] actualParameterTypes, Class<?>[] expectedParameterTypes) {
        if (actualParameterTypes.length != expectedParameterTypes.length) {
            return -1;
        }

        int totalScore = 0;
        for (int index = 0; index < actualParameterTypes.length; index++) {
            int score = compatibilityScore(actualParameterTypes[index], expectedParameterTypes[index]);
            if (score < 0) {
                return -1;
            }
            totalScore += score;
        }
        return totalScore;
    }

    private static int compatibilityScore(Class<?> actualParameterType, Class<?> expectedParameterType) {
        Class<?> wrappedActual = wrap(actualParameterType);
        Class<?> wrappedExpected = wrap(expectedParameterType);

        if (wrappedActual.equals(wrappedExpected)) {
            return 4;
        }
        if (wrappedActual.isAssignableFrom(wrappedExpected)) {
            return 3;
        }
        if (wrappedExpected.isAssignableFrom(wrappedActual)) {
            return 1;
        }
        return -1;
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

    // UiRegistrationResult 已迁移到 PacketBridgeAPI.UiRegistrationResult
    private static PacketBridgeAPI.UiRegistrationResult uiSuccess(String runtimeUiId, String registeredUiId, String action) {
        return new PacketBridgeAPI.UiRegistrationResult(true, runtimeUiId, registeredUiId, action, "");
    }

    private record RegistryCallResult(String action, boolean success, String message, boolean exceptionThrown) {
        private static RegistryCallResult success(String action, String message) {
            return new RegistryCallResult(action, true, message, false);
        }

        private static RegistryCallResult failure(String action, String message) {
            return new RegistryCallResult(action, false, message, false);
        }

        private static RegistryCallResult failure(String action, String message, boolean exceptionThrown) {
            return new RegistryCallResult(action, false, message, exceptionThrown);
        }
    }

}
