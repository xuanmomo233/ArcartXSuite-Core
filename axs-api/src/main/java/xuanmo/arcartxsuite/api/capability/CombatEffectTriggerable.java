package xuanmo.arcartxsuite.api.capability;

import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 战斗特效主动触发能力接口。
 * <p>
 * 由 CombatEffect 模块实现，供其他模块（如 EventPacket、OnlineRewards）跨模块触发战斗特效。
 */
public interface CombatEffectTriggerable {

    /**
     * 通过包 ID 触发已注册的战斗特效包定义。
     *
     * @param packetId  包定义 ID（如 {@code "kill-effect"}）
     * @param recipient 目标玩家
     * @param variables 额外变量（会合并到 pack 模板渲染中），可为 null
     * @return {@code true} 表示发送成功
     */
    boolean triggerPacket(@NotNull String packetId, @NotNull Player recipient, @Nullable Map<String, String> variables);

    /**
     * 直接向玩家发送 UI 包（绕过包定义，完全自定义）。
     *
     * @param uiId          ArcartX UI ID
     * @param packetHandler 目标 packetHandler 名称
     * @param recipient     目标玩家
     * @param payload       发包内容（字符串、列表或字典）
     * @return {@code true} 表示发送成功
     */
    boolean triggerDirect(@NotNull String uiId, @NotNull String packetHandler, @NotNull Player recipient, @Nullable Object payload);
}
