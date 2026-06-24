package xuanmo.arcartxsuite.module;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 异步检查 ArcartXSuite 最新版本，启动时通知控制台，OP 加入时通知游戏内。
 * <p>
 * 支持 Ed25519 签名验证，防止中间人伪造更新提示诱导下载恶意文件。
 */
public final class VersionCheckService implements Listener {

    private static final String VERSION_URL = "https://axs.021209.xyz/api/version";
    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final JavaPlugin plugin;
    private final Logger logger;
    private final String currentVersion;
    private final PublicKey verifyKey;
    private volatile String latestVersion;
    private volatile String updateMessage;

    public VersionCheckService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentVersion = plugin.getDescription().getVersion();
        this.verifyKey = loadPublicKey(plugin.getConfig().getString("version-check-public-key", ""));
    }

    private static PublicKey loadPublicKey(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(base64.trim());
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            return kf.generatePublic(new java.security.spec.X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 异步发起版本检查。
     */
    public void checkAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERSION_URL))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String body = response.body().trim();
                    if (body.isEmpty()) return;

                    String remoteVersion = null;
                    String remoteSignature = null;

                    if (body.startsWith("{")) {
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        if (json.has("version")) {
                            remoteVersion = json.get("version").getAsString();
                        }
                        if (json.has("signature") && !json.get("signature").isJsonNull()) {
                            remoteSignature = json.get("signature").getAsString();
                        }
                    } else if (body.length() < 64) {
                        // 兼容旧版纯文本版本号
                        remoteVersion = body;
                    }

                    if (remoteVersion == null || remoteVersion.isEmpty()) {
                        return;
                    }

                    // 签名校验（如配置了公钥）
                    if (verifyKey != null) {
                        if (remoteSignature == null || remoteSignature.isBlank()) {
                            logger.warning("[VersionCheck] 云端未返回签名，跳过版本提示（防止中间人攻击）。");
                            return;
                        }
                        if (!verifySignature(remoteVersion, remoteSignature)) {
                            logger.warning("[VersionCheck] 版本响应签名验证失败，忽略本次更新提示（防止中间人攻击）。");
                            return;
                        }
                    }

                    latestVersion = remoteVersion;
                    if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                        updateMessage = "发现新版本: " + latestVersion + " (当前: " + currentVersion + ")";
                        logger.info("[VersionCheck] " + updateMessage);
                    } else {
                        logger.info("[VersionCheck] 当前已是最新版本 (" + currentVersion + ")");
                    }
                }
            } catch (Exception e) {
                logger.fine("[VersionCheck] 版本检查失败: " + e.getMessage());
            }
        });
    }

    private boolean verifySignature(String version, String signatureBase64) {
        try {
            byte[] sig = Base64.getDecoder().decode(signatureBase64.trim());
            byte[] payload = version.getBytes(StandardCharsets.UTF_8);
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(verifyKey);
            s.update(payload);
            return s.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateMessage == null) return;
        Player player = event.getPlayer();
        if (player.hasPermission("arcartxsuite.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(PREFIX + ChatColor.YELLOW + updateMessage);
                }
            }, 60L); // 3秒后发送，避免刷屏
        }
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public boolean hasUpdate() {
        return updateMessage != null;
    }
}
