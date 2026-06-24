package xuanmo.arcartxsuite.api.bridge;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * ArcartX WorldTexture 文字贴图特效桥接。
 * <p>
 * 在实体上方或世界坐标处渲染客户端自定义文字贴图。
 *
 * @since 1.2.0
 */
@ApiStability.Internal
public interface WorldTextureBridgeAPI {

    /** 初始化桥接 */
    boolean initialize();

    /** 桥接是否可用 */
    boolean isAvailable();

    /**
     * 在实体上方渲染文字贴图。
     */
    void spawnOnEntity(@NotNull Entity entity, @NotNull String id, @NotNull String texture,
                       double width, double height, double offsetY, boolean billboard);

    /**
     * 在世界坐标处渲染文字贴图。
     */
    void spawnAtLocation(@NotNull World world, @NotNull Location location, @NotNull String id,
                         @NotNull String texture, double width, double height);

    /**
     * 移除实体附着的贴图特效。
     */
    void removeFromEntity(@NotNull Entity entity, @NotNull String id);

    /**
     * 移除世界坐标上的贴图特效。
     */
    void removeFromWorld(@NotNull World world, @NotNull String id, @NotNull Location location);
}
