package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArcartXPropBridge implements xuanmo.arcartxsuite.api.bridge.PropBridgeAPI {

    private static final String ARCARTX_API_CLASS_NAME = "priv.seventeen.artist.arcartx.api.ArcartXAPI";
    private static final String ENTITY_MANAGER_CLASS_NAME = "priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager";
    private static final String ARCARTX_PLAYER_CLASS_NAME = "priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer";
    private static final String ITEM_STACK_UTILS_CLASS_NAME = "priv.seventeen.artist.arcartx.util.ItemStackUtils";
    private static final String KEY_CALLBACK_CLASS_NAME = "priv.seventeen.artist.arcartx.util.collections.KeyCallBack";
    private static final String PDC_BACKEND_KEY = "pdc";

    private final JavaPlugin plugin;
    private final NamespacedKey persistentPropIdKey;
    private final Map<String, Object> keyCallbackKeepAlive = new ConcurrentHashMap<>();

    private boolean available;
    private Object keyBindRegistry;
    private Method registerClientKeyBindMethod;
    private Method unregisterClientKeyBindMethod;
    private Class<?> keyCallbackType;
    private Object entityManager;
    private Method entityManagerGetPlayerMethod;
    private Method playerGetSlotItemStackMethod;
    private Method playerSetSlotItemStackMethod;
    private Method playerSetTagCooldownMethod;
    private Method playerGetTagCooldownMethod;
    private Method playerSyncSlotCacheToClientMethod;
    private Method playerRemoveSlotItemStackOnlyClientMethod;
    private Method itemGetTagMethod;
    private Method itemSetCoolDownTagMethod;
    private Method itemGetCooldownTagMethod;

    public ArcartXPropBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.persistentPropIdKey = new NamespacedKey(plugin, "prop_id");
    }

    public boolean initialize() {
        available = false;
        keyBindRegistry = null;
        registerClientKeyBindMethod = null;
        unregisterClientKeyBindMethod = null;
        keyCallbackType = null;
        entityManager = null;
        entityManagerGetPlayerMethod = null;
        playerGetSlotItemStackMethod = null;
        playerSetSlotItemStackMethod = null;
        playerSetTagCooldownMethod = null;
        playerGetTagCooldownMethod = null;
        playerSyncSlotCacheToClientMethod = null;
        playerRemoveSlotItemStackOnlyClientMethod = null;
        itemGetTagMethod = null;
        itemSetCoolDownTagMethod = null;
        itemGetCooldownTagMethod = null;
        keyCallbackKeepAlive.clear();

        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(ARCARTX_API_CLASS_NAME, true, classLoader);
            Class<?> entityManagerClass = Class.forName(ENTITY_MANAGER_CLASS_NAME, true, classLoader);
            Class<?> arcartXPlayerClass = Class.forName(ARCARTX_PLAYER_CLASS_NAME, true, classLoader);
            Class<?> itemStackUtilsClass = Class.forName(ITEM_STACK_UTILS_CLASS_NAME, true, classLoader);
            keyCallbackType = Class.forName(KEY_CALLBACK_CLASS_NAME, true, classLoader);

            keyBindRegistry = apiClass.getMethod("getKeyBindRegistry").invoke(null);
            registerClientKeyBindMethod = keyBindRegistry.getClass().getMethod(
                "registerClientKeyBind",
                String.class,
                String.class,
                String.class,
                keyCallbackType
            );
            unregisterClientKeyBindMethod = findMethod(keyBindRegistry.getClass(), "unregisterClientKeyBind", String.class);
            if (unregisterClientKeyBindMethod == null) {
                unregisterClientKeyBindMethod = keyBindRegistry.getClass().getMethod("unRegisterClientKeyBind", String.class);
            }

            entityManager = apiClass.getMethod("getEntityManager").invoke(null);
            entityManagerGetPlayerMethod = entityManagerClass.getMethod("getPlayer", Player.class);

            playerGetSlotItemStackMethod = arcartXPlayerClass.getMethod("getSlotItemStack", String.class);
            playerSetSlotItemStackMethod = arcartXPlayerClass.getMethod("setSlotItemStack", String.class, ItemStack.class);
            playerSetTagCooldownMethod = arcartXPlayerClass.getMethod("setTagCooldown", String.class, long.class);
            playerGetTagCooldownMethod = arcartXPlayerClass.getMethod("getTagCooldown", String.class);
            playerSyncSlotCacheToClientMethod = arcartXPlayerClass.getMethod("syncSlotCacheToClient");
            playerRemoveSlotItemStackOnlyClientMethod = findMethod(
                arcartXPlayerClass,
                "removeSlotItemStackOnlyClient",
                String.class,
                boolean.class
            );

            itemGetTagMethod = itemStackUtilsClass.getMethod("getTag", ItemStack.class, String.class);
            itemSetCoolDownTagMethod = itemStackUtilsClass.getMethod("setCoolDownTag", ItemStack.class, String.class);
            itemGetCooldownTagMethod = itemStackUtilsClass.getMethod("getCooldownTag", ItemStack.class);
            available = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("初始化 ArcartX Prop ArcartX API 桥接失败: " + describeThrowable(exception));
            return false;
        }
    }

    public void shutdown() {
        available = false;
        keyBindRegistry = null;
        registerClientKeyBindMethod = null;
        unregisterClientKeyBindMethod = null;
        keyCallbackType = null;
        entityManager = null;
        entityManagerGetPlayerMethod = null;
        playerGetSlotItemStackMethod = null;
        playerSetSlotItemStackMethod = null;
        playerSetTagCooldownMethod = null;
        playerGetTagCooldownMethod = null;
        playerSyncSlotCacheToClientMethod = null;
        playerRemoveSlotItemStackOnlyClientMethod = null;
        itemGetTagMethod = null;
        itemSetCoolDownTagMethod = null;
        itemGetCooldownTagMethod = null;
        keyCallbackKeepAlive.clear();
    }

    public boolean isAvailable() {
        return available;
    }

    public String propIdWriterBackendKey() {
        return PDC_BACKEND_KEY;
    }

    public boolean registerClientKeyBind(String bindingId, String category, String defaultKey, Consumer<Player> onPress) {
        if (!available || keyBindRegistry == null || bindingId == null || bindingId.isBlank() || onPress == null || keyCallbackType == null) {
            return false;
        }

        InvocationHandler handler = (proxy, method, args) -> {
            if ("onPress".equals(method.getName())
                && args != null
                && args.length == 1
                && args[0] instanceof Player player
            ) {
                onPress.accept(player);
            }
            return null;
        };
        Object callback = Proxy.newProxyInstance(
            keyCallbackType.getClassLoader(),
            new Class<?>[] {keyCallbackType},
            handler
        );

        try {
            registerClientKeyBindMethod.invoke(keyBindRegistry, bindingId, safe(category), safe(defaultKey), callback);
            keyCallbackKeepAlive.put(bindingId, callback);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("注册 ArcartX 客户端按键失败(" + bindingId + "): " + describeThrowable(exception));
            return false;
        }
    }

    public void unregisterClientKeyBind(String bindingId) {
        if (!available || unregisterClientKeyBindMethod == null || bindingId == null || bindingId.isBlank()) {
            keyCallbackKeepAlive.remove(bindingId);
            return;
        }
        try {
            unregisterClientKeyBindMethod.invoke(keyBindRegistry, bindingId);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("注销 ArcartX 客户端按键失败(" + bindingId + "): " + describeThrowable(exception));
        } finally {
            keyCallbackKeepAlive.remove(bindingId);
        }
    }

    @Override
    public Optional<xuanmo.arcartxsuite.api.bridge.PropPlayerHandle> resolvePlayerHandle(Player player) {
        if (!available || player == null) {
            return Optional.empty();
        }

        try {
            Object handle = null;
            if (entityManager != null && entityManagerGetPlayerMethod != null) {
                handle = entityManagerGetPlayerMethod.invoke(entityManager, player);
            }
            return handle == null ? Optional.empty() : Optional.of(new PlayerHandle(handle));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("获取 ArcartXPlayer 失败(" + player.getName() + "): " + describeThrowable(exception));
            return Optional.empty();
        }
    }

    public String getItemTag(ItemStack itemStack, String key) {
        if (!available || itemStack == null || key == null || key.isBlank() || itemGetTagMethod == null) {
            return "";
        }
        try {
            Object raw = itemGetTagMethod.invoke(null, itemStack, key);
            return raw instanceof String value ? safe(value) : "";
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("读取 ArcartX ItemStack 标签失败(" + key + "): " + describeThrowable(exception));
            return "";
        }
    }

    public void setCooldownTag(ItemStack itemStack, String coolDownGroup) {
        if (!available || itemStack == null || itemSetCoolDownTagMethod == null) {
            return;
        }
        try {
            itemSetCoolDownTagMethod.invoke(null, itemStack, safe(coolDownGroup));
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("写入 ArcartX 冷却标签失败: " + describeThrowable(exception));
        }
    }

    public String getCooldownTag(ItemStack itemStack) {
        if (!available || itemStack == null || itemGetCooldownTagMethod == null) {
            return "";
        }
        try {
            Object raw = itemGetCooldownTagMethod.invoke(null, itemStack);
            return raw instanceof String value ? safe(value) : "";
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("读取 ArcartX 冷却标签失败: " + describeThrowable(exception));
            return "";
        }
    }

    public ItemStack writePropId(ItemStack itemStack, String propId) {
        if (itemStack == null) {
            return null;
        }
        return writePersistentPropId(itemStack, propId);
    }

    public String getPersistentPropId(ItemStack itemStack) {
        if (itemStack == null) {
            return "";
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return "";
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        String value = container.get(persistentPropIdKey, PersistentDataType.STRING);
        return value == null ? "" : value.trim();
    }


    private ItemStack writePersistentPropId(ItemStack itemStack, String propId) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }
        itemMeta.getPersistentDataContainer().set(persistentPropIdKey, PersistentDataType.STRING, safe(propId));
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String describeThrowable(Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException invocationTargetException
            ? invocationTargetException.getCause()
            : throwable;
        if (cause == null) {
            cause = throwable;
        }
        if (cause == null) {
            return "Unknown failure";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank()
            ? cause.getClass().getSimpleName()
            : cause.getClass().getSimpleName() + ": " + message;
    }

    public final class PlayerHandle implements xuanmo.arcartxsuite.api.bridge.PropPlayerHandle {

        private final Object handle;

        private PlayerHandle(Object handle) {
            this.handle = Objects.requireNonNull(handle, "handle");
        }

        public ItemStack getSlotItemStack(String slotId) {
            try {
                Object raw = playerGetSlotItemStackMethod.invoke(handle, safe(slotId));
                return raw instanceof ItemStack itemStack ? itemStack : null;
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("读取 ArcartX 额外槽位失败(" + slotId + "): " + describeThrowable(exception));
                return null;
            }
        }

        public void setSlotItemStack(String slotId, ItemStack itemStack) {
            try {
                playerSetSlotItemStackMethod.invoke(handle, safe(slotId), itemStack);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("写入 ArcartX 额外槽位失败(" + slotId + "): " + describeThrowable(exception));
            }
        }

        public void removeSlotItemStackOnlyClient(String slotPrefix, boolean recursive) {
            if (playerRemoveSlotItemStackOnlyClientMethod == null) {
                return;
            }
            try {
                playerRemoveSlotItemStackOnlyClientMethod.invoke(handle, safe(slotPrefix), recursive);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("清理 ArcartX 客户端额外槽位失败(" + slotPrefix + "): " + describeThrowable(exception));
            }
        }

        public void setTagCooldown(String tag, long cooldownMillis) {
            try {
                playerSetTagCooldownMethod.invoke(handle, safe(tag), Math.max(0L, cooldownMillis));
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("设置 ArcartX 冷却失败(" + tag + "): " + describeThrowable(exception));
            }
        }

        public long getTagCooldown(String tag) {
            try {
                Object raw = playerGetTagCooldownMethod.invoke(handle, safe(tag));
                return raw instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("读取 ArcartX 冷却失败(" + tag + "): " + describeThrowable(exception));
                return 0L;
            }
        }

        public void syncSlotCacheToClient() {
            try {
                playerSyncSlotCacheToClientMethod.invoke(handle);
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("同步 ArcartX 槽位缓存失败: " + describeThrowable(exception));
            }
        }
    }

}
