package xuanmo.arcartxsuite.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.account.AccountType;
import xuanmo.arcartxsuite.api.account.AccountTypeService;
import xuanmo.arcartxsuite.util.ReflectionCache;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Paper 1.21+ 安全聊天签名绕过服务。
 * <p>
 * Paper 1.21 引入 secure-chat validation，要求所有聊天消息携带 Mojang 签名。
 * LittleSkin / 离线玩家没有正版签名，聊天会被拦截（客户端提示 "Chat message validation failure"）。
 * <p>
 * 本服务在玩家加入时，通过反射清除 NMS 层中与聊天签名验证相关的字段，
 * 使 Paper 跳过对该玩家的签名校验，从而允许混合登录玩家正常发送聊天消息。
 */
public final class ChatSignBypassService implements Listener {

    private static final String[] SERVER_PLAYER_FIELDS = {
        "chatSession",
        "remoteChatSession",
    };
    private static final String[] CONNECTION_FIELDS = {
        "chatMessageValidator",
        "chatDecorator",
    };

    private final JavaPlugin plugin;
    private final Logger logger;
    private final boolean enabled;
    private final boolean onlyForNonPremium;
    private final AccountTypeService accountTypeService;
    private final ReflectionCache reflectionCache;
    private boolean paperDetected;
    private volatile boolean reflectionSuccessLogged;
    private volatile boolean skipLogged;

    public ChatSignBypassService(JavaPlugin plugin, boolean enabled,
                                 boolean onlyForNonPremium, AccountTypeService accountTypeService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = enabled;
        this.onlyForNonPremium = onlyForNonPremium;
        this.accountTypeService = accountTypeService;
        this.reflectionCache = new ReflectionCache(plugin.getClass().getClassLoader());
    }

    public void initialize() {
        if (!enabled) {
            logger.fine("聊天签名绕过已关闭（混合登录/离线玩家可能在 Paper 1.21+ 无法发送聊天消息）。");
            return;
        }
        if (isBelow1_21()) {
            // 1.21 以下不存在 secure-chat validation，无需绕过，静默退出
            return;
        }
        detectPaper();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // 对当前在线玩家立即生效（热重载场景）
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyBypass(player);
        }
        logger.info("聊天签名绕过服务已启用（解决 LittleSkin / 离线玩家 Paper 1.21+ 聊天失败）。");
    }

    public void shutdown() {
        // Listener 由 Bukkit 自动注销；无需额外清理。
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (enabled) {
            applyBypass(event.getPlayer());
        }
    }

    private void detectPaper() {
        try {
            reflectionCache.forName("com.destroystokyo.paper.PaperConfig");
            paperDetected = true;
        } catch (ClassNotFoundException e) {
            try {
                reflectionCache.forName("io.papermc.paper.configuration.Configuration");
                paperDetected = true;
            } catch (ClassNotFoundException e2) {
                paperDetected = false;
            }
        }
        if (!paperDetected) {
            logger.warning("当前不是 Paper 服务端，聊天签名绕过可能无效。");
        }
    }

    /**
     * 检测服务器版本是否低于 1.21。
     * secure-chat validation 仅在 Paper 1.21+ 存在，低版本无需任何绕过操作。
     */
    private boolean isBelow1_21() {
        String version = Bukkit.getBukkitVersion(); // e.g. "1.20.1-R0.1-SNAPSHOT"
        try {
            int dot = version.indexOf('.');
            if (dot == -1) return true;
            int major = Integer.parseInt(version.substring(0, dot));
            int minorDot = version.indexOf('.', dot + 1);
            String minorStr = minorDot == -1 ? version.substring(dot + 1) : version.substring(dot + 1, minorDot);
            // 去除可能的 "-R" 后缀
            int dash = minorStr.indexOf('-');
            if (dash != -1) minorStr = minorStr.substring(0, dash);
            int minor = Integer.parseInt(minorStr);
            return major < 1 || (major == 1 && minor < 21);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            // 无法解析时保守处理：假设需要 bypass
            return false;
        }
    }

    private void applyBypass(Player player) {
        if (!paperDetected) {
            return;
        }

        // 智能模式：利用 AccountTypeService 仅对非正版玩家绕过签名验证
        if (onlyForNonPremium && accountTypeService != null) {
            AccountType type = accountTypeService.resolve(player.getUniqueId(), player.getName());
            if (type == AccountType.MICROSOFT) {
                if (!skipLogged) {
                    skipLogged = true;
                    logger.fine("聊天签名绕过：跳过正版玩家 " + player.getName()
                        + "（保留 Mojang 签名验证，后续同类日志将抑制）。");
                }
                return;
            }
        }

        try {
            // CraftPlayer -> getHandle() -> ServerPlayer (NMS)
            Method getHandle = reflectionCache.method(player.getClass(), "getHandle");
            Object handle = getHandle.invoke(player);

            // 1. 尝试清除 ServerPlayer 上的聊天会话字段
            boolean playerFieldCleared = false;
            for (String fieldName : SERVER_PLAYER_FIELDS) {
                if (trySetField(handle, fieldName, null)) {
                    playerFieldCleared = true;
                    break;
                }
            }

            // 2. 尝试清除 ServerGamePacketListenerImpl 上的签名字段
            // ServerPlayer.connection -> ServerGamePacketListenerImpl
            Field connectionField = reflectionCache.field(handle.getClass(), "connection");
            Object connection = connectionField.get(handle);
            boolean connectionFieldCleared = false;
            for (String fieldName : CONNECTION_FIELDS) {
                if (trySetField(connection, fieldName, null)) {
                    connectionFieldCleared = true;
                    break;
                }
            }

            if ((playerFieldCleared || connectionFieldCleared) && !reflectionSuccessLogged) {
                reflectionSuccessLogged = true;
                logger.fine("已绕过 " + player.getName() + " 的聊天签名验证（后续同类型日志将抑制）。");
            }
        } catch (Exception e) {
            logger.warning("聊天签名绕过反射失败: " + e.getMessage());
        }
    }

    private boolean trySetField(Object target, String fieldName, Object value) {
        Field field = reflectionCache.findFieldInHierarchy(target.getClass(), fieldName);
        if (field == null) {
            return false;
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
