package xuanmo.arcartxsuite.proxy.bungee;

import java.util.UUID;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import xuanmo.arcartxsuite.proxy.common.auth.YggdrasilAuthenticator;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;
import xuanmo.arcartxsuite.proxy.common.model.AccountType;

public class BungeeAuthListener implements Listener {

    private final ArcartXSuiteBungee plugin;
    private final ProxyConfig config;
    private final YggdrasilAuthenticator authenticator;
    private final Logger logger;

    public BungeeAuthListener(ArcartXSuiteBungee plugin, ProxyConfig config,
                               YggdrasilAuthenticator authenticator, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.authenticator = authenticator;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(PreLoginEvent event) {
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(LoginEvent event) {
        PendingConnection connection = event.getConnection();
        UUID uuid = connection.getUniqueId();
        String username = connection.getName();

        AccountType type = classifyByUuid(uuid, username);

        if (config.debug()) {
            logger.info("[Auth] " + username + " 登录 BungeeCord，账号类型: " + type.displayName() + " (UUID: " + uuid + ")");
        }

        if (type == AccountType.OFFLINE && config.denyOffline()) {
            event.setCancelled(true);
            event.setCancelReason(ChatColor.translateAlternateColorCodes('&', config.kickOfflineMessage()));
            logger.info("[Auth] 拒绝离线玩家: " + username);
        }
    }

    @NotNull
    private AccountType classifyByUuid(@NotNull UUID uuid, @NotNull String username) {
        int version = (uuid.version() & 0xF);
        if (version == 4) return AccountType.LITTLESKIN;
        if (version == 3) return AccountType.OFFLINE;
        if (version == 1) return AccountType.MICROSOFT;
        return AccountType.UNKNOWN;
    }
}
