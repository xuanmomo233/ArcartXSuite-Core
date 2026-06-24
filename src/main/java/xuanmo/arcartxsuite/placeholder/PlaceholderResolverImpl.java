package xuanmo.arcartxsuite.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

/**
 * {@link PlaceholderResolverAPI} 的宿主实现。
 * <p>
 * 封装 {@code me.clip.placeholderapi.PlaceholderAPI.setPlaceholders()}，
 * 并在启动时检测 PlaceholderAPI 是否可用，缓存结果以避免每次反射。
 */
public final class PlaceholderResolverImpl implements PlaceholderResolverAPI {

    private volatile boolean available;

    public PlaceholderResolverImpl() {
        refreshAvailability();
    }

    /**
     * 刷新可用性状态（例如插件重新加载后）。
     */
    public void refreshAvailability() {
        available = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public @NotNull String applyPlaceholders(@Nullable Player player, @NotNull String input) {
        if (!available || input.isEmpty()) {
            return input;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, input);
        } catch (Exception exception) {
            return input;
        }
    }

    @Override
    public @NotNull String applyPlaceholders(@Nullable OfflinePlayer player, @NotNull String input) {
        if (!available || input.isEmpty()) {
            return input;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, input);
        } catch (Exception exception) {
            return input;
        }
    }
}
