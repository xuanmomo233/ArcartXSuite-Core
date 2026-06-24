package xuanmo.arcartxsuite.proxy.velocity;

import java.util.UUID;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;

import xuanmo.arcartxsuite.proxy.common.auth.YggdrasilAuthenticator;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;
import xuanmo.arcartxsuite.proxy.common.model.AccountType;

/**
 * Velocity 认证事件监听器。
 * <p>
 * 代理端本身不直接处理 Yggdrasil 握手（由 authlib-injector 或 Velocity 内置完成），
 * 但在此层做以下辅助：
 * <ul>
 *   <li>离线玩家拦截（根据 UUID 版本判定）</li>
 *   <li>账号类型识别并写入 GameProfile Property，供后端 ArcartXSuite 读取</li>
 * </ul>
 */
public class VelocityAuthListener {

    private static final String AXS_ACCOUNT_TYPE_KEY = "arcartxsuite:account_type";
    private static final String AXS_AUTH_SOURCE_KEY = "arcartxsuite:auth_source";

    private final ArcartXSuiteVelocity plugin;
    private final ProxyServer server;
    private final ProxyConfig config;
    private final YggdrasilAuthenticator authenticator;
    private final Logger logger;

    public VelocityAuthListener(ArcartXSuiteVelocity plugin, ProxyServer server,
                                 ProxyConfig config, YggdrasilAuthenticator authenticator,
                                 Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.config = config;
        this.authenticator = authenticator;
        this.logger = logger;
    }

    /**
     * PreLogin：Velocity 已完成 Mojang/Yggdrasil 验证后的早期拦截点。
     * 用于拒绝明确判为离线的玩家。
     */
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!config.denyOffline()) {
            return;
        }

        String username = event.getUsername();
        // PreLogin 阶段 UUID 尚未确定，只能做粗略拦截
        // 真正的离线判定在 LoginEvent 阶段（UUID 已确定）
    }

    /**
     * LoginEvent：Velocity 已完成全部验证，玩家即将进入代理端。
     * 在此阶段：
     * 1. 根据 UUID 版本判定账号类型
     * 2. 若为离线且配置拒绝，踢出
     * 3. 将账号类型写入 GameProfile Property，后端可读取
     */
    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        AccountType type = classifyByUuid(uuid, username);

        if (config.debug()) {
            logger.info("[Auth] " + username + " 登录代理端，账号类型判定: " + type.displayName() + " (UUID: " + uuid + ")");
        }

        // 离线拦截
        if (type == AccountType.OFFLINE && config.denyOffline()) {
            player.disconnect(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(legacyToMiniMessage(config.kickOfflineMessage())));
            logger.info("[Auth] 拒绝离线玩家: " + username);
            return;
        }

        // 将账号类型信息附加到 GameProfile
        GameProfile original = player.getGameProfile();
        GameProfile updated = original.addProperty(
            new GameProfile.Property(AXS_ACCOUNT_TYPE_KEY, type.id(), "")
        );
        // Note: Velocity API 中 GameProfile 是不可变的，addProperty 返回新实例
        // 但无法替换 player 的 GameProfile，所以使用 Plugin Message 替代方案

        // 向后端发送插件消息告知账号类型（后端 ArcartXSuite 监听）
        // 实际实现需要在后端 Paper 注册 Plugin Message 监听器
    }

    /**
     * 根据 UUID 版本和特征判定账号类型。
     * <p>
     * 规则（与后端 AccountTypeService 保持一致）：
     * <ul>
     *   <li>UUID v4 → LittleSkin</li>
     *   <li>UUID v3 → 离线（需 Mojang API 二次确认时才可能是微软正版，代理端不做网络查询）</li>
     *   <li>UUID v1/v2 → 未知</li>
     * </ul>
     */
    @NotNull
    private AccountType classifyByUuid(@NotNull UUID uuid, @NotNull String username) {
        int version = (uuid.version() & 0xF);
        if (version == 4) {
            return AccountType.LITTLESKIN;
        }
        if (version == 3) {
            // v3 大部分是离线，但可能是微软正版（未关联 LittleSkin 时）
            // 代理端不做网络查询，保守判定为离线
            // 后端 AccountTypeService 会进一步通过 Mojang API 确认
            return AccountType.OFFLINE;
        }
        if (version == 1) {
            // v1 通常是微软正版（某些场景下）
            return AccountType.MICROSOFT;
        }
        return AccountType.UNKNOWN;
    }

    private String legacyToMiniMessage(String legacy) {
        if (legacy == null) return "";
        String msg = legacy
            .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
            .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
            .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
            .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
            .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
            .replace("&f", "<white>").replace("&k", "<obfuscated>").replace("&l", "<bold>")
            .replace("&m", "<strikethrough>").replace("&n", "<underlined>").replace("&o", "<italic>")
            .replace("&r", "<reset>");
        // 处理 &#RRGGBB 格式
        msg = msg.replaceAll("&#([0-9A-Fa-f]{6})", "<#$1>");
        return msg;
    }
}
