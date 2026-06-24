package xuanmo.arcartxsuite.api.security;

import org.bukkit.entity.Player;

/**
 * 客户端包频率限制 API。
 * <p>
 * 模块在处理客户端包时应调用 {@link #allow} 进行频率校验，
 * 防止客户端发送恶意高频包。
 */
public interface PacketGuardAPI {

    /**
     * 检查是否允许此次包通过频率限制。
     *
     * @param player       玩家
     * @param module       模块标识（如 "warehouse"）
     * @param action       动作标识（如 "buy"）
     * @param debugLogging 是否输出调试日志
     * @return true 如果允许通过
     */
    boolean allow(Player player, String module, String action, boolean debugLogging);
}
