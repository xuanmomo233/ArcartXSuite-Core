package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.NotNull;

/**
 * 邮件发送能力接口。
 * <p>
 * 由 Mail 模块实现，供 EventPacket 等模块跨模块调用。
 */
public interface MailDispatchable {

    /**
     * 按预设模板发送邮件。
     *
     * @param presetId   预设 id
     * @param playerName 收件人名
     * @param source     来源标识（如 "EventPacket"）
     * @return {@code true} 表示发送成功
     */
    boolean dispatchPreset(@NotNull String presetId, @NotNull String playerName, @NotNull String source);
}
