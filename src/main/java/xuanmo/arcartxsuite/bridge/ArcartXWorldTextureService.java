package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ArcartX WorldTextureEffect 反射桥接。
 * <p>
 * 通过 {@code ArcartXEffectManager.spawnWorldTextureEffect} 在实体上方或世界坐标处
 * 渲染客户端自定义文字贴图（与 ArcartX UI 文字控件同一渲染管线）。
 */
public class ArcartXWorldTextureService implements xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI {

    private final JavaPlugin plugin;
    private final Logger logger;

    private boolean available;

    // WorldTextureBuilder
    private Constructor<?> builderCtor;
    private Method setTextureMethod;
    private Method setSizeMethod;
    private Method setLifeTimeMethod;

    // EffectPosition
    private Method followEntityMethod;
    private Method locationMethod;

    // ArcartXEffectManager
    private Method spawnOnEntityMethod;
    private Method spawnAtLocationMethod;
    private Method removeFromEntityMethod;
    private Method removeFromWorldMethod;

    public ArcartXWorldTextureService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean initialize() {
        available = false;
        Plugin arcartX = plugin.getServer().getPluginManager().getPlugin("ArcartX");
        if (arcartX == null) {
            return false;
        }

        try {
            ClassLoader cl = arcartX.getClass().getClassLoader();

            // WorldTextureBuilder
            Class<?> builderClass = resolveClass(cl,
                "priv.seventeen.artist.arcartx.core.effect.data.WorldTextureBuilder",
                "priv.seventeen.artist.arcartx.api.effect.WorldTextureBuilder");
            builderCtor = builderClass.getConstructor();
            setTextureMethod = builderClass.getMethod("setTexture", String.class, boolean.class, boolean.class);
            setSizeMethod = builderClass.getMethod("setSize", double.class, double.class);
            setLifeTimeMethod = builderClass.getMethod("setLifeTime", int.class);

            // EffectPosition
            Class<?> posClass = resolveClass(cl,
                "priv.seventeen.artist.arcartx.core.effect.data.EffectPosition",
                "priv.seventeen.artist.arcartx.api.effect.EffectPosition");
            followEntityMethod = posClass.getMethod("followEntity",
                Entity.class, double.class, double.class, double.class,
                float.class, float.class, float.class, boolean.class, boolean.class);
            locationMethod = posClass.getMethod("location", Location.class);

            // ArcartXEffectManager
            Class<?> managerClass = resolveClass(cl,
                "priv.seventeen.artist.arcartx.core.effect.ArcartXEffectManager",
                "priv.seventeen.artist.arcartx.api.effect.ArcartXEffectManager");
            spawnOnEntityMethod = managerClass.getMethod("spawnWorldTextureEffect",
                Entity.class, String.class, builderClass, posClass);
            spawnAtLocationMethod = findSpawnAtLocationMethod(managerClass, builderClass, posClass);
            removeFromEntityMethod = findMethod(managerClass, "removeModelEffect", Entity.class, String.class);
            if (removeFromEntityMethod == null) {
                removeFromEntityMethod = managerClass.getMethod("removeWorldTextureEffect", Entity.class, String.class);
            }
            removeFromWorldMethod = findMethod(managerClass, "removeModelEffect", World.class, String.class, Location.class);

            available = true;
            logger.fine("ArcartX WorldTexture 桥接初始化成功。");
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.warning("初始化 ArcartX WorldTexture 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * 在实体上方渲染文字贴图。
     *
     * @param entity    目标实体
     * @param id        唯一标识符（用于后续移除）
     * @param texture   贴图文字内容（同 ArcartX UI 文字控件格式）
     * @param width     显示宽度
     * @param height    显示高度
     * @param offsetY   Y 轴偏移
     * @param billboard 是否始终面向玩家
     */
    @Override
    public void spawnOnEntity(Entity entity, String id, String texture,
                              double width, double height, double offsetY, boolean billboard) {
        if (!available) return;
        try {
            Object builder = builderCtor.newInstance();
            setTextureMethod.invoke(builder, texture, true, true);
            setSizeMethod.invoke(builder, width, height);
            setLifeTimeMethod.invoke(builder, -1);
            Object position = followEntityMethod.invoke(null,
                entity, 0.0, offsetY, 0.0, 0f, 0f, 0f, false, billboard);
            spawnOnEntityMethod.invoke(null, entity, id, builder, position);
        } catch (ReflectiveOperationException exception) {
            logger.warning("WorldTexture 生成失败 (" + id + "): " + exception.getMessage());
        }
    }

    /**
     * 在世界坐标处渲染文字贴图。
     */
    @Override
    public void spawnAtLocation(World world, Location location, String id, String texture,
                                double width, double height) {
        if (!available || spawnAtLocationMethod == null) return;
        try {
            Object builder = builderCtor.newInstance();
            setTextureMethod.invoke(builder, texture, true, true);
            setSizeMethod.invoke(builder, width, height);
            setLifeTimeMethod.invoke(builder, -1);
            Object position = locationMethod.invoke(null, location);
            spawnAtLocationMethod.invoke(null, world, location, id, builder, position);
        } catch (ReflectiveOperationException exception) {
            logger.warning("WorldTexture 生成失败 (" + id + "): " + exception.getMessage());
        }
    }

    /**
     * 移除实体附着的贴图特效。
     */
    @Override
    public void removeFromEntity(Entity entity, String id) {
        if (!available || removeFromEntityMethod == null) return;
        try {
            removeFromEntityMethod.invoke(null, entity, id);
        } catch (ReflectiveOperationException exception) {
            logger.warning("WorldTexture 移除失败 (" + id + "): " + exception.getMessage());
        }
    }

    /**
     * 移除世界坐标上的贴图特效。
     */
    @Override
    public void removeFromWorld(World world, String id, Location location) {
        if (!available || removeFromWorldMethod == null) return;
        try {
            removeFromWorldMethod.invoke(null, world, id, location);
        } catch (ReflectiveOperationException exception) {
            logger.warning("WorldTexture 移除失败 (" + id + "): " + exception.getMessage());
        }
    }

    // ─── 辅助 ──────────────────────────────────────────────────

    private static Method findSpawnAtLocationMethod(Class<?> managerClass, Class<?> builderClass, Class<?> posClass) {
        try {
            return managerClass.getMethod("spawnWorldTextureEffect",
                World.class, Location.class, String.class, builderClass, posClass);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Class<?> resolveClass(ClassLoader classLoader, String primaryName, String fallbackName) throws ClassNotFoundException {
        try {
            return Class.forName(primaryName, true, classLoader);
        } catch (ClassNotFoundException ignored) {
            return Class.forName(fallbackName, true, classLoader);
        }
    }
}
