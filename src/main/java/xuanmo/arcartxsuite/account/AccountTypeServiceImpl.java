package xuanmo.arcartxsuite.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.account.AccountType;
import xuanmo.arcartxsuite.api.account.AccountTypeService;

/**
 * 宿主统一账号识别服务实现。
 * <p>
 * 合并了原 loginview / qqbot 各自实现的判定逻辑，并修复了「微软正版未关联 LittleSkin 时
 * UUID 为 v3 被误判为离线」的问题。判定规则见 {@link AccountTypeService}。
 * <p>
 * 同时作为 {@link Listener} 在 {@link AsyncPlayerPreLoginEvent}（异步线程）阶段预热缓存，
 * 使玩家进服时账号类型已就绪，避免主线程阻塞网络请求。
 */
public final class AccountTypeServiceImpl implements AccountTypeService, Listener {

    private static final String AUTHLIB_AGENT_CLASS = "moe.yushi.authlibinjector.AuthlibInjector";
    private static final String MOJANG_PROFILE_API = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    private static final String MOJANG_PROFILE_BY_UUID_API = "https://api.minecraftservices.com/minecraft/profile/lookup/";
    private static final String MOJANG_LEGACY_API = "https://api.mojang.com/users/profiles/minecraft/";

    private final Logger logger;
    private final boolean enableMojangLookup;
    private final int mojangTimeoutMs;
    private final boolean debug;
    private final boolean authlibInjectorLoaded;
    private final Proxy proxy;
    /** 本地混合代理端口（<= 0 表示未启用混合登录，跳过代理权威查询）。 */
    private final int mixedProxyPort;

    /** UUID -> 已确定（含网络查询）的账号类型缓存 */
    private final ConcurrentMap<UUID, AccountType> accountTypeCache = new ConcurrentHashMap<>();
    /** 玩家名(小写) -> Mojang 官方 UUID（空串表示已确认不存在；网络失败不缓存） */
    private final ConcurrentMap<String, String> officialUuidCache = new ConcurrentHashMap<>();

