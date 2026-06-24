package xuanmo.arcartxsuite.api.capability;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 任务导航能力接口。
 * <p>
 * 由 QuestGPS 模块实现，供 EventPacket 等模块跨模块调用。
 */
public interface QuestGpsNavigable {

    /** 向玩家推送任务供选择 */
    void offerQuest(@NotNull Player player, @NotNull String questId, boolean openMenu);

    /** 让玩家接受任务 */
    void acceptQuest(@NotNull Player player, @NotNull String questId);

    /** 打开任务菜单 */
    void openMenu(@NotNull Player player);

    /** 追踪任务 */
    void trackQuest(@NotNull Player player, @NotNull String questId);

    /** 追踪任务的指定步骤 */
    void trackTask(@NotNull Player player, @NotNull String questId, @NotNull String taskId);

    /** 检查事件规则是否被锁定 */
    boolean eventRuleLocked(@NotNull Player player, @NotNull String ruleId);
}
