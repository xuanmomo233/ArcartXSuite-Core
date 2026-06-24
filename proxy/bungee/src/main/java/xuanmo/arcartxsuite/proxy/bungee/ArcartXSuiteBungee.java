package xuanmo.arcartxsuite.proxy.bungee;

import java.io.File;
import java.util.logging.Logger;

import net.md_5.bungee.api.plugin.Plugin;

import xuanmo.arcartxsuite.proxy.common.auth.YggdrasilAuthenticator;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;

/**
 * ArcartXSuite BungeeCord 代理端伴侣插件。
 */
public class ArcartXSuiteBungee extends Plugin {

    private ProxyConfig config;
    private YggdrasilAuthenticator authenticator;
    private BungeeAuthListener authListener;

    @Override
    public void onEnable() {
        Logger logger = getLogger();

        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║      ArcartXSuite Proxy (BungeeCord) 加载中...             ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");

        // 加载配置
        config = new ProxyConfig(logger, getDataFolder());
        config.load("proxy-config.yml");

        // 初始化认证器
        authenticator = new YggdrasilAuthenticator(logger, config.debug());

        // 注册事件监听器
        authListener = new BungeeAuthListener(this, config, authenticator, logger);
        getProxy().getPluginManager().registerListener(this, authListener);

        // 注册命令
        getProxy().getPluginManager().registerCommand(this, new ProxyCommand(this, config, logger));

        // authlib-injector 检测提示
        detectAuthlibInjector(logger);

        logger.info("ArcartXSuite Proxy (BungeeCord) 加载完成。");
    }

    @Override
    public void onDisable() {
        getLogger().info("ArcartXSuite Proxy (BungeeCord) 已关闭。");
    }

    public ProxyConfig getProxyConfig() {
        return config;
    }

    public YggdrasilAuthenticator getAuthenticator() {
        return authenticator;
    }

    private void detectAuthlibInjector(Logger logger) {
        boolean loaded = false;
        try {
            Class.forName("moe.yushi.authlibinjector.AuthlibInjector");
            loaded = true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.contains("-javaagent") && arg.toLowerCase().contains("authlib-injector")) {
                    loaded = true;
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        if (loaded) {
            logger.info("[Auth] 已检测到 authlib-injector。");
            logger.info("[Auth] 注意: 若需正版+外置混合登录，authlib-injector 必须指向支持多源的认证端");
            logger.info("[Auth]   （MultiLogin 或本地混合代理）；单独指向 littleskin.cn 仅支持外置登录。");
            logger.info("[Auth]   ?mixed 并非 authlib-injector/LittleSkin 的有效参数，请勿使用。");
        } else {
            logger.warning("[Auth] 未检测到 authlib-injector！");
            logger.warning("[Auth] 群组服混合登录（正版 + LittleSkin）推荐方案:");
            logger.warning("[Auth]   方案A（推荐）: 安装 MultiLogin 插件，配置 OFFICIAL + BLESSING_SKIN 多源");
            logger.warning("[Auth]   方案B（仅外置）: 启动参数加 -javaagent:authlib-injector.jar=littleskin.cn");
            logger.warning("[Auth]   提示: LittleSkin 官方已不再推荐 BungeeCord，建议迁移至 Velocity");
        }
    }
}
