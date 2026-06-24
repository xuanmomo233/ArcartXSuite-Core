package xuanmo.arcartxsuite.proxy.common.model;

import java.util.UUID;

/**
 * Yggdrasil 认证源定义（Mojang / LittleSkin / 自定义）。
 */
public record YggdrasilSource(
    String name,
    String apiUrl,
    boolean enabled,
    boolean allowOfflineFallback,
    String serverId
) {
    public YggdrasilSource {
        if (apiUrl != null && !apiUrl.endsWith("/")) {
            apiUrl = apiUrl + "/";
        }
        if (serverId == null || serverId.isBlank()) {
            serverId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }

    public String authUrl() {
        return apiUrl + "authserver/authenticate";
    }

    public String refreshUrl() {
        return apiUrl + "authserver/refresh";
    }

    public String validateUrl() {
        return apiUrl + "authserver/validate";
    }

    public String invalidateUrl() {
        return apiUrl + "authserver/invalidate";
    }

    public String signoutUrl() {
        return apiUrl + "authserver/signout";
    }

    public String profileUrl(String uuid) {
        return apiUrl + "sessionserver/session/minecraft/profile/" + uuid;
    }

    public String hasJoinedUrl(String username, String serverId) {
        return apiUrl + "sessionserver/session/minecraft/hasJoined?username=" + username + "&serverId=" + serverId;
    }

    public boolean isMojang() {
        return apiUrl.contains("mojang.com") || apiUrl.contains("minecraft.net");
    }
}