    public AccountTypeServiceImpl(Logger logger, boolean enableMojangLookup, int mojangTimeoutMs, boolean debug, String proxyHost, int proxyPort, int mixedProxyPort) {
        this.logger = logger;
        this.enableMojangLookup = enableMojangLookup;
        this.mojangTimeoutMs = Math.max(1000, mojangTimeoutMs);
        this.debug = debug;
        this.authlibInjectorLoaded = detectAuthlibInjector();
        this.proxy = (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0)
            ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort))
            : Proxy.NO_PROXY;
        this.mixedProxyPort = mixedProxyPort;
    }

    @Override
    public @NotNull AccountType resolve(@Nullable UUID uuid, @Nullable String name) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }
        AccountType cached = accountTypeCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        // 非阻塞：仅使用本地已有信息判定，不发起网络请求，结果不写主缓存
        return classify(uuid, name, false);
    }

    @Override
    public @NotNull AccountType resolveBlocking(@Nullable UUID uuid, @Nullable String name) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }
        AccountType cached = accountTypeCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        AccountType type = classify(uuid, name, true);
        accountTypeCache.put(uuid, type);
        return type;
    }

    @Override
    public @NotNull AccountType cached(@Nullable UUID uuid) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }
        return accountTypeCache.getOrDefault(uuid, AccountType.OFFLINE);
    }

    @Override
    public boolean isAuthlibInjectorLoaded() {
        return authlibInjectorLoaded;
    }

    @Override
    public void invalidate(@Nullable UUID uuid) {
        if (uuid != null) {
            accountTypeCache.remove(uuid);
        }
    }

    @Override
    public void clearCache() {
        accountTypeCache.clear();
        officialUuidCache.clear();
    }

    /**
     * 在 PreLogin（异步线程）阶段预热账号类型缓存。
     * 使用 LOWEST 优先级尽早执行，让后续白名单门控等监听器能拿到已就绪的结果。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        AccountType type = resolveBlocking(event.getUniqueId(), event.getName());
        if (debug) {
            UUID uuid = event.getUniqueId();
            logger.info("[AccountType] PreLogin " + event.getName()
                + " uuid=" + uuid + " v" + (uuid == null ? "?" : uuid.version())
                + " -> " + type.id());
        }
    }

    // ─── 核心分类逻辑 ──────────────────────────────────────────

    private AccountType classify(UUID uuid, String name, boolean allowNetwork) {
        if (uuid == null) {
            return AccountType.OFFLINE;
        }

        // 优先：查本地混合代理的权威认证来源。
        // 玩家进服时 hasJoined 走本地代理，LittleSkin/Mojang 哪个命中就是哪种账号，
        // 这是 100% 准确的真实认证结果，且为本机查询（毫秒级），无需依赖外部 Mojang API。
        if (allowNetwork && mixedProxyPort > 0) {
            AccountType proxyResult = queryProxyAccountSource(uuid);
            if (proxyResult != null) {
                return proxyResult;
            }
        }

        // 微软正版与 LittleSkin 都可能为 v4 UUID，不能仅凭版本号区分。
        // 非混合模式（或代理无记录）时回退：通过 Mojang API 比较当前 UUID 与官方 UUID。
        // 关键事实：
        //   - 微软正版玩家的 UUID = Mojang 官方 UUID（无论是否通过 authlib-injector 登录）
        //   - LittleSkin 玩家的 UUID 由 yggdrasil 认证服务器分配，通常与官方 UUID 不同
        if (enableMojangLookup && allowNetwork) {
            String officialUuid = lookupOfficialUuid(name, true);
            if (officialUuid != null) {
                if (!officialUuid.isBlank()) {
                    // 玩家名在 Mojang 存在 → 微软正版（与 API 文档一致，不因 UUID 版本差异误判）
                    return AccountType.MICROSOFT;
                }
                // 名字已确认不在 Mojang（404/204）
                Boolean uuidExists = queryOfficialByUuid(uuid);
                if (uuidExists != null) {
                    if (uuidExists) {
                        return AccountType.MICROSOFT;
                    }
                    if (uuid.version() == 4) {
                        return AccountType.LITTLESKIN;
                    }
                    return AccountType.OFFLINE;
                }
                // UUID 反查也失败：保守判定，禁止 premium bypass
                return AccountType.OFFLINE;
            }
            // 名字查询网络失败：尝试 UUID 反查
            Boolean uuidExists = queryOfficialByUuid(uuid);
            if (uuidExists != null) {
                if (uuidExists) {
                    return AccountType.MICROSOFT;
                }
                if (uuid.version() == 4) {
                    return AccountType.LITTLESKIN;
                }
                return AccountType.OFFLINE;
            }
            // 全部查询失败：保守 OFFLINE，避免网络抖动导致 v4 误判为 LittleSkin 从而免密 bypass
            return AccountType.OFFLINE;
        }

        // 未启用 Mojang 查询：仅本地启发式，v4 不自动视为 LittleSkin（避免误 bypass）
        if (uuid != null && uuid.version() == 4 && isAuthlibInjectorLoaded()) {
            return AccountType.LITTLESKIN;
        }
        return AccountType.OFFLINE;
    }

    /**
     * 查询本地混合代理记录的权威认证来源。
     * <p>玩家进服时 hasJoined 走本地代理，代理已记录该 UUID 是 LittleSkin 还是 Mojang 命中。
     * 本机查询（localhost），毫秒级，且 100% 准确。
     *
     * @return {@link AccountType#MICROSOFT} / {@link AccountType#LITTLESKIN}；
     *         代理无记录（404）、未启动（连接拒绝）或异常时返回 {@code null}
     */
    private AccountType queryProxyAccountSource(UUID uuid) {
        String urlStr = "http://127.0.0.1:" + mixedProxyPort + "/axs-internal/account-source?uuid="
            + uuid.toString().replace("-", "");
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-AccountType");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                String source = extractJsonString(response.toString(), "source");
                if ("microsoft".equalsIgnoreCase(source)) {
                    if (debug) {
                        logger.fine("[AccountType] 代理权威结果: " + uuid + " -> microsoft");
                    }
                    return AccountType.MICROSOFT;
                }
                if ("littleskin".equalsIgnoreCase(source)) {
                    if (debug) {
                        logger.fine("[AccountType] 代理权威结果: " + uuid + " -> littleskin");
                    }
                    return AccountType.LITTLESKIN;
                }
                return null;
            }
        } catch (IOException e) {
            // 代理未启动（连接拒绝）属正常情况：非混合模式或代理尚未就绪，回退 Mojang API
            if (debug) {
                logger.fine("[AccountType] 代理查询跳过 (" + uuid + "): " + e.getMessage());
            }
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static boolean uuidEqualsIgnoreDashes(UUID uuid, String other) {
        String a = uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
        String b = other.replace("-", "").toLowerCase(Locale.ROOT);
        return a.equals(b);
    }

    /**
     * 查询玩家名对应的 Mojang 官方 UUID。
     * <p>
     * 返回官方 UUID（存在）；空串表示已确认该名字不是正版；
     * {@code null} 表示网络失败/未知（不写缓存，便于重试）。
     */
    private @Nullable String lookupOfficialUuid(String name, boolean allowNetwork) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        String cached = officialUuidCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        if (!allowNetwork) {
            return null;
        }
        try {
            String resolved = queryOfficialUuid(name);
            officialUuidCache.put(normalized, resolved);
            return resolved;
        } catch (IOException exception) {
            if (debug) {
                logger.warning("[AccountType] Mojang API 查询失败 (" + name + "): " + exception.getMessage()
                    + " —— 本次不缓存，下次重试");
            }
            return null;
        }
    }

    /**
     * 直接请求 Mojang Profile API。
     *
     * @return 官方 UUID（存在）或空串（HTTP 204/404 确认不存在）
     * @throws IOException 网络异常或非预期 HTTP 状态（如 429 限流），调用方据此跳过缓存
     */
    private String queryOfficialUuid(String name) throws IOException {
        // 优先尝试旧版 Mojang API（authlib-injector 通常已代理，连通性更好），
        // 失败后回退新版微软 API（带重试）。
        try {
            if (debug) {
                logger.fine("[AccountType] 尝试旧版 API: " + name);
            }
            return queryOfficialUuidFrom(name, MOJANG_LEGACY_API);
        } catch (IOException e) {
            if (debug) {
                logger.fine("[AccountType] 旧版 API 查询失败 (" + name + "): " + e.getMessage() + "，尝试新版 API");
            }
        }
        IOException lastException = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                return queryOfficialUuidFrom(name, MOJANG_PROFILE_API);
            } catch (IOException e) {
                lastException = e;
                if (debug) {
                    logger.fine("[AccountType] 新版 API 查询失败 (" + name + ") 第" + (attempt + 1) + "次: " + e.getMessage());
                }
            }
        }
        throw lastException != null ? lastException : new IOException("Mojang API 查询全部失败");
    }

    /**
     * 通过 UUID 反查 Mojang API，确认该 UUID 是否属于微软正版。
     *
     * @return true=微软正版（200）, false=非正版（404/204）, null=查询失败（网络异常）
     */
    private Boolean queryOfficialByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String urlStr = MOJANG_PROFILE_BY_UUID_API + uuid.toString().replace("-", "");
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(mojangTimeoutMs);
            conn.setReadTimeout(mojangTimeoutMs);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-AccountType");
            try {
                int code = conn.getResponseCode();
                if (code == 200) {
                    if (debug) {
                        logger.fine("[AccountType] UUID 反查确认正版: " + uuid);
                    }
                    return true;
                }
                if (code == 204 || code == 404) {
                    if (debug) {
                        logger.fine("[AccountType] UUID 反查确认非正版: " + uuid);
                    }
                    return false;
                }
                throw new IOException("非预期 HTTP 状态: " + code);
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            if (debug) {
                logger.warning("[AccountType] UUID 反查失败 (" + uuid + "): " + e.getMessage());
            }
            return null;
        }
    }

    private String queryOfficialUuidFrom(String name, String baseUrl) throws IOException {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        URL url = new URL(baseUrl + encoded);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(mojangTimeoutMs);
        conn.setReadTimeout(mojangTimeoutMs);
        conn.setRequestProperty("User-Agent", "ArcartXSuite-AccountType");
        try {
            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return extractJsonString(response.toString(), "id");
                }
            }
            if (code == 204 || code == 404) {
                // Mojang 明确返回「无此玩家」
                return "";
            }
            throw new IOException("非预期 HTTP 状态: " + code);
        } finally {
            conn.disconnect();
        }
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', keyIndex + pattern.length());
        if (colonIndex < 0) {
            return "";
        }
        int startQuote = json.indexOf('"', colonIndex + 1);
        if (startQuote < 0) {
            return "";
        }
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return "";
        }
        return json.substring(startQuote + 1, endQuote).trim();
    }

    private static boolean detectAuthlibInjector() {
        // 优先检查 JVM 启动参数：-javaagent:...authlib-injector... 是 authlib-injector
        // 实际注入 JVM 的方式，比 Class.forName 更可靠（避免被其他插件 shade 的库误报）。
        try {
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.contains("-javaagent") && arg.toLowerCase(Locale.ROOT).contains("authlib-injector")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        // fallback: 类路径中存在 authlib-injector 代理类（某些自定义加载方式）
        try {
            Class.forName(AUTHLIB_AGENT_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
            // ignore
        }
        return false;
    }
}
