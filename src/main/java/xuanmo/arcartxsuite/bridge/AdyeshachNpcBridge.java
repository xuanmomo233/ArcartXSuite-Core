package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdyeshachNpcBridge implements xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI {

    private static final long REFLECTION_WARNING_INTERVAL_MS = 5000L;

    private static final String ARCARTX_ENTITY_MANAGER_CLASS = "priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager";
    private static final String ARCARTX_API_CLASS = "priv.seventeen.artist.arcartx.api.ArcartXAPI";
    private static final String ARCARTX_GET_ENTITY_MANAGER = "getEntityManager";
    private static final String ARCARTX_GET_OR_CREATE_ENTITY = "getOrCreateEntity";

    private static final String ADY_API_CLASS = "ink.ptms.adyeshach.api.AdyeshachAPI";
    private static final String ADY_ENTITY_TYPES_CLASS = "ink.ptms.adyeshach.core.entity.EntityTypes";

    private final JavaPlugin plugin;
    private final ConcurrentMap<Class<?>, EntityAccessors> entityAccessorsByClass = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> privateMarkerEntities = new ConcurrentHashMap<>();

    private boolean available;
    private boolean debug;
    private Object apiInstance;
    private Object publicManager;
    private Method getEntitiesMethod;
    private long nextReflectionWarningAtMs;

    private Object arcartXEntityManager;
    private Method getOrCreateEntityMethod;
    private Method axEntitySetModelMethod;
    private Method axEntitySetDefaultStateMethod;
    private Method axEntityPlayAnimationMethod;

    // ArcartXNetworkSender bridge (点对点发包，用于私有实体)
    private Object arcartXNetworkSender;
    private Method networkSendSetEntityModelMethod;
    private Method networkSendSetEntityAnimationMethod;
    private boolean networkSenderBridgeAttempted;

    private Method getPrivateTempManagerMethod;
    private Method managerCreateMethod;
    private Object playerEntityType;
    private Method entityTeleportMethod;
    private Method entitySetVisibleMethod;
    private Method entityDeleteMethod;
    private Method entitySetMetaMethod;

    // AdyeshachEntityVisibleEvent 反射注册监听器（避免编译期依赖 Adyeshach 事件类）
    private Listener adyeshachVisibleListener;
    private BiConsumer<Player, Object> adyeshachVisibleHandler;
    private Method adyVisibleEventGetEntityMethod;
    private Method adyVisibleEventGetViewerMethod;
    private Method adyVisibleEventGetVisibleMethod;

    public AdyeshachNpcBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean initialize() {
        available = false;
        apiInstance = null;
        publicManager = null;
        getEntitiesMethod = null;
        entityAccessorsByClass.clear();
        nextReflectionWarningAtMs = 0L;
        arcartXEntityManager = null;
        getOrCreateEntityMethod = null;
        axEntitySetModelMethod = null;
        axEntitySetDefaultStateMethod = null;
        axEntityPlayAnimationMethod = null;

        Plugin adyeshach = plugin.getServer().getPluginManager().getPlugin("Adyeshach");
        if (adyeshach == null) {
            return false;
        }

        try {
            ClassLoader classLoader = adyeshach.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("ink.ptms.adyeshach.api.AdyeshachAPI", true, classLoader);
            apiInstance = apiClass.getField("INSTANCE").get(null);
            Method getEntityManagerPublicMethod = findMethod(apiClass, "getEntityManagerPublic");
            if (getEntityManagerPublicMethod == null) {
                getEntityManagerPublicMethod = findMethod(apiClass, "getPublicEntityManager");
            }
            if (getEntityManagerPublicMethod == null) {
                throw new NoSuchMethodException("未找到 Adyeshach 公共实体管理器访问方法。");
            }
            publicManager = getEntityManagerPublicMethod.invoke(apiInstance);
            if (publicManager == null) {
                throw new IllegalStateException("Adyeshach 公共实体管理器不可用。");
            }

            getEntitiesMethod = findMethod(publicManager.getClass(), "getEntities");
            if (getEntitiesMethod == null) {
                throw new NoSuchMethodException("未找到 Adyeshach Manager.getEntities()。");
            }

            available = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("初始化 Adyeshach NPC 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
        unregisterVisibleHandler();
        clearAllPrivateMarkers();
        available = false;
        apiInstance = null;
        publicManager = null;
        getEntitiesMethod = null;
        entityAccessorsByClass.clear();
        nextReflectionWarningAtMs = 0L;
        arcartXEntityManager = null;
        getOrCreateEntityMethod = null;
        axEntitySetModelMethod = null;
        axEntitySetDefaultStateMethod = null;
        axEntityPlayAnimationMethod = null;
        arcartXNetworkSender = null;
        networkSendSetEntityModelMethod = null;
        networkSendSetEntityAnimationMethod = null;
        networkSenderBridgeAttempted = false;
        getPrivateTempManagerMethod = null;
        managerCreateMethod = null;
        playerEntityType = null;
        entityTeleportMethod = null;
        entitySetVisibleMethod = null;
        entityDeleteMethod = null;
        entitySetMetaMethod = null;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public List<xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc> findNearby(Player player, double range) {
        if (!available || player == null || !player.isOnline() || range <= 0.0D) {
            return List.of();
        }

        World playerWorld = player.getWorld();
        Location playerLocation = player.getLocation();
        double rangeSquared = range * range;
        List<xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc> result = new ArrayList<>();
        for (Object entity : invokeEntities()) {
            try {
                xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc nearbyNpc = resolveNearbyNpc(entity, player, playerWorld, playerLocation, rangeSquared);
                if (nearbyNpc != null) {
                    result.add(nearbyNpc);
                }
            } catch (RuntimeException exception) {
                warnReflectionFailure("scan-entity", null, entity, exception);
            }
        }

        result.sort(
            Comparator.comparingDouble(xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc::distanceSquared)
                .thenComparing(nearbyNpc -> nearbyNpc.label().toLowerCase(Locale.ROOT))
                .thenComparing(nearbyNpc -> nearbyNpc.npcId().toLowerCase(Locale.ROOT))
        );
        return List.copyOf(result);
    }

    /**
     * 按显示名（或 ID）精确查找第一个匹配的 Adyeshach NPC。
     * 先比较 displayName/customName，再比较 id，均忽略大小写。
     */
    @Override
    public Optional<Object> findByName(String name) {
        if (!available || name == null || name.isBlank()) {
            return Optional.empty();
        }
        String lower = name.strip().toLowerCase(Locale.ROOT);
        for (Object entity : invokeEntities()) {
            if (entity == null) {
                continue;
            }
            EntityAccessors accessors = accessorsFor(entity);
            if (accessors == null || !accessors.valid()) {
                continue;
            }
            if (invokeBoolean(accessors.deletedMethod(), entity, "isDeleted")) {
                continue;
            }
            if (invokeBoolean(accessors.invalidMethod(), entity, "getInvalid")) {
                continue;
            }
            String displayName = invokeString(accessors.displayNameMethod(), entity, "getDisplayName");
            if (!displayName.isBlank() && displayName.strip().toLowerCase(Locale.ROOT).equals(lower)) {
                return Optional.of(entity);
            }
            String customName = invokeString(accessors.customNameMethod(), entity, "getCustomName");
            if (!customName.isBlank() && customName.strip().toLowerCase(Locale.ROOT).equals(lower)) {
                return Optional.of(entity);
            }
            String id = invokeString(accessors.idMethod(), entity, "getId");
            if (!id.isBlank() && id.strip().toLowerCase(Locale.ROOT).equals(lower)) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取 Adyeshach 实体的所有可识别名称（displayName / customName / id），用于反向匹配配置。
     * 顺序与 findByName 一致，便于上层用同一逻辑反查实体对应的配置条目。
     *
     * @param adyeshachEntity Adyeshach 实体对象
     * @return 非空名称列表（去除空白）
     */
    @Override
    public List<String> getEntityNames(Object adyeshachEntity) {
        if (adyeshachEntity == null) {
            return List.of();
        }
        EntityAccessors accessors = accessorsFor(adyeshachEntity);
        if (accessors == null || !accessors.valid()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(3);
        String displayName = invokeString(accessors.displayNameMethod(), adyeshachEntity, "getDisplayName");
        if (!displayName.isBlank()) {
            names.add(displayName.strip());
        }
        String customName = invokeString(accessors.customNameMethod(), adyeshachEntity, "getCustomName");
        if (!customName.isBlank()) {
            names.add(customName.strip());
        }
        String id = invokeString(accessors.idMethod(), adyeshachEntity, "getId");
        if (!id.isBlank()) {
            names.add(id.strip());
        }
        return names;
    }

    /**
     * 注册 "Adyeshach 实体对某玩家可见时" 的回调。
     * 通过反射注册 {@code ink.ptms.adyeshach.core.event.AdyeshachEntityVisibleEvent} 监听器，
     * 当玩家进入 NPC 视野（即客户端首次/重新看到该实体）时触发 handler，
     * 上层可在此时立即对该玩家点对点发送模型/动画包，保证视觉效果与客户端同步。
     *
     * <p>仅处理 {@code visible == true} 的事件（实体显示），不处理隐藏事件。
     *
     * @param handler 回调，参数为 (viewer, adyeshachEntity)
     * @return 注册成功返回 true；Adyeshach 不可用、事件类无法加载或反射失败返回 false
     */
    @Override
    public boolean registerVisibleHandler(BiConsumer<Player, Object> handler) {
        unregisterVisibleHandler();
        if (!available || handler == null) {
            return false;
        }
        Plugin adyeshach = plugin.getServer().getPluginManager().getPlugin("Adyeshach");
        if (adyeshach == null) {
            return false;
        }
        try {
            ClassLoader classLoader = adyeshach.getClass().getClassLoader();
            Class<?> eventClass = Class.forName(
                "ink.ptms.adyeshach.core.event.AdyeshachEntityVisibleEvent", true, classLoader);
            if (!Event.class.isAssignableFrom(eventClass)) {
                plugin.getLogger().warning("AdyeshachNpcBridge: AdyeshachEntityVisibleEvent 不是 Bukkit Event，无法注册监听器。");
                return false;
            }
            adyVisibleEventGetEntityMethod = eventClass.getMethod("getEntity");
            adyVisibleEventGetViewerMethod = eventClass.getMethod("getViewer");
            adyVisibleEventGetVisibleMethod = eventClass.getMethod("getVisible");

            adyeshachVisibleHandler = handler;
            adyeshachVisibleListener = new Listener() {};

            @SuppressWarnings("unchecked")
            Class<? extends Event> typed = (Class<? extends Event>) eventClass;
            EventExecutor executor = (listener, event) -> dispatchAdyeshachVisibleEvent(event);
            plugin.getServer().getPluginManager().registerEvent(
                typed,
                adyeshachVisibleListener,
                EventPriority.MONITOR,
                executor,
                plugin
            );
            if (debug) {
                plugin.getLogger().info("AdyeshachNpcBridge: 已注册 AdyeshachEntityVisibleEvent 监听器。");
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: 注册 AdyeshachEntityVisibleEvent 监听器失败 - "
                + describeException(exception));
            unregisterVisibleHandler();
            return false;
        }
    }

    /**
     * 取消已注册的可见事件监听器。重复调用安全。
     */
    @Override
    public void unregisterVisibleHandler() {
        if (adyeshachVisibleListener != null) {
            try {
                HandlerList.unregisterAll(adyeshachVisibleListener);
            } catch (RuntimeException ignored) {
            }
            adyeshachVisibleListener = null;
        }
        adyeshachVisibleHandler = null;
        adyVisibleEventGetEntityMethod = null;
        adyVisibleEventGetViewerMethod = null;
        adyVisibleEventGetVisibleMethod = null;
    }

    private void dispatchAdyeshachVisibleEvent(Event event) {
        BiConsumer<Player, Object> handler = adyeshachVisibleHandler;
        if (handler == null || event == null) {
            return;
        }
        try {
            Object visibleResult = adyVisibleEventGetVisibleMethod.invoke(event);
            if (!(visibleResult instanceof Boolean visible) || !visible) {
                return;
            }
            Object entity = adyVisibleEventGetEntityMethod.invoke(event);
            Object viewerObj = adyVisibleEventGetViewerMethod.invoke(event);
            if (!(viewerObj instanceof Player viewer) || entity == null) {
                return;
            }
            handler.accept(viewer, entity);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (debug) {
                plugin.getLogger().warning("AdyeshachNpcBridge: 处理 AdyeshachEntityVisibleEvent 失败 - "
                    + describeException(exception));
            }
        }
    }

    // ======================== 私有临时实体（导航标记）========================

    /**
     * 为指定玩家在目标位置生成一个私有临时 Adyeshach 实体作为导航标记载体。
     * 实体仅对该玩家可见，不产生服务端 tick 开销。
     *
     * @param player   目标玩家
     * @param markerId 标记唯一 ID（用于后续删除）
     * @param location 目标位置
     * @return 成功返回生成的 Adyeshach 实体对象，失败返回 null
     */
    @Override
    public Object spawnPrivateMarker(Player player, String markerId, Location location) {
        if (!available || player == null || markerId == null || location == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: spawnPrivateMarker 前置检查失败 available=" + available);
            return null;
        }
        ensurePrivateEntityApi();
        if (getPrivateTempManagerMethod == null || playerEntityType == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: spawnPrivateMarker API 未就绪 manager=" 
                + (getPrivateTempManagerMethod != null) + " entityType=" + (playerEntityType != null));
            return null;
        }
        try {
            Object privateTempManager = getPrivateTempManagerMethod.invoke(apiInstance, player);
            if (privateTempManager == null) {
                plugin.getLogger().warning("AdyeshachNpcBridge: getPrivateTempManager 返回 null, player=" + player.getName());
                return null;
            }
            // 延迟查找 create 方法（初始化时可能因无在线玩家而无法获取 manager 实例）
            if (managerCreateMethod == null) {
                managerCreateMethod = findCreateMethod(privateTempManager);
                if (managerCreateMethod == null) {
                    plugin.getLogger().warning("AdyeshachNpcBridge: 无法在 " + privateTempManager.getClass().getName()
                        + " 上找到 create(EntityTypes, Location) 方法");
                    return null;
                }
                if (debug) plugin.getLogger().info("AdyeshachNpcBridge: 延迟发现 create 方法: " + managerCreateMethod);
            }
            if (debug) plugin.getLogger().info("AdyeshachNpcBridge: spawnPrivateMarker manager=" 
                + privateTempManager.getClass().getSimpleName() + " entityType=" + playerEntityType 
                + " loc=" + location);
            Object entity = managerCreateMethod.invoke(privateTempManager, playerEntityType, location);
            if (entity == null) {
                plugin.getLogger().warning("AdyeshachNpcBridge: manager.create() 返回 null");
                return null;
            }
            if (debug) plugin.getLogger().info("AdyeshachNpcBridge: 实体已创建 class=" + entity.getClass().getSimpleName());
            // 设置隐身（隐藏原版实体模型）
            try {
                if (entitySetVisibleMethod == null) {
                    entitySetVisibleMethod = findMethod(entity.getClass(), "setVisible", boolean.class);
                }
                if (entitySetVisibleMethod != null) {
                    resolveCompatibleMethod(entitySetVisibleMethod, entity).invoke(entity, false);
                }
            } catch (Exception ignored) {
            }
            // 隐藏名称标签
            try {
                // 设置空名称
                Method setNameMethod = findMethod(entity.getClass(), "setCustomName", String.class);
                if (setNameMethod == null) {
                    setNameMethod = findMethod(entity.getClass(), "setName", String.class);
                }
                if (setNameMethod != null) {
                    resolveCompatibleMethod(setNameMethod, entity).invoke(entity, "");
                }
                // 隐藏名称可见性
                Method setNameVisibleMethod = findMethod(entity.getClass(), "setCustomNameVisible", boolean.class);
                if (setNameVisibleMethod == null) {
                    setNameVisibleMethod = findMethod(entity.getClass(), "setNameTagVisible", boolean.class);
                }
                if (setNameVisibleMethod == null) {
                    setNameVisibleMethod = findMethod(entity.getClass(), "setNameVisible", boolean.class);
                }
                if (setNameVisibleMethod != null) {
                    resolveCompatibleMethod(setNameVisibleMethod, entity).invoke(entity, false);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("AdyeshachNpcBridge: 隐藏名称失败 - " + e.getMessage());
            }
            // 设置无碰撞元数据（ArmorStand marker 模式：无碰撞体积）
            try {
                if (entitySetMetaMethod == null) {
                    entitySetMetaMethod = findMethod(entity.getClass(), "setMeta", String.class, Object.class);
                }
                if (entitySetMetaMethod != null) {
                    Method m = resolveCompatibleMethod(entitySetMetaMethod, entity);
                    if (m != null) {
                        m.invoke(entity, "isSmall", true);
                        m.invoke(entity, "isMarker", true);
                        m.invoke(entity, "isNoGravity", true);
                    }
                }
            } catch (Exception ignored) {
            }
            // 尝试通过 setCollision/setCollidable 关闭碰撞
            try {
                Method setCollidableMethod = findMethod(entity.getClass(), "setCollidable", boolean.class);
                if (setCollidableMethod != null) {
                    resolveCompatibleMethod(setCollidableMethod, entity).invoke(entity, false);
                }
            } catch (Exception ignored) {
            }
            privateMarkerEntities.put(markerKey(player, markerId), entity);
            return entity;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: spawnPrivateMarker 失败 - " + describeException(exception));
            return null;
        }
    }

    /**
     * 传送指定玩家的导航标记实体到新位置。
     */
    @Override
    public boolean teleportMarker(Player player, String markerId, Location location) {
        if (player == null || markerId == null || location == null) {
            return false;
        }
        Object entity = privateMarkerEntities.get(markerKey(player, markerId));
        if (entity == null) {
            return false;
        }
        try {
            if (entityTeleportMethod == null) {
                entityTeleportMethod = findMethod(entity.getClass(), "teleport", Location.class);
            }
            if (entityTeleportMethod != null) {
                resolveCompatibleMethod(entityTeleportMethod, entity).invoke(entity, location);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: teleportMarker 失败 - " + describeException(exception));
        }
        return false;
    }

    /**
     * 移除指定玩家的导航标记实体。
     */
    @Override
    public boolean removePrivateMarker(Player player, String markerId) {
        if (player == null || markerId == null) {
            return false;
        }
        Object entity = privateMarkerEntities.remove(markerKey(player, markerId));
        if (entity == null) {
            return false;
        }
        return deleteEntity(entity);
    }

    /**
     * 清除所有私有标记实体（shutdown 时调用）。
     */
    @Override
    public void clearAllPrivateMarkers() {
        for (Object entity : privateMarkerEntities.values()) {
            deleteEntity(entity);
        }
        privateMarkerEntities.clear();
    }

    /**
     * 获取已生成的私有标记实体（用于后续 applyModel 调用）。
     */
    @Override
    public Object getPrivateMarker(Player player, String markerId) {
        if (player == null || markerId == null) {
            return null;
        }
        return privateMarkerEntities.get(markerKey(player, markerId));
    }

    private boolean deleteEntity(Object entity) {
        if (entity == null) {
            return false;
        }
        try {
            if (entityDeleteMethod == null) {
                entityDeleteMethod = findMethod(entity.getClass(), "delete");
                if (entityDeleteMethod == null) {
                    entityDeleteMethod = findMethod(entity.getClass(), "remove");
                }
            }
            if (entityDeleteMethod != null) {
                Method m = resolveCompatibleMethod(entityDeleteMethod, entity);
                if (m != null) {
                    m.invoke(entity);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: deleteEntity 失败 - " + describeException(exception));
        }
        return false;
    }

    private void ensurePrivateEntityApi() {
        if (getPrivateTempManagerMethod != null) {
            return;
        }
        Plugin adyeshach = plugin.getServer().getPluginManager().getPlugin("Adyeshach");
        if (adyeshach == null) {
            return;
        }
        try {
            ClassLoader classLoader = adyeshach.getClass().getClassLoader();
            Class<?> adyApiClass = Class.forName(ADY_API_CLASS, true, classLoader);
            Object adyApiInstance = adyApiClass.getField("INSTANCE").get(null);

            getPrivateTempManagerMethod = findMethod(adyApiClass, "getEntityManagerPrivateTemporary", Player.class);
            if (getPrivateTempManagerMethod == null) {
                plugin.getLogger().warning("AdyeshachNpcBridge: 未找到 getEntityManagerPrivateTemporary(Player)");
                return;
            }

            // 解析 EntityTypes.ARMOR_STAND
            Class<?> entityTypesClass = Class.forName(ADY_ENTITY_TYPES_CLASS, true, classLoader);
            playerEntityType = Enum.valueOf((Class<Enum>) entityTypesClass, "ARMOR_STAND");

            // 解析 Manager.create(EntityTypes, Location)
            // 先尝试获取 manager 实例以获取其 class
            Object tempManager = getPrivateTempManagerMethod.invoke(adyApiInstance,
                plugin.getServer().getOnlinePlayers().isEmpty() ? null : plugin.getServer().getOnlinePlayers().iterator().next());
            if (tempManager != null) {
                Class<?> actualEntityTypesClass = playerEntityType instanceof Enum<?> e
                    ? e.getDeclaringClass() : entityTypesClass;
                managerCreateMethod = findMethod(tempManager.getClass(), "create", actualEntityTypesClass, Location.class);
            }
            if (managerCreateMethod == null) {
                // 退而求其次：从接口层查找
                Class<?> actualEntityTypesClass = playerEntityType instanceof Enum<?> e
                    ? e.getDeclaringClass() : entityTypesClass;
                for (Class<?> iface : getAllInterfaces(adyApiClass)) {
                    managerCreateMethod = findMethod(iface, "create", actualEntityTypesClass, Location.class);
                    if (managerCreateMethod != null) break;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: 初始化私有实体 API 失败 - " + describeException(exception));
            getPrivateTempManagerMethod = null;
        }
    }

    /**
     * 从 manager 实例的类层次结构（含接口、父类）中查找 create(EntityTypes, Location) 方法。
     */
    @SuppressWarnings("unchecked")
    private Method findCreateMethod(Object manager) {
        // 宽松匹配: 按方法名 "create" + 恰好 2 个参数 + 第二个参数为 Location 来查找
        Method bestMatch = null;
        for (Method method : manager.getClass().getMethods()) {
            if (!method.getName().equals("create") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[1] == Location.class || params[1].getName().equals("org.bukkit.Location")) {
                bestMatch = method;
                break;
            }
        }
        if (bestMatch != null) {
            if (debug) plugin.getLogger().info("AdyeshachNpcBridge: 找到 create 方法: " + bestMatch
                + " param0=" + bestMatch.getParameterTypes()[0].getName()
                + " param1=" + bestMatch.getParameterTypes()[1].getName());
            // 用方法参数的 EntityTypes 类重新创建枚举值，确保 classloader 一致
            Class<?> methodEntityTypesClass = bestMatch.getParameterTypes()[0];
            if (methodEntityTypesClass.isEnum()) {
                try {
                    playerEntityType = Enum.valueOf((Class<Enum>) methodEntityTypesClass, "ARMOR_STAND");
                    if (debug) plugin.getLogger().info("AdyeshachNpcBridge: 用方法参数类型重建 ARMOR_STAND 枚举值成功"
                        + " class=" + methodEntityTypesClass.getName());
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("AdyeshachNpcBridge: 无法在 " 
                        + methodEntityTypesClass.getName() + " 中找到 ARMOR_STAND 枚举值");
                }
            }
        } else {
            plugin.getLogger().warning("AdyeshachNpcBridge: manager 上找不到 create(?, Location) 方法");
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getDeclaringClass().equals(Object.class)) {
                    plugin.getLogger().warning("  - " + method.getName() + "("
                        + java.util.Arrays.stream(method.getParameterTypes())
                            .map(Class::getSimpleName).collect(java.util.stream.Collectors.joining(", ")) + ")");
                }
            }
        }
        return bestMatch;
    }

    private static String markerKey(Player player, String markerId) {
        return player.getUniqueId().toString() + ":" + markerId;
    }

    private static List<Class<?>> getAllInterfaces(Class<?> type) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                result.add(iface);
            }
        }
        return result;
    }

    // ======================== ArcartX 模型 ========================

    /**
     * 通过 ArcartXEntityManager 为 Adyeshach NPC 所关联的 Bukkit Entity 调用
     * ArcartXEntity.setModel(modelID, scale)。
     *
     * @param adyeshachEntity findByName 返回的原始 Adyeshach 实体对象
     * @param modelId         模型 ID
     * @param scale           缩放比例
     * @return 成功返回 true
     */
    @Override
    public boolean applyModel(Object adyeshachEntity, String modelId, double scale) {
        if (!available || adyeshachEntity == null || modelId == null || modelId.isBlank()) {
            return false;
        }
        // 路径 1: 通过 ArcartXEntityManager（仅当实体附带真实 Bukkit Entity 时有效）
        ensureArcartXEntityBridge();
        Object axEntity = getOrCreateAxEntity(adyeshachEntity);
        if (axEntity != null) {
            if (axEntitySetModelMethod == null) {
                axEntitySetModelMethod = findMethod(axEntity.getClass(), "setModel", String.class, double.class);
            }
            if (axEntitySetModelMethod != null) {
                try {
                    invokeOrThrow(axEntitySetModelMethod, axEntity, modelId, scale);
                    return true;
                } catch (Exception exception) {
                    plugin.getLogger().warning("AdyeshachNpcBridge: setModel(Bukkit Entity 路径) 失败 - " + exception.getMessage());
                }
            }
        }
        // 路径 2: NetworkSender 广播路径（Adyeshach 虚拟实体使用此路径）
        return broadcastApplyModel(adyeshachEntity, modelId, scale);
    }

    /**
     * 通过 ArcartXNetworkSender 对所有看见该实体的在线玩家广播 sendSetEntityModel。
     * 这是 Adyeshach 虚拟实体（无真实 Bukkit Entity）应用模型的唯一可行路径。
     */
    private boolean broadcastApplyModel(Object adyeshachEntity, String modelId, double scale) {
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: 无法获取 Adyeshach 实体 UUID，无法广播模型。");
            return false;
        }
        ensureNetworkSenderBridge();
        if (networkSendSetEntityModelMethod == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: ArcartXNetworkSender.sendSetEntityModel 不可用，无法应用模型。");
            return false;
        }
        List<Player> viewers = collectVisibleViewers(adyeshachEntity);
        if (viewers.isEmpty()) {
            if (debug) plugin.getLogger().info("AdyeshachNpcBridge: broadcastApplyModel UUID=" + entityUUID
                + " 当前没有看见该实体的在线玩家，跳过广播。");
            // 没有可视玩家不算失败：reload 时可能尚无玩家在视野内
            return true;
        }
        int success = 0;
        for (Player player : viewers) {
            try {
                networkSendSetEntityModelMethod.invoke(arcartXNetworkSender, player, entityUUID, modelId, scale);
                success++;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("AdyeshachNpcBridge: sendSetEntityModel(" + player.getName()
                    + ") 失败 - " + describeException(exception));
            }
        }
        if (debug) plugin.getLogger().info("AdyeshachNpcBridge: broadcastApplyModel UUID=" + entityUUID
            + " model=" + modelId + " success=" + success + "/" + viewers.size());
        return success > 0;
    }

    /**
     * 为私有临时实体通过 ArcartXNetworkSender 点对点应用模型。
     * 此方法适用于没有真实 Bukkit Entity 的 Adyeshach 私有实体。
     *
     * @param player          目标玩家（模型只对该玩家可见）
     * @param adyeshachEntity Adyeshach 私有临时实体
     * @param modelId         模型 ID
     * @param scale           缩放比例
     * @return 成功返回 true
     */
    @Override
    public boolean applyModelForPlayer(Player player, Object adyeshachEntity, String modelId, double scale) {
        if (!available || player == null || adyeshachEntity == null || modelId == null || modelId.isBlank()) {
            plugin.getLogger().warning("AdyeshachNpcBridge: applyModelForPlayer 参数无效 available=" + available
                + " entity=" + (adyeshachEntity != null) + " model=" + modelId);
            return false;
        }
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: applyModelForPlayer - 无法获取实体 UUID，回退到标准路径");
            return applyModel(adyeshachEntity, modelId, scale);
        }
        if (debug) plugin.getLogger().info("AdyeshachNpcBridge: applyModelForPlayer UUID=" + entityUUID
            + " model=" + modelId + " scale=" + scale);
        ensureNetworkSenderBridge();
        if (networkSendSetEntityModelMethod == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: NetworkSender.sendSetEntityModel 方法未找到，回退到标准路径");
            return applyModel(adyeshachEntity, modelId, scale);
        }
        try {
            if (debug) plugin.getLogger().info("AdyeshachNpcBridge: 调用 sendSetEntityModel player=" + player.getName()
                + " UUID=" + entityUUID + " model=" + modelId);
            networkSendSetEntityModelMethod.invoke(arcartXNetworkSender, player, entityUUID, modelId, scale);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: applyModelForPlayer 失败 - " + describeException(exception));
            return false;
        }
    }

    /**
     * 为私有临时实体通过 ArcartXNetworkSender 点对点播放动画。
     */
    @Override
    public boolean applyAnimationForPlayer(Player player, Object adyeshachEntity, String animation, double speed, int transitionTime, long keepTime) {
        if (!available || player == null || adyeshachEntity == null || animation == null || animation.isBlank()) {
            return false;
        }
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            return applyAnimation(adyeshachEntity, animation, speed, transitionTime, keepTime);
        }
        ensureNetworkSenderBridge();
        if (networkSendSetEntityAnimationMethod == null) {
            return applyAnimation(adyeshachEntity, animation, speed, transitionTime, keepTime);
        }
        try {
            networkSendSetEntityAnimationMethod.invoke(arcartXNetworkSender, player, entityUUID, animation, speed, transitionTime, keepTime);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: applyAnimationForPlayer 失败 - " + describeException(exception));
            return false;
        }
    }

    /**
     * 为私有临时实体设置默认状态。
     * NetworkSender 无 setDefaultState 方法，使用 sendSetEntityAnimation 以 -1 keepTime 模拟。
     */
    @Override
    public boolean applyDefaultStateForPlayer(Player player, Object adyeshachEntity, String state, String animName) {
        if (!available || player == null || adyeshachEntity == null || state == null || animName == null) {
            return false;
        }
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            return applyDefaultState(adyeshachEntity, state, animName);
        }
        ensureNetworkSenderBridge();
        if (networkSendSetEntityAnimationMethod == null) {
            return applyDefaultState(adyeshachEntity, state, animName);
        }
        try {
            // 使用 animation 接口设置持续动画作为默认状态（keepTime=-1 表示无限）
            networkSendSetEntityAnimationMethod.invoke(arcartXNetworkSender, player, entityUUID, animName, 1.0, 0, -1L);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: applyDefaultStateForPlayer 失败 - " + describeException(exception));
            return false;
        }
    }

    /**
     * 初始化 ArcartXNetworkSender 桥接。
     * 通过 ArcartXAPI.getNetworkSender() 获取实例，用于向单个玩家发送模型/动画包。
     */
    private void ensureNetworkSenderBridge() {
        if (networkSenderBridgeAttempted) {
            return;
        }
        networkSenderBridgeAttempted = true;

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return;
        }
        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(ARCARTX_API_CLASS, true, classLoader);

            // ArcartXAPI.getNetworkSender()
            Method getNetworkSenderMethod = findMethod(apiClass, "getNetworkSender");
            if (getNetworkSenderMethod == null) {
                plugin.getLogger().warning("AdyeshachNpcBridge: ArcartXAPI 未提供 getNetworkSender() 方法。");
                return;
            }
            arcartXNetworkSender = getNetworkSenderMethod.invoke(null);
            if (arcartXNetworkSender == null) {
                plugin.getLogger().warning("AdyeshachNpcBridge: ArcartXAPI.getNetworkSender() 返回 null。");
                return;
            }

            Class<?> senderClass = arcartXNetworkSender.getClass();

            // sendSetEntityModel(Player player, UUID entity, String modelId, double scale)
            networkSendSetEntityModelMethod = findMethod(senderClass, "sendSetEntityModel",
                Player.class, UUID.class, String.class, double.class);

            // sendSetEntityAnimation(Player player, UUID entity, String animation, double speed, int transitionTime, long keepTime)
            networkSendSetEntityAnimationMethod = findMethod(senderClass, "sendSetEntityAnimation",
                Player.class, UUID.class, String.class, double.class, int.class, long.class);
            if (networkSendSetEntityAnimationMethod == null) {
                // 尝试别名
                networkSendSetEntityAnimationMethod = findMethod(senderClass, "sendPlayEntityAnimation",
                    Player.class, UUID.class, String.class, double.class, int.class, long.class);
            }

            if (networkSendSetEntityModelMethod != null) {
                plugin.getLogger().fine("AdyeshachNpcBridge: NetworkSender bridge 已就绪 (sendSetEntityModel)");
            } else {
                plugin.getLogger().warning("AdyeshachNpcBridge: ArcartXNetworkSender 未提供 sendSetEntityModel(Player,UUID,String,double) 方法。");
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: 初始化 NetworkSender bridge 失败 - " + describeException(exception));
        }
    }

    /**
     * 通过 ArcartXEntityManager 为 Adyeshach NPC 调用
     * ArcartXEntity.playAnimation(animation, speed, transitionTime, keepTime)。
     *
     * @param adyeshachEntity findByName 返回的原始 Adyeshach 实体对象
     * @param animation       动画名称
     * @param speed           播放速度（建议 1.0）
     * @param transitionTime  过渡时间（毫秒）
     * @param keepTime        持续时间（毫秒），-1 表示播放完整动画
     * @return 成功返回 true
     */
    @Override
    public boolean applyAnimation(Object adyeshachEntity, String animation, double speed, int transitionTime, long keepTime) {
        if (!available || adyeshachEntity == null || animation == null || animation.isBlank()) {
            return false;
        }
        // 路径 1: 通过 ArcartXEntityManager（仅当实体附带真实 Bukkit Entity 时有效）
        ensureArcartXEntityBridge();
        Object axEntity = getOrCreateAxEntity(adyeshachEntity);
        if (axEntity != null) {
            if (axEntityPlayAnimationMethod == null) {
                axEntityPlayAnimationMethod = findMethod(axEntity.getClass(), "playAnimation",
                    String.class, double.class, int.class, long.class);
            }
            if (axEntityPlayAnimationMethod != null) {
                try {
                    invokeOrThrow(axEntityPlayAnimationMethod, axEntity, animation, speed, transitionTime, keepTime);
                    return true;
                } catch (Exception exception) {
                    plugin.getLogger().warning("AdyeshachNpcBridge: playAnimation(Bukkit Entity 路径) 失败 - " + exception.getMessage());
                }
            }
        }
        // 路径 2: NetworkSender 广播路径
        return broadcastApplyAnimation(adyeshachEntity, animation, speed, transitionTime, keepTime);
    }

    private boolean broadcastApplyAnimation(Object adyeshachEntity, String animation, double speed, int transitionTime, long keepTime) {
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: 无法获取 Adyeshach 实体 UUID，无法广播动画。");
            return false;
        }
        ensureNetworkSenderBridge();
        if (networkSendSetEntityAnimationMethod == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: ArcartXNetworkSender.sendSetEntityAnimation 不可用，无法播放动画。");
            return false;
        }
        List<Player> viewers = collectVisibleViewers(adyeshachEntity);
        if (viewers.isEmpty()) {
            if (debug) plugin.getLogger().info("AdyeshachNpcBridge: broadcastApplyAnimation UUID=" + entityUUID
                + " 当前没有看见该实体的在线玩家，跳过广播。");
            return true;
        }
        int success = 0;
        for (Player player : viewers) {
            try {
                networkSendSetEntityAnimationMethod.invoke(arcartXNetworkSender, player, entityUUID,
                    animation, speed, transitionTime, keepTime);
                success++;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("AdyeshachNpcBridge: sendSetEntityAnimation(" + player.getName()
                    + ") 失败 - " + describeException(exception));
            }
        }
        if (debug) plugin.getLogger().info("AdyeshachNpcBridge: broadcastApplyAnimation UUID=" + entityUUID
            + " animation=" + animation + " success=" + success + "/" + viewers.size());
        return success > 0;
    }

    /**
     * 通过 ArcartXEntityManager 为 Adyeshach NPC 调用
     * ArcartXEntity.setDefaultState(state, animName)。
     *
     * @param adyeshachEntity findByName 返回的原始 Adyeshach 实体对象
     * @param state           状态名称（如 "idle"）
     * @param animName        动画名称
     * @return 成功返回 true
     */
    @Override
    public boolean applyDefaultState(Object adyeshachEntity, String state, String animName) {
        if (!available || adyeshachEntity == null || state == null || animName == null) {
            return false;
        }
        // 路径 1: 通过 ArcartXEntityManager（仅当实体附带真实 Bukkit Entity 时有效）
        ensureArcartXEntityBridge();
        Object axEntity = getOrCreateAxEntity(adyeshachEntity);
        if (axEntity != null) {
            if (axEntitySetDefaultStateMethod == null) {
                axEntitySetDefaultStateMethod = findMethod(axEntity.getClass(), "setDefaultState", String.class, String.class);
            }
            if (axEntitySetDefaultStateMethod != null) {
                try {
                    invokeOrThrow(axEntitySetDefaultStateMethod, axEntity, state, animName);
                    return true;
                } catch (Exception exception) {
                    plugin.getLogger().warning("AdyeshachNpcBridge: setDefaultState(Bukkit Entity 路径) 失败 - " + exception.getMessage());
                }
            }
        }
        // 路径 2: NetworkSender 广播路径（使用 sendSetEntityAnimation 模拟持续动画作为默认状态，keepTime=-1）
        return broadcastApplyDefaultState(adyeshachEntity, state, animName);
    }

    private boolean broadcastApplyDefaultState(Object adyeshachEntity, String state, String animName) {
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: 无法获取 Adyeshach 实体 UUID，无法广播默认状态。");
            return false;
        }
        ensureNetworkSenderBridge();
        if (networkSendSetEntityAnimationMethod == null) {
            plugin.getLogger().warning("AdyeshachNpcBridge: ArcartXNetworkSender.sendSetEntityAnimation 不可用，无法应用默认状态。");
            return false;
        }
        List<Player> viewers = collectVisibleViewers(adyeshachEntity);
        if (viewers.isEmpty()) {
            if (debug) plugin.getLogger().info("AdyeshachNpcBridge: broadcastApplyDefaultState UUID=" + entityUUID
                + " 当前没有看见该实体的在线玩家，跳过广播。");
            return true;
        }
        int success = 0;
        for (Player player : viewers) {
            try {
                // keepTime=-1 表示无限循环，模拟默认状态
                networkSendSetEntityAnimationMethod.invoke(arcartXNetworkSender, player, entityUUID,
                    animName, 1.0, 0, -1L);
                success++;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("AdyeshachNpcBridge: sendSetEntityAnimation(default state, "
                    + player.getName() + ") 失败 - " + describeException(exception));
            }
        }
        if (debug) plugin.getLogger().info("AdyeshachNpcBridge: broadcastApplyDefaultState UUID=" + entityUUID
            + " state=" + state + " anim=" + animName + " success=" + success + "/" + viewers.size());
        return success > 0;
    }

    private void ensureArcartXEntityBridge() {
        if (arcartXEntityManager != null && getOrCreateEntityMethod != null) {
            return;
        }
        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return;
        }
        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(ARCARTX_API_CLASS, true, classLoader);
            Class<?> entityManagerClass = Class.forName(ARCARTX_ENTITY_MANAGER_CLASS, true, classLoader);
            arcartXEntityManager = apiClass.getMethod(ARCARTX_GET_ENTITY_MANAGER).invoke(null);
            getOrCreateEntityMethod = findMethod(entityManagerClass, ARCARTX_GET_OR_CREATE_ENTITY, Entity.class);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("AdyeshachNpcBridge: 初始化 ArcartXEntityManager 失败 - " + exception.getMessage());
        }
    }

    /**
     * 仅在 Adyeshach 实体附带真实 Bukkit Entity 时（极少数情况）通过 ArcartXEntityManager 创建 ArcartXEntity。
     * 绝大多数 Adyeshach NPC 是基于协议包的虚拟实体（无真实 Bukkit Entity），返回 null 后由调用方走 NetworkSender 广播路径。
     */
    private Object getOrCreateAxEntity(Object adyeshachEntity) {
        if (arcartXEntityManager == null || getOrCreateEntityMethod == null) {
            return null;
        }
        EntityAccessors accessors = accessorsFor(adyeshachEntity);
        if (accessors == null) {
            return null;
        }
        Entity bukkitEntity = accessors.getBukkitEntity(adyeshachEntity);
        if (bukkitEntity == null) {
            return null;
        }
        return invokeCompatible(getOrCreateEntityMethod, arcartXEntityManager, bukkitEntity);
    }

    /**
     * 收集当前能看见该 Adyeshach 实体的在线玩家列表。
     * 若实体未提供 isVisibleViewer(Player) 方法，则回退为所有在线玩家。
     */
    private List<Player> collectVisibleViewers(Object adyeshachEntity) {
        List<Player> viewers = new ArrayList<>();
        EntityAccessors accessors = adyeshachEntity == null ? null : accessorsFor(adyeshachEntity);
        Method visibleViewerMethod = accessors == null ? null : accessors.visibleViewerMethod();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (visibleViewerMethod == null
                || invokeBoolean(visibleViewerMethod, adyeshachEntity, "isVisibleViewer", player)) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    /**
     * 获取 Adyeshach 实体的 UUID。
     * Adyeshach DefaultEntityBase 中 uniqueId 是字符串（32位无连字符），normalizeUniqueId 是 UUID 对象。
     * 尝试 getNormalizeUniqueId() → UUID，或 getUniqueId() → String → UUID。
     *
     * @return 实体 UUID，失败返回 null
     */
    private UUID getAdyEntityUUID(Object adyeshachEntity) {
        if (adyeshachEntity == null) {
            return null;
        }
        // 优先尝试 getNormalizeUniqueId() 返回 UUID
        Method normalizeMethod = findMethod(adyeshachEntity.getClass(), "getNormalizeUniqueId");
        if (normalizeMethod != null) {
            try {
                Object result = normalizeMethod.invoke(adyeshachEntity);
                if (result instanceof UUID uuid) {
                    return uuid;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        // 回退尝试 getUniqueId() 返回 String
        Method uniqueIdMethod = findMethod(adyeshachEntity.getClass(), "getUniqueId");
        if (uniqueIdMethod != null) {
            try {
                Object result = uniqueIdMethod.invoke(adyeshachEntity);
                if (result instanceof UUID uuid) {
                    return uuid;
                }
                if (result instanceof String str && !str.isBlank()) {
                    // Adyeshach 存储的 uniqueId 可能是 32位无连字符的格式
                    if (str.contains("-")) {
                        return UUID.fromString(str);
                    } else if (str.length() == 32) {
                        String formatted = str.substring(0, 8) + "-" + str.substring(8, 12) + "-"
                            + str.substring(12, 16) + "-" + str.substring(16, 20) + "-" + str.substring(20);
                        return UUID.fromString(formatted);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        plugin.getLogger().warning("AdyeshachNpcBridge: 无法获取实体 UUID (class=" + adyeshachEntity.getClass().getName() + ")");
        return null;
    }

    private xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc resolveNearbyNpc(
        Object entity,
        Player player,
        World playerWorld,
        Location playerLocation,
        double rangeSquared
    ) {
        if (entity == null) {
            return null;
        }

        EntityAccessors accessors = accessorsFor(entity);
        if (accessors == null || !accessors.valid()) {
            return null;
        }
        if (isFiltered(accessors, entity, player)) {
            return null;
        }

        Location location = invokeLocation(accessors, entity);
        if (location == null || location.getWorld() == null || !playerWorld.equals(location.getWorld())) {
            return null;
        }

        double distanceSquared = playerLocation.distanceSquared(location);
        if (distanceSquared > rangeSquared) {
            return null;
        }

        String npcId = invokeString(accessors.idMethod(), entity, "getId");
        if (npcId.isBlank()) {
            return null;
        }

        String label = firstNonBlank(
            invokeString(accessors.displayNameMethod(), entity, "getDisplayName"),
            invokeString(accessors.customNameMethod(), entity, "getCustomName"),
            npcId
        );
        Object conversationEntity = invokeEntityHandle(accessors, entity);
        if (conversationEntity == null) {
            return null;
        }

        return new xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc(npcId, label, location.clone(), entity, conversationEntity, distanceSquared);
    }

    @SuppressWarnings("unchecked")
    private List<?> invokeEntities() {
        if (!available || publicManager == null || getEntitiesMethod == null) {
            return List.of();
        }
        try {
            Object result = invokeCompatible(getEntitiesMethod, publicManager);
            return result instanceof List<?> list ? list : List.of();
        } catch (RuntimeException exception) {
            warnReflectionFailure("getEntities", getEntitiesMethod, publicManager, exception);
            return List.of();
        }
    }

    private EntityAccessors accessorsFor(Object entity) {
        EntityAccessors accessors = entityAccessorsByClass.computeIfAbsent(entity.getClass(), EntityAccessors::resolve);
        if (!accessors.valid()) {
            warnReflectionFailure("resolve-accessors", null, entity, new NoSuchMethodException(accessors.missingRequiredMethods()));
            return null;
        }
        return accessors;
    }

    private boolean isFiltered(EntityAccessors accessors, Object entity, Player player) {
        if (invokeBoolean(accessors.deletedMethod(), entity, "isDeleted")) {
            return true;
        }
        if (invokeBoolean(accessors.invalidMethod(), entity, "getInvalid")) {
            return true;
        }
        return accessors.visibleViewerMethod() != null && !invokeBoolean(accessors.visibleViewerMethod(), entity, "isVisibleViewer", player);
    }

    private Object invokeEntityHandle(EntityAccessors accessors, Object entity) {
        if (accessors.v2Method() == null) {
            return entity;
        }
        return invokeObject(accessors.v2Method(), entity, "getV2");
    }

    private Location invokeLocation(EntityAccessors accessors, Object entity) {
        Object result = invokeObject(accessors.locationMethod(), entity, "getLocation");
        return result instanceof Location location ? location : null;
    }

    private boolean invokeBoolean(Method method, Object target, String action, Object... arguments) {
        Object result = invokeObject(method, target, action, arguments);
        return result instanceof Boolean booleanResult && booleanResult.booleanValue();
    }

    private String invokeString(Method method, Object target, String action) {
        Object result = invokeObject(method, target, action);
        return result == null ? "" : String.valueOf(result).trim();
    }

    private Object invokeObject(Method method, Object target, String action, Object... arguments) {
        if (method == null || target == null) {
            return null;
        }
        Method actualMethod = resolveCompatibleMethod(method, target);
        if (actualMethod == null) {
            warnReflectionFailure(
                action,
                method,
                target,
                new IllegalArgumentException("object is not an instance of declaring class")
            );
            return null;
        }
        try {
            return actualMethod.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnReflectionFailure(action, actualMethod, target, exception);
            return null;
        }
    }

    static void invokeOrThrow(Method method, Object target, Object... arguments) throws ReflectiveOperationException {
        Method actualMethod = resolveCompatibleMethod(method, target);
        if (actualMethod == null) {
            throw new NoSuchMethodException(
                "No compatible method " + (method == null ? "null" : method.getName())
                    + " on " + (target == null ? "null" : target.getClass().getName()));
        }
        actualMethod.invoke(target, arguments);
    }

    static Object invokeCompatible(Method method, Object target, Object... arguments) {
        Method actualMethod = resolveCompatibleMethod(method, target);
        if (actualMethod == null) {
            return null;
        }
        try {
            return actualMethod.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    static Method resolveCompatibleMethod(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        if (method.getDeclaringClass().isInstance(target)) {
            return method;
        }
        Method resolved = findMethod(target.getClass(), method.getName(), method.getParameterTypes());
        if (resolved == null || !resolved.getDeclaringClass().isInstance(target)) {
            return null;
        }
        return resolved;
    }

    private void warnReflectionFailure(String action, Method method, Object target, Exception exception) {
        long now = System.currentTimeMillis();
        if (now < nextReflectionWarningAtMs) {
            return;
        }
        nextReflectionWarningAtMs = now + REFLECTION_WARNING_INTERVAL_MS;
        plugin.getLogger().warning(
            "Adyeshach NPC 反射失败("
                + action
                + "): "
                + describeException(exception)
                + " | method="
                + describeMethod(method)
                + " | target="
                + describeTarget(target)
        );
    }

    private static Method findRequiredMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = findMethod(type, name, parameterTypes);
        if (method != null) {
            return method;
        }
        throw new NoSuchMethodException("No compatible method " + name + " on " + type.getName());
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (parameterTypes.length == 0) {
                    return method;
                }
                Class<?>[] actual = method.getParameterTypes();
                if (actual.length != parameterTypes.length) {
                    continue;
                }
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                String normalized = value.trim();
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }
        return "";
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

    private static String describeMethod(Method method) {
        if (method == null) {
            return "null";
        }
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private static String describeTarget(Object target) {
        return target == null ? "null" : target.getClass().getName();
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

    static final class EntityAccessors {

        private final Class<?> entityType;
        private final Method idMethod;
        private final Method locationMethod;
        private final Method displayNameMethod;
        private final Method customNameMethod;
        private final Method v2Method;
        private final Method deletedMethod;
        private final Method invalidMethod;
        private final Method visibleViewerMethod;

        private EntityAccessors(
            Class<?> entityType,
            Method idMethod,
            Method locationMethod,
            Method displayNameMethod,
            Method customNameMethod,
            Method v2Method,
            Method deletedMethod,
            Method invalidMethod,
            Method visibleViewerMethod
        ) {
            this.entityType = entityType;
            this.idMethod = idMethod;
            this.locationMethod = locationMethod;
            this.displayNameMethod = displayNameMethod;
            this.customNameMethod = customNameMethod;
            this.v2Method = v2Method;
            this.deletedMethod = deletedMethod;
            this.invalidMethod = invalidMethod;
            this.visibleViewerMethod = visibleViewerMethod;
        }

        static EntityAccessors resolve(Class<?> entityType) {
            return new EntityAccessors(
                entityType,
                findMethod(entityType, "getId"),
                findMethod(entityType, "getLocation"),
                findMethod(entityType, "getDisplayName"),
                findMethod(entityType, "getCustomName"),
                findMethod(entityType, "getV2"),
                findMethod(entityType, "isDeleted"),
                findMethod(entityType, "getInvalid"),
                findMethod(entityType, "isVisibleViewer", Player.class)
            );
        }

        /**
         * 尝试通过 getEntity() / getBukkitEntity() 获取 Adyeshach 实体关联的 Bukkit Entity。
         */
        Entity getBukkitEntity(Object adyeshachEntity) {
            for (String methodName : new String[]{"getEntity", "getBukkitEntity"}) {
                Method m = findMethod(entityType, methodName);
                if (m == null) {
                    continue;
                }
                try {
                    Object result = m.invoke(adyeshachEntity);
                    if (result instanceof Entity entity) {
                        return entity;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            return null;
        }

        boolean valid() {
            return idMethod != null && locationMethod != null;
        }

        String missingRequiredMethods() {
            List<String> missing = new ArrayList<>();
            if (idMethod == null) {
                missing.add("getId");
            }
            if (locationMethod == null) {
                missing.add("getLocation");
            }
            return entityType.getName() + " missing " + String.join("/", missing);
        }

        Method idMethod() {
            return idMethod;
        }

        Method locationMethod() {
            return locationMethod;
        }

        Method displayNameMethod() {
            return displayNameMethod;
        }

        Method customNameMethod() {
            return customNameMethod;
        }

        Method v2Method() {
            return v2Method;
        }

        Method deletedMethod() {
            return deletedMethod;
        }

        Method invalidMethod() {
            return invalidMethod;
        }

        Method visibleViewerMethod() {
            return visibleViewerMethod;
        }
    }

}
