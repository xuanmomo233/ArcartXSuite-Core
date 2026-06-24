package xuanmo.arcartxsuite.api.placeholder;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 统一 PlaceholderAPI 解析入口。
 * <p>
 * 由宿主实现并注入 {@link xuanmo.arcartxsuite.api.ModuleContext}，模块不再直接依赖
 * {@code me.clip.placeholderapi.PlaceholderAPI} 类。
 * <p>
 * 当 PlaceholderAPI 未安装时返回原字符串，调用方无需做空值判断。
 */
public interface PlaceholderResolverAPI {

    /**
     * PlaceholderAPI 是否可用（已安装且已启用）。
     */
    boolean available();

    /**
     * 解析字符串中的 PlaceholderAPI 占位符。
     *
     * @param player 提供上下文的玩家，可为 null
     * @param input  原始字符串
     * @return 已解析字符串；若 PAPI 不可用或输入为空则返回原字符串
     */
    @NotNull
    String applyPlaceholders(@Nullable Player player, @NotNull String input);

    /**
     * 解析字符串中的 PlaceholderAPI 占位符（离线玩家上下文）。
     *
     * @param player 提供上下文的离线玩家，可为 null
     * @param input  原始字符串
     * @return 已解析字符串；若 PAPI 不可用或输入为空则返回原字符串
     */
    @NotNull
    String applyPlaceholders(@Nullable OfflinePlayer player, @NotNull String input);
}
