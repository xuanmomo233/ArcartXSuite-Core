package xuanmo.arcartxsuite.api.bridge;

import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ArcartX Prop 桥接的公开 API。
 * <p>
 * 提供客户端按键绑定、额外槽位、冷却标签与物品持久化 prop id 等能力。
 *
 * @since 1.2.0
 */
@ApiStability.Internal
public interface PropBridgeAPI {

    /** 桥接是否可用 */
    boolean isAvailable();

    /** 初始化桥接（由宿主调用） */
    boolean initialize();

    /** 关闭桥接（由宿主调用） */
    void shutdown();

    /** 写入 prop id 的 PDC backend key 标识 */
    @NotNull String propIdWriterBackendKey();

    /**
     * 注册客户端按键绑定。
     *
     * @param bindingId  绑定 id
     * @param category   按键分类
     * @param defaultKey 默认按键
     * @param onPress    按下回调
     */
    boolean registerClientKeyBind(@NotNull String bindingId, @Nullable String category,
                                  @Nullable String defaultKey, @NotNull Consumer<Player> onPress);

    /** 注销客户端按键绑定 */
    void unregisterClientKeyBind(@NotNull String bindingId);

    /** 解析玩家句柄 */
    @NotNull Optional<PropPlayerHandle> resolvePlayerHandle(@NotNull Player player);

    /** 读取物品标签 */
    @NotNull String getItemTag(@Nullable ItemStack itemStack, @NotNull String key);

    /** 设置冷却标签 */
    void setCooldownTag(@Nullable ItemStack itemStack, @NotNull String coolDownGroup);

    /** 读取冷却标签 */
    @NotNull String getCooldownTag(@Nullable ItemStack itemStack);

    /** 将 prop id 写入物品 PDC */
    @Nullable ItemStack writePropId(@Nullable ItemStack itemStack, @NotNull String propId);

    /** 从物品 PDC 读取持久化 prop id */
    @NotNull String getPersistentPropId(@Nullable ItemStack itemStack);
}
