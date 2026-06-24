package xuanmo.arcartxsuite.api.capability;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 信号派发能力接口。
 * <p>
 * 由 EventPacket 模块实现，供其他模块（如 OnlineRewards）跨模块触发信号。
 */
public interface SignalDispatchable {

    /**
     * 向指定玩家触发一个信号，由 EventPacket 规则引擎匹配并执行对应动作。
     *
     * @param signal    信号名称（如 {@code "signin_success"}）
     * @param subject   触发玩家
     * @param variables 附加变量（可为空）
     */
    void dispatchSignal(@NotNull String signal, @NotNull Player subject, @Nullable Map<String, String> variables);
}
