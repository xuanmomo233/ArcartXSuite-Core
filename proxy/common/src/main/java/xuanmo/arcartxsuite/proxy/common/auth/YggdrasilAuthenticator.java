package xuanmo.arcartxsuite.proxy.common.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import xuanmo.arcartxsuite.proxy.common.model.AccountType;
import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

/**
 * Yggdrasil 认证执行器：向指定认证源发起 hasJoined 验证。
 */
public class YggdrasilAuthenticator {

    private final Logger logger;
    private final boolean debug;

    public YggdrasilAuthenticator(Logger logger, boolean debug) {
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * 向指定 Yggdrasil 源验证玩家身份。
     *
     * @param source    Yggdrasil 认证源
     * @param username  玩家名
     * @param serverId  服务器 ID（ handshake 中传递）
     * @return 验证成功返回 Mojang Profile JSON（含 UUID、皮肤等），失败返回 null
     */
    @Nullable
    public String authenticate(@NotNull YggdrasilSource source, @NotNull String username, @NotNull String serverId) {
        String url = source.hasJoinedUrl(username, serverId);
        if (debug) {
            logger.info("[Auth] 验证玩家 " + username + " -> " + source.name() + " | URL: " + url);
        }

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-Proxy/1.0");
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == 204 || code == 404) {
                if (debug) {
                    logger.info("[Auth] " + source.name() + " 未找到玩家: " + username);
                }
                return null;
            }
            if (code != 200) {
                logger.warning("[Auth] " + source.name() + " 返回 HTTP " + code + " for " + username);
                return null;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                String response = sb.toString();
                if (debug) {
                    logger.info("[Auth] " + source.name() + " 响应: " + response);
                }
                return response;
            }
        } catch (IOException e) {
            if (debug) {
                logger.warning("[Auth] " + source.name() + " 验证异常: " + e.getMessage());
            }
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析 Mojang Profile JSON，提取 UUID。
     */
    @Nullable
    public UUID extractUuid(@Nullable String profileJson) {
        if (profileJson == null || profileJson.isBlank()) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(profileJson).getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (id.length() == 32) {
                return uuidFromNoDash(id);
            }
            return UUID.fromString(id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Profile JSON 中推断账号类型。
     * 如果 source 是 Mojang 官方 → MICROSOFT；
     * 否则 → LITTLESKIN。
     */
    @NotNull
    public AccountType inferAccountType(@NotNull YggdrasilSource source) {
        if (source.isMojang()) {
            return AccountType.MICROSOFT;
        }
        return AccountType.LITTLESKIN;
    }

    private static UUID uuidFromNoDash(String id) {
        return UUID.fromString(
            id.substring(0, 8) + "-" +
            id.substring(8, 12) + "-" +
            id.substring(12, 16) + "-" +
            id.substring(16, 20) + "-" +
            id.substring(20, 32)
        );
    }
}
