package xuanmo.arcartxsuite.api.account;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 玩家账号认证类型。
 * <p>
 * 由宿主统一账号识别服务 {@link AccountTypeService} 判定，供 loginview / qqbot / eventpacket
 * 等模块共享，取代各模块各自实现的微软正版 / LittleSkin / 离线账号判定逻辑。
 *
 * @since 1.1.0
 */
@ApiStability.Stable
public enum AccountType {

    /** 微软正版账号（玩家名在 Mojang 正版数据库存在）。 */
    MICROSOFT("microsoft", "微软正版", true),

    /** LittleSkin（authlib-injector）认证账号（已通过第三方 yggdrasil 认证，但非 Mojang 正版名）。 */
    LITTLESKIN("littleskin", "LittleSkin", true),

    /** 离线账号（未通过任何正版认证）。 */
    OFFLINE("offline", "离线", false);

    private final String id;
    private final String displayName;
    private final boolean premium;

    AccountType(String id, String displayName, boolean premium) {
        this.id = id;
        this.displayName = displayName;
        this.premium = premium;
    }

    /** 稳定的小写标识（microsoft / littleskin / offline），用于占位符与配置。 */
    public @NotNull String id() {
        return id;
    }

    /** 中文展示名。 */
    public @NotNull String displayName() {
        return displayName;
    }

    /** 是否为正版账号（MICROSOFT 或 LITTLESKIN），可免密直接进服。 */
    public boolean premium() {
        return premium;
    }

    /** 按 id 解析，未知或 null 时回退为 {@link #OFFLINE}。 */
    public static @NotNull AccountType fromId(@Nullable String id) {
        if (id == null) {
            return OFFLINE;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (AccountType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return OFFLINE;
    }
}
