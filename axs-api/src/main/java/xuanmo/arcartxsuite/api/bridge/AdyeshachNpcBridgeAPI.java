package xuanmo.arcartxsuite.api.bridge;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adyeshach NPC 桥接。
 * <p>
 * 查询附近 NPC、应用模型/动画、生成私有导航标记等。
 *
 * @since 1.2.0
 */
@ApiStability.Internal
public interface AdyeshachNpcBridgeAPI {

    /** 设置调试日志开关 */
    void setDebug(boolean debug);

    /** 初始化桥接 */
    boolean initialize();

    /** 关闭桥接 */
    void shutdown();

    /** 桥接是否可用 */
    boolean isAvailable();

    /** 查找玩家附近的 NPC */
    @NotNull List<AdyeshachNearbyNpc> findNearby(@NotNull Player player, double range);

    /** 按显示名/ID 查找 NPC */
    @NotNull Optional<Object> findByName(@NotNull String name);

    /** 获取指定实体的所有可识别名称 */
    @NotNull List<String> getEntityNames(@Nullable Object adyeshachEntity);

    /** 注册可见事件处理器 */
    boolean registerVisibleHandler(@NotNull BiConsumer<Player, Object> handler);

    /** 注销可见事件处理器 */
    void unregisterVisibleHandler();

    /** 为玩家生成私有导航标记实体 */
    @Nullable Object spawnPrivateMarker(@NotNull Player player, @NotNull String markerId, @NotNull Location location);

    /** 传送玩家私有标记实体 */
    boolean teleportMarker(@NotNull Player player, @NotNull String markerId, @NotNull Location location);

    /** 移除玩家私有标记实体 */
    boolean removePrivateMarker(@NotNull Player player, @NotNull String markerId);

    /** 清理所有私有标记实体 */
    void clearAllPrivateMarkers();

    /** 获取玩家私有标记实体 */
    @Nullable Object getPrivateMarker(@NotNull Player player, @NotNull String markerId);

    /** 为 NPC 广播应用模型 */
    boolean applyModel(@Nullable Object adyeshachEntity, @Nullable String modelId, double scale);

    /** 为指定玩家对 NPC 应用模型 */
    boolean applyModelForPlayer(@Nullable Player player, @Nullable Object adyeshachEntity, @Nullable String modelId, double scale);

    /** 为 NPC 广播播放动画 */
    boolean applyAnimation(@Nullable Object adyeshachEntity, @Nullable String animation, double speed, int transitionTime, long keepTime);

    /** 为指定玩家对 NPC 播放动画 */
    boolean applyAnimationForPlayer(@Nullable Player player, @Nullable Object adyeshachEntity, @Nullable String animation, double speed, int transitionTime, long keepTime);

    /** 为 NPC 广播设置默认状态 */
    boolean applyDefaultState(@Nullable Object adyeshachEntity, @Nullable String state, @Nullable String animName);

    /** 为指定玩家对 NPC 设置默认状态 */
    boolean applyDefaultStateForPlayer(@Nullable Player player, @Nullable Object adyeshachEntity, @Nullable String state, @Nullable String animName);
}
