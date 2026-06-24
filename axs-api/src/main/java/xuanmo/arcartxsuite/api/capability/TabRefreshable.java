package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tab 列表刷新能力接口。
 * <p>
 * 由 Tab 模块实现，供 Title、Chat 等模块在数据变更时触发 Tab 刷新。
 */
public interface TabRefreshable {

    /**
     * 请求刷新指定玩家可见的 Tab。
     *
     * @param viewer 目标玩家
     * @param reason 触发原因（日志用）
     */
    void requestViewerRefresh(@NotNull Player viewer, @Nullable String reason);

    /**
     * 请求刷新所有在线玩家的 Tab。
     *
     * @param reason 触发原因（日志用）
     */
    void requestGlobalRefresh(@Nullable String reason);
}
