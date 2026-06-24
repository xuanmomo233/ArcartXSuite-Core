package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.NotNull;

/**
 * QQ 群反向通知能力接口（群 → 服务器方向）。
 * <p>
 * 由 QQBot 模块注册，供其他模块监听来自 QQ 群的特定事件。
 * 例如：群内有人 @机器人、新成员加群、管理员在群里发出特定指令等。
 * <p>
 * 其他模块通过 {@code context.getCapability(QQBotNotifiable.class)} 获取后
 * 注册 {@link QQGroupEventListener} 监听器。
 */
public interface QQBotNotifiable {

    /**
     * 注册一个群事件监听器。
     *
     * @param listener 监听器实例
     */
    void registerListener(@NotNull QQGroupEventListener listener);

    /**
     * 注销一个群事件监听器。
     *
     * @param listener 监听器实例
     */
    void unregisterListener(@NotNull QQGroupEventListener listener);

    /**
     * QQ 群事件监听器。
     */
    interface QQGroupEventListener {

        /**
         * 收到群消息时触发（非指令消息）。
         *
         * @param groupId  群号
         * @param senderId 发送者 QQ 号
         * @param nickname 发送者昵称
         * @param message  消息内容
         */
        default void onGroupMessage(long groupId, long senderId, @NotNull String nickname, @NotNull String message) {}

        /**
         * 新成员加入 QQ 群时触发。
         *
         * @param groupId 群号
         * @param userId  新成员 QQ 号
         */
        default void onMemberJoin(long groupId, long userId) {}

        /**
         * 成员退出 QQ 群时触发。
         *
         * @param groupId 群号
         * @param userId  退出成员 QQ 号
         */
        default void onMemberLeave(long groupId, long userId) {}
    }
}
