package xuanmo.arcartxsuite.api.bridge;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ArcartX UI/Packet 桥接的公开 API 子集。
 * <p>
 * 模块通过 {@link xuanmo.arcartxsuite.api.ModuleContext#packetBridge()} 获取实例。
 * 仅暴露模块开发所需的安全操作，内部反射细节对模块不可见。
 *
 * @since 1.1.0
 */
@ApiStability.Stable
public interface PacketBridgeAPI {

    /** 桥接是否可用（ArcartX 插件已加载且初始化成功） */
    boolean isAvailable();

    /** 初始化桥接（由宿主调用，模块不应调用） */
    boolean initialize();

    /** 关闭桥接（由宿主调用，模块不应调用） */
    void shutdown();

    /** 返回当前 packet 发送模式描述（用于日志/诊断） */
    String describePacketMode();

    /** 复位 UI 注册计数（模块加载期间统计用） */
    void resetUiRegistrationCount();

    /** 获取自上次复位以来成功注册的 UI 数量 */
    int successfulUiRegistrationCount();

    // ─── UI 生命周期 ────────────────────────────────────────────

    /**
     * 向 ArcartX 注册或热重载一个 UI 文件。
     *
     * @param configuredUiId 配置中指定的 UI id（可为 null，自动从文件名推导）
     * @param uiFile         UI YAML 文件
     * @return 注册结果
     */
    @NotNull UiRegistrationResult registerOrReloadUi(@Nullable String configuredUiId, @NotNull File uiFile);

    /** 注销指定 UI */
    boolean unregisterUi(@NotNull String uiId);

    // ─── 打开 / 关闭 ───────────────────────────────────────────

    /** 向玩家打开指定 UI */
    boolean openUi(@NotNull Player player, @NotNull String uiId);

    /** 向玩家打开 UI，关闭时回调 */
    boolean openUiWithCallback(@NotNull Player player, @NotNull String uiId, @NotNull Runnable callback);

    /** 向玩家关闭指定 UI */
    boolean closeUi(@NotNull Player player, @NotNull String uiId);

    /** 批量打开 */
    boolean openUiAll(@NotNull Player player, @NotNull List<String> uiIds);

    /** 批量关闭 */
    boolean closeUiAll(@NotNull Player player, @NotNull List<String> uiIds);

    // ─── Packet 发送 ────────────────────────────────────────────

    /**
     * 向玩家发送自定义包到指定 UI 的 handler。
     *
     * @param player      目标玩家
     * @param uiId        UI id
     * @param handlerName handler 名称
     * @param payload     数据载荷（Map / List / 基本类型）
     */
    boolean sendPacket(@NotNull Player player, @NotNull String uiId,
                       @NotNull String handlerName, @Nullable Object payload);

    /** 向多个 UI 同时发送 */
    boolean sendPacketToAll(@NotNull Player player, @NotNull List<String> uiIds,
                            @NotNull String handlerName, @Nullable Object payload);

    // ─── 聊天卡片 ──────────────────────────────────────────────

    /** 发送聊天卡片 */
    boolean sendChatCard(@NotNull Player player, @NotNull String cardId, @NotNull Map<String, String> data);

    // ─── 非安全 UI 操作（不触发 ArcartX 服务端校验/回调，模块按需使用） ──

    /** 非安全打开 UI（不经过 ArcartX 服务端校验） */
    boolean openUiUnsafe(@NotNull Player player, @NotNull String uiId);

    /** 非安全打开 UI 并带关闭回调 */
    boolean openUiUnsafeWithCallback(@NotNull Player player, @NotNull String uiId, @NotNull Runnable callback);

    /** 非安全关闭 UI（不经过 ArcartX 服务端校验） */
    boolean closeUiUnsafe(@NotNull Player player, @NotNull String uiId);

    // ─── 关闭回调 ──────────────────────────────────────────────

    /** 注册 UI 关闭回调 */
    boolean registerUiCloseCallback(@NotNull String uiId, @NotNull Consumer<Player> callback);

    /** 注销 UI 关闭回调 */
    void unregisterUiCloseCallback(@NotNull String uiId);

    // ─── UI id 工具 ─────────────────────────────────────────────

    /** 规范化 UI id */
    @NotNull
    static String normalizeUiId(@Nullable String configuredUiId, @NotNull File uiFile) {
        if (configuredUiId != null && !configuredUiId.isBlank()) {
            return configuredUiId.trim();
        }
        String name = uiFile.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ─── 结果类型 ──────────────────────────────────────────────

    record UiRegistrationResult(boolean success, String runtimeUiId, String registeredUiId,
                                String action, String message) {
        public static UiRegistrationResult failure(String runtimeUiId, String message) {
            return new UiRegistrationResult(false, runtimeUiId, null, "fail", message);
        }
    }
}
