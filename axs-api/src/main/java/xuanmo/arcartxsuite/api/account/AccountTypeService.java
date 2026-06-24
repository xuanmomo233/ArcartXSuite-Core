package xuanmo.arcartxsuite.api.account;

import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 统一账号识别服务（宿主级单例）。
 * <p>
 * 由 ArcartXSuite 本体实现，通过 {@link xuanmo.arcartxsuite.api.ModuleContext#accountTypeService()}
 * 暴露给所有模块。loginview / qqbot / eventpacket 等模块统一调用此服务判定账号类型，
 * 取代各模块自行实现且不一致的微软正版 / LittleSkin / 离线账号判定。
 *
 * <h2>判定规则（兼容 authlib-injector + LittleSkin 环境）</h2>
 * <ul>
 *   <li>玩家名在 Mojang 正版数据库存在 → {@link AccountType#MICROSOFT}
 *       （无论 UUID 为 v3 离线 UUID 还是 v4 在线 UUID。
 *       这修复了「微软正版未关联 LittleSkin 时 UUID 为 v3 被误判为离线」的问题）</li>
 *   <li>玩家名不在 Mojang，且 UUID 为 v4（已通过 yggdrasil 认证）→ {@link AccountType#LITTLESKIN}</li>
 *   <li>其他情况（v3 离线 UUID 且不在 Mojang）→ {@link AccountType#OFFLINE}</li>
 * </ul>
 *
 * @since 1.1.0
 */
@ApiStability.Stable
public interface AccountTypeService {

    /**
     * 解析账号类型（非阻塞，主线程安全）。
     * <p>
     * 优先返回缓存结果；缓存未命中时仅做<strong>本地判定</strong>（不发起网络请求），
     * 因此在 Mojang 信息尚未预热时可能保守地返回 {@link AccountType#LITTLESKIN}（v4）
     * 或 {@link AccountType#OFFLINE}。需要精确结果时请改用 {@link #resolveBlocking}。
     *
     * @param uuid 玩家 UUID（可空）
     * @param name 玩家名（可空）
     * @return 账号类型，永不为 null
     */
    @NotNull AccountType resolve(@Nullable UUID uuid, @Nullable String name);

    /**
     * 解析账号类型（允许阻塞网络查询 Mojang API），并将结果写入缓存。
     * <p>
     * <strong>必须在异步线程调用</strong>（如 {@code AsyncPlayerPreLoginEvent} 处理中），
     * 切勿在主线程调用以免卡服。宿主已在登录预登录阶段自动调用此方法预热缓存。
     *
     * @param uuid 玩家 UUID（可空）
     * @param name 玩家名（可空）
     * @return 账号类型，永不为 null
     */
    @NotNull AccountType resolveBlocking(@Nullable UUID uuid, @Nullable String name);

    /**
     * 仅查询缓存，缓存未命中时返回 {@link AccountType#OFFLINE}（不做任何判定）。
     *
     * @param uuid 玩家 UUID（可空）
     * @return 缓存的账号类型，未命中为 OFFLINE
     */
    @NotNull AccountType cached(@Nullable UUID uuid);

    /**
     * 便捷方法：基于 {@link OfflinePlayer} 解析（非阻塞）。
     */
    @NotNull
    default AccountType resolve(@Nullable OfflinePlayer player) {
        if (player == null) {
            return AccountType.OFFLINE;
        }
        return resolve(player.getUniqueId(), player.getName());
    }

    /**
     * 便捷方法：是否为正版账号（可免密直接进服）。
     */
    default boolean isPremium(@Nullable UUID uuid, @Nullable String name) {
        return resolve(uuid, name).premium();
    }

    /**
     * authlib-injector 是否已作为 JVM Agent 加载。
     */
    boolean isAuthlibInjectorLoaded();

    /**
     * 失效指定玩家的缓存（如玩家退出时）。
     */
    void invalidate(@Nullable UUID uuid);

    /**
     * 清空所有缓存。
     */
    void clearCache();
}
