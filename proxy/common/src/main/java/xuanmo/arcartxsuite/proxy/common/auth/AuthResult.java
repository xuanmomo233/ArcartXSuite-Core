package xuanmo.arcartxsuite.proxy.common.auth;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xuanmo.arcartxsuite.proxy.common.model.AccountType;
import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

/**
 * 认证结果封装。
 */
public record AuthResult(
    boolean success,
    @Nullable UUID uuid,
    @Nullable String username,
    @Nullable String profileJson,
    @NotNull AccountType accountType,
    @Nullable YggdrasilSource source,
    @Nullable String kickReason
) {
    public static AuthResult success(UUID uuid, String username, String profileJson,
                                      AccountType accountType, YggdrasilSource source) {
        return new AuthResult(true, uuid, username, profileJson, accountType, source, null);
    }

    public static AuthResult failure(String kickReason) {
        return new AuthResult(false, null, null, null, AccountType.UNKNOWN, null, kickReason);
    }

    public static AuthResult offline(String username) {
        return new AuthResult(true, null, username, null, AccountType.OFFLINE, null, null);
    }
}
