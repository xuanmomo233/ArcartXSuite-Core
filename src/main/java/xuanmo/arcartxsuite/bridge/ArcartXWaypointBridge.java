package xuanmo.arcartxsuite.bridge;

import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArcartXWaypointBridge implements xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI {

    private final JavaPlugin plugin;
    private final Set<String> availableStyleIds = new LinkedHashSet<>();

    private boolean available;
    private Object entityManager;
    private Method entityManagerGetPlayerMethod;
    private Method addWaypointByHandlerMethod;
    private Method deleteWaypointByHandlerMethod;
    private Method clearWaypointByHandlerMethod;

    public ArcartXWaypointBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean initialize(String ownerLabel) {
        reset();

        Plugin arcartX = Bukkit.getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            plugin.getLogger().warning(ownerLabel + " 初始化失败: 未找到 ArcartX。");
            return false;
        }

        try {
            ClassLoader classLoader = arcartX.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI", true, classLoader);
            initializeHandlerWaypointBridge(classLoader, apiClass);
            loadAvailableStyleIds(arcartX.getDataFolder());
            available = hasHandlerBridge();
            if (!available) {
                plugin.getLogger().warning(ownerLabel + " 初始化失败: 未找到兼容的 waypoint API。");
            }
            return available;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning(ownerLabel + " 初始化失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
        reset();
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public Set<String> availableStyleIds() {
        return Set.copyOf(availableStyleIds);
    }

    @Override
    public String resolveStyleId(String preferredStyleId, String fallbackStyleId, String ownerLabel) {
        String preferred = safe(preferredStyleId).isBlank() ? safe(fallbackStyleId) : preferredStyleId.trim();
        if (availableStyleIds.isEmpty() || availableStyleIds.contains(normalize(preferred))) {
            return preferred;
        }
        if (!"default".equalsIgnoreCase(preferred) && availableStyleIds.contains("default")) {
            plugin.getLogger().warning(ownerLabel + " 路标样式不存在，已回退 default: " + preferred);
            return "default";
        }
        return preferred;
    }

    @Override
    public boolean addWaypoint(
        Player player,
        String waypointId,
        String title,
        String styleId,
        double x,
        double y,
        double z
    ) {
        if (!available || player == null) {
            return false;
        }
        try {
            Object handler = resolvePlayerHandler(player);
            if (handler == null) {
                return false;
            }
            addWaypointByHandlerMethod.invoke(handler, waypointId, title, styleId, x, y, z);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("ArcartX waypoint 创建失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean removeWaypoint(Player player, String waypointId, boolean animated) {
        if (!available || player == null || safe(waypointId).isBlank()) {
            return false;
        }
        try {
            Object handler = resolvePlayerHandler(player);
            if (handler == null) {
                return false;
            }
            deleteWaypointByHandlerMethod.invoke(handler, waypointId, animated);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("ArcartX waypoint 删除失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean clearWaypoints(Player player) {
        if (!available || player == null) {
            return false;
        }
        try {
            Object handler = resolvePlayerHandler(player);
            if (handler == null) {
                return false;
            }
            clearWaypointByHandlerMethod.invoke(handler);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("ArcartX waypoint 清理失败: " + exception.getMessage());
            return false;
        }
    }

    private void initializeHandlerWaypointBridge(ClassLoader classLoader, Class<?> apiClass) throws ReflectiveOperationException {
        Class<?> entityManagerClass = Class.forName(
            "priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager",
            true,
            classLoader
        );
        entityManager = apiClass.getMethod("getEntityManager").invoke(null);
        entityManagerGetPlayerMethod = entityManagerClass.getMethod("getPlayer", Player.class);
        Class<?> handlerClass = Class.forName(
            "priv.seventeen.artist.arcartx.core.entity.data.ArcartXPlayer",
            true,
            classLoader
        );
        addWaypointByHandlerMethod = handlerClass.getMethod(
            "addWayPoint",
            String.class,
            String.class,
            String.class,
            double.class,
            double.class,
            double.class
        );
        deleteWaypointByHandlerMethod = handlerClass.getMethod("deleteWayPoint", String.class, boolean.class);
        clearWaypointByHandlerMethod = handlerClass.getMethod("clearWayPoint");
    }

    private Object resolvePlayerHandler(Player player) throws ReflectiveOperationException {
        if (player == null || entityManager == null || entityManagerGetPlayerMethod == null) {
            return null;
        }
        return entityManagerGetPlayerMethod.invoke(entityManager, player);
    }

    private void loadAvailableStyleIds(File arcartXDataFolder) {
        File waypointFolder = new File(arcartXDataFolder, "waypoint");
        if (!waypointFolder.isDirectory()) {
            return;
        }
        File[] files = waypointFolder.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                availableStyleIds.add(normalize(key));
            }
        }
    }

    private boolean hasHandlerBridge() {
        return entityManager != null
            && entityManagerGetPlayerMethod != null
            && addWaypointByHandlerMethod != null
            && deleteWaypointByHandlerMethod != null
            && clearWaypointByHandlerMethod != null;
    }

    private void reset() {
        available = false;
        entityManager = null;
        entityManagerGetPlayerMethod = null;
        addWaypointByHandlerMethod = null;
        deleteWaypointByHandlerMethod = null;
        clearWaypointByHandlerMethod = null;
        availableStyleIds.clear();
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
