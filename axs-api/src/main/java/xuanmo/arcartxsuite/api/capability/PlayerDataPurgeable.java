package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家数据可清除能力接口。
 * <p>
 * 各有持久化存储的模块实现此接口并注册为 capability，
 * 由宿主的 {@code /axs purge <player>} 命令统一调度。
 */
public interface PlayerDataPurgeable {

    /**
     * 模块标识（如 "qqbot", "mail", "title"）。
     */
    @NotNull String moduleId();

    /**
     * 删除指定玩家在该模块的全部数据。
     *
     * @param playerUuid 玩家 UUID
     * @return 受影响行数
     */
    int purgePlayerData(@NotNull UUID playerUuid);

    /**
     * 删除该模块中所有玩家数据（清空表）。
     *
     * @return 受影响行数
     */
    default int purgeAllPlayerData() {
        return -1;
    }
}
