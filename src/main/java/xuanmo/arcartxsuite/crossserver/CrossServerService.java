package xuanmo.arcartxsuite.crossserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

public final class CrossServerService implements CrossServerAPI, PluginMessageListener {

    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final int PROXY_MAX_BYTES = Short.MAX_VALUE;

    private final JavaPlugin plugin;
    private CrossServerGlobalConfiguration configuration;
    private CrossServerDedupe dedupe = new CrossServerDedupe(60_000L);
    private final Map<String, ModuleRegistration> registrations = new ConcurrentHashMap<>();

    private JedisPool jedisPool;
    private ExecutorService redisSubscriberExecutor;
    private JedisPubSub redisPubSub;
    private volatile boolean redisSubscriberActive;
    private boolean proxyActive;

    public CrossServerService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configuration = CrossServerGlobalConfiguration.disabled();
    }

    public void start() {
        shutdownBackends();
        configuration = CrossServerGlobalConfiguration.from(plugin.getConfig());
        dedupe = new CrossServerDedupe(configuration.dedupeTtlMs());
        if (configuration.shouldSign() && configuration.signatureSecret().isBlank()) {
            plugin.getLogger().warning("[CrossServer] signature.enabled=true 但 secret 为空，签名与验签已禁用。");
        }
        if (configuration.redisEnabled()) {
            startRedis();
        }
        if (configuration.proxyEnabled()) {
            startProxy();
        }
        if (configuration.redisEnabled() || configuration.proxyEnabled()) {
            plugin.getLogger().info(
                "[CrossServer] 已启动 | node=" + configuration.nodeId()
                    + " | redis=" + configuration.redisEnabled()
                    + " | proxy=" + configuration.proxyEnabled()
                    + " | sign=" + configuration.shouldSign()
                    + " | verify=" + configuration.shouldVerify()
            );
        }
    }

    public void shutdown() {
        for (ModuleRegistration registration : registrations.values()) {
            registration.closed = true;
        }
        registrations.clear();
        shutdownBackends();
    }

    @Override
    public String nodeId() {
        return configuration.nodeId();
    }

    @Override
    public CrossServerChannel openChannel(
        String moduleId,
        CrossServerChannelConfig config,
        Consumer<CrossServerDelivery> consumer
    ) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(consumer, "consumer");
        CrossServerChannelConfig channelConfig = config == null ? CrossServerChannelConfig.disabled() : config;
        closeChannel(moduleId);
        ModuleRegistration registration = new ModuleRegistration(moduleId, channelConfig, consumer);
        registrations.put(moduleId, registration);
        return new CrossServerChannelHandle(registration);
    }

    private void closeChannel(String moduleId) {
        ModuleRegistration existing = registrations.remove(moduleId);
        if (existing != null) {
            existing.closed = true;
        }
    }

    private void startRedis() {
        try {
            String password = configuration.redisPassword().isBlank() ? null : configuration.redisPassword();
            jedisPool = new JedisPool(
                new JedisPoolConfig(),
                configuration.redisHost(),
                configuration.redisPort(),
                configuration.redisConnectTimeoutMs(),
                password,
                configuration.redisDatabase()
            );
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }
            redisSubscriberActive = true;
            redisPubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    handleWireMessage(message, "redis");
                }
            };
            redisSubscriberExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "AXS-CrossServer-Redis");
                thread.setDaemon(true);
                return thread;
            });
            redisSubscriberExecutor.execute(this::redisSubscribeLoop);
        } catch (Exception exception) {
            plugin.getLogger().warning("[CrossServer] Redis 初始化失败: " + exception.getMessage());
            shutdownRedis();
        }
    }

    private void redisSubscribeLoop() {
        while (redisSubscriberActive && jedisPool != null && redisPubSub != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(redisPubSub, configuration.redisChannel());
                return;
            } catch (Exception exception) {
                if (redisSubscriberActive) {
                    plugin.getLogger().warning("[CrossServer] Redis 订阅中断，3 秒后重试: " + exception.getMessage());
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void startProxy() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        proxyActive = true;
    }

    private void shutdownBackends() {
        shutdownRedis();
        shutdownProxy();
    }

    private void shutdownRedis() {
        redisSubscriberActive = false;
        if (redisPubSub != null) {
            try {
                redisPubSub.unsubscribe();
            } catch (Exception ignored) {
            }
            redisPubSub = null;
        }
        if (redisSubscriberExecutor != null) {
            redisSubscriberExecutor.shutdownNow();
            redisSubscriberExecutor = null;
        }
        if (jedisPool != null) {
            try {
                jedisPool.close();
            } catch (Exception ignored) {
            }
            jedisPool = null;
        }
    }

    private void shutdownProxy() {
        if (!proxyActive) {
            return;
        }
        proxyActive = false;
        try {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        } catch (Exception ignored) {
        }
        try {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!proxyActive || !BUNGEE_CHANNEL.equals(channel) || message == null || message.length == 0) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = input.readUTF();
            if (!"Forward".equalsIgnoreCase(subChannel)) {
                return;
            }
            input.readUTF();
            String actualChannel = input.readUTF();
            if (!configuration.proxyMessengerChannel().equalsIgnoreCase(actualChannel)) {
                return;
            }
            short length = input.readShort();
            byte[] payload = new byte[length];
            input.readFully(payload);
            handleWireMessage(new String(payload, StandardCharsets.UTF_8), "proxy");
        } catch (Exception exception) {
            plugin.getLogger().warning("[CrossServer] Proxy 消息解析失败: " + exception.getMessage());
        }
    }

    private void handleWireMessage(String wire, String source) {
        if (wire == null || wire.isBlank()) {
            return;
        }
        if (wire.length() > configuration.maxPayloadChars()) {
            plugin.getLogger().warning("[CrossServer] 丢弃超大 payload (" + source + "): " + wire.length());
            return;
        }
        try {
            CrossServerEnvelope envelope = CrossServerEnvelopeCodec.decode(wire);
            if (envelope.protocol() != CrossServerEnvelope.PROTOCOL_VERSION) {
                plugin.getLogger().warning("[CrossServer] 不支持的 protocol=" + envelope.protocol());
                return;
            }
            if (configuration.nodeId().equalsIgnoreCase(envelope.nodeId())) {
                return;
            }
            if (configuration.shouldVerify()) {
                if (envelope.signature() == null || envelope.signature().isBlank()) {
                    plugin.getLogger().warning("[CrossServer] 丢弃无签名消息 module=" + envelope.module());
                    return;
                }
                if (!CrossServerSigner.verify(envelope, envelope.signature(), secretBytes())) {
                    plugin.getLogger().warning("[CrossServer] 签名无效 module=" + envelope.module());
                    return;
                }
            }
            if (!dedupe.registerIfNew(envelope.messageId())) {
                return;
            }
            ModuleRegistration registration = registrations.get(envelope.module());
            if (registration == null || registration.closed) {
                return;
            }
            CrossServerDelivery delivery = new CrossServerDelivery(
                envelope.module(),
                envelope.nodeId(),
                envelope.messageId(),
                envelope.payload()
            );
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!registration.closed) {
                    registration.consumer.accept(delivery);
                }
            });
        } catch (Exception exception) {
            plugin.getLogger().warning("[CrossServer] 入站消息失败 (" + source + "): " + exception.getMessage());
        }
    }

    private void publishEnvelope(CrossServerEnvelope unsignedEnvelope, ModuleRegistration registration) {
        if (unsignedEnvelope.payload() != null
            && unsignedEnvelope.payload().length() > configuration.maxPayloadChars()) {
            plugin.getLogger().warning(
                "[CrossServer] 模块 " + unsignedEnvelope.module() + " payload 超过上限，已跳过发送"
            );
            return;
        }
        CrossServerEnvelope envelope = configuration.shouldSign()
            ? CrossServerSigner.withSignature(unsignedEnvelope, secretBytes())
            : unsignedEnvelope;
        String wire = CrossServerEnvelopeCodec.encode(envelope);
        boolean sendRedis = registration.config.redisEnabled(configuration.redisEnabled()) && jedisPool != null;
        boolean sendProxy = registration.config.proxyEnabled(configuration.proxyEnabled()) && proxyActive;
        if (!sendRedis && !sendProxy) {
            return;
        }
        if (sendRedis) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(configuration.redisChannel(), wire);
            } catch (Exception exception) {
                plugin.getLogger().warning("[CrossServer] Redis 发布失败: " + exception.getMessage());
            }
        }
        if (sendProxy) {
            publishProxy(wire, unsignedEnvelope.module());
        }
    }

    private void publishProxy(String wire, String moduleId) {
        byte[] payloadBytes = wire.getBytes(StandardCharsets.UTF_8);
        if (payloadBytes.length > PROXY_MAX_BYTES) {
            plugin.getLogger().warning(
                "[CrossServer] 模块 " + moduleId + " Proxy 负载 " + payloadBytes.length
                    + " 字节超过 " + PROXY_MAX_BYTES + "，已跳过 Proxy 仅走 Redis"
            );
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            return;
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeUTF("Forward");
                output.writeUTF(configuration.proxyForwardTarget());
                output.writeUTF(configuration.proxyMessengerChannel());
                output.writeShort(payloadBytes.length);
                output.write(payloadBytes);
            }
            carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, buffer.toByteArray());
        } catch (Exception exception) {
            plugin.getLogger().warning("[CrossServer] Proxy 发布失败: " + exception.getMessage());
        }
    }

    private byte[] secretBytes() {
        return configuration.signatureSecret().getBytes(StandardCharsets.UTF_8);
    }

    private final class CrossServerChannelHandle implements CrossServerChannel {

        private final ModuleRegistration registration;

        private CrossServerChannelHandle(ModuleRegistration registration) {
            this.registration = registration;
        }

        @Override
        public String moduleId() {
            return registration.moduleId;
        }

        @Override
        public boolean isActive() {
            if (registration.closed || !registration.config.enabled()) {
                return false;
            }
            boolean redis = registration.config.redisEnabled(configuration.redisEnabled()) && jedisPool != null;
            boolean proxy = registration.config.proxyEnabled(configuration.proxyEnabled()) && proxyActive;
            return redis || proxy;
        }

        @Override
        public void publish(String payload) {
            if (registration.closed || !registration.config.enabled() || payload == null) {
                return;
            }
            CrossServerEnvelope envelope = new CrossServerEnvelope(
                CrossServerEnvelope.PROTOCOL_VERSION,
                registration.moduleId,
                configuration.nodeId(),
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                payload,
                ""
            );
            publishEnvelope(envelope, registration);
        }

        @Override
        public void close() {
            registration.closed = true;
            registrations.remove(registration.moduleId, registration);
        }
    }

    private static final class ModuleRegistration {
        private final String moduleId;
        private final CrossServerChannelConfig config;
        private final Consumer<CrossServerDelivery> consumer;
        private volatile boolean closed;

        private ModuleRegistration(String moduleId, CrossServerChannelConfig config, Consumer<CrossServerDelivery> consumer) {
            this.moduleId = moduleId;
            this.config = config;
            this.consumer = consumer;
        }
    }
}
