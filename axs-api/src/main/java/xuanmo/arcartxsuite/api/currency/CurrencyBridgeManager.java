package xuanmo.arcartxsuite.api.currency;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;

public final class CurrencyBridgeManager implements CurrencyBridgeAPI {

    private final JavaPlugin plugin;
    private final Map<String, CurrencyDefinition> definitions;
    private final Map<String, CurrencyBridge> bridges = new LinkedHashMap<>();
    private final PlaceholderResolverAPI placeholderResolver;

    public CurrencyBridgeManager(JavaPlugin plugin) {
        this(plugin, Map.of(), null);
    }

    public CurrencyBridgeManager(JavaPlugin plugin, Map<String, CurrencyDefinition> definitions) {
        this(plugin, definitions, null);
    }

    public CurrencyBridgeManager(JavaPlugin plugin, Map<String, CurrencyDefinition> definitions, PlaceholderResolverAPI placeholderResolver) {
        this.plugin = plugin;
        this.definitions = definitions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(definitions);
        this.placeholderResolver = placeholderResolver;
    }

    public void initialize() {
        bridges.clear();
        for (CurrencyDefinition definition : definitions.values()) {
            bridges.put(definition.id(), createBridge(definition));
        }
    }

    /**
     * 动态注册额外的货币定义（不覆盖已有）。
     * 注册后需调用 {@link #initialize()} 生效。
     */
    public void registerCurrencies(Map<String, CurrencyDefinition> additional) {
        if (additional == null || additional.isEmpty()) {
            return;
        }
        for (Map.Entry<String, CurrencyDefinition> entry : additional.entrySet()) {
            definitions.putIfAbsent(normalizeId(entry.getKey()), entry.getValue());
        }
    }

    @Override
    public CurrencyBridge bridge(String currencyId) {
        return bridges.get(normalizeId(currencyId));
    }

    @Override
    public CurrencyDefinition definition(String currencyId) {
        return definitions.get(normalizeId(currencyId));
    }

    @Override
    public Collection<CurrencyDefinition> definitions() {
        return definitions.values();
    }

    @Override
    public Set<String> currencyIds() {
        return bridges.keySet();
    }

    @Override
    public String format(String currencyId, BigDecimal amount) {
        CurrencyBridge bridge = bridge(currencyId);
        CurrencyDefinition definition = bridge == null ? definitions.get(normalizeId(currencyId)) : bridge.definition();
        if (definition == null) {
            return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
        }
        BigDecimal scaled = (amount == null ? BigDecimal.ZERO : amount).setScale(definition.scale(), RoundingMode.DOWN);
        return scaled.stripTrailingZeros().toPlainString();
    }

    private CurrencyBridge createBridge(CurrencyDefinition definition) {
        return switch (normalizeId(definition.provider())) {
            case "playerpoints" -> new PlayerPointsBridge(definition);
            case "placeholder-command", "command", "custom" -> new CommandCurrencyBridge(definition, placeholderResolver);
            case "rondo" -> new RondoCurrencyBridge(definition);
            default -> new VaultCurrencyBridge(definition);
        };
    }

    private abstract class AbstractCurrencyBridge implements CurrencyBridge {

        private final CurrencyDefinition definition;

        private AbstractCurrencyBridge(CurrencyDefinition definition) {
            this.definition = definition;
        }

        @Override
        public CurrencyDefinition definition() {
            return definition;
        }

        protected BigDecimal normalize(BigDecimal amount) {
            if (amount == null) {
                return BigDecimal.ZERO;
            }
            return amount.max(BigDecimal.ZERO).setScale(definition.scale(), RoundingMode.DOWN);
        }

        protected String formatAmount(BigDecimal amount) {
            return normalize(amount).stripTrailingZeros().toPlainString();
        }
    }

    private final class VaultCurrencyBridge extends AbstractCurrencyBridge {

        private Object economy;
        private Method balanceMethod;
        private Method withdrawMethod;
        private Method depositMethod;
        private String unavailableReason = "";

        private VaultCurrencyBridge(CurrencyDefinition definition) {
            super(definition);
            initializeVault();
        }

        @Override
        public boolean available() {
            return economy != null && balanceMethod != null && withdrawMethod != null && depositMethod != null;
        }

        @Override
        public String unavailableReason() {
            return unavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (!available() || player == null) {
                return BigDecimal.ZERO;
            }
            try {
                Object value = invokeEconomyMethod(balanceMethod, player, BigDecimal.ZERO);
                return convertToBigDecimal(value);
            } catch (ReflectiveOperationException exception) {
                return BigDecimal.ZERO;
            }
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            return invokeEconomyTransaction(withdrawMethod, player, amount, "扣款失败。");
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            return invokeEconomyTransaction(depositMethod, player, amount, "入账失败。");
        }

        private void initializeVault() {
            try {
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
                if (registration == null || registration.getProvider() == null) {
                    unavailableReason = "Vault Economy 服务未注册";
                    return;
                }
                economy = registration.getProvider();
                balanceMethod = findEconomyMethod(economy.getClass(), "getBalance");
                withdrawMethod = findEconomyMethod(economy.getClass(), "withdrawPlayer");
                depositMethod = findEconomyMethod(economy.getClass(), "depositPlayer");
                if (!available()) {
                    unavailableReason = "未找到兼容的 Vault Economy 方法";
                }
            } catch (ClassNotFoundException exception) {
                unavailableReason = "Vault 未安装";
            }
        }

        private CurrencyTransactionResult invokeEconomyTransaction(Method method, Player player, BigDecimal amount, String defaultMessage) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            try {
                Object response = invokeEconomyMethod(method, player, amount);
                return transactionSucceeded(response)
                    ? CurrencyTransactionResult.ok()
                    : CurrencyTransactionResult.failure(resolveTransactionMessage(response, defaultMessage));
            } catch (ReflectiveOperationException exception) {
                return CurrencyTransactionResult.failure(defaultMessage);
            }
        }

        private Object invokeEconomyMethod(Method method, Player player, BigDecimal amount) throws ReflectiveOperationException {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1) {
                return method.invoke(economy, resolveFirstArgument(method, player));
            }
            if (parameterTypes.length == 2) {
                return method.invoke(economy, resolveFirstArgument(method, player), castNumeric(parameterTypes[1], amount));
            }
            return method.invoke(
                economy,
                resolveFirstArgument(method, player),
                player.getWorld().getName(),
                castNumeric(parameterTypes[2], amount)
            );
        }
    }

    private final class PlayerPointsBridge extends AbstractCurrencyBridge {

        private Object api;
        private Method lookMethod;
        private Method takeMethod;
        private Method giveMethod;
        private String unavailableReason = "";

        private PlayerPointsBridge(CurrencyDefinition definition) {
            super(definition);
            initializePlayerPoints();
        }

        @Override
        public boolean available() {
            return api != null && lookMethod != null && takeMethod != null && giveMethod != null;
        }

        @Override
        public String unavailableReason() {
            return unavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (!available() || player == null) {
                return BigDecimal.ZERO;
            }
            try {
                Object raw = lookMethod.invoke(api, resolvePlayerArgument(lookMethod, player));
                return convertToBigDecimal(raw);
            } catch (ReflectiveOperationException exception) {
                return BigDecimal.ZERO;
            }
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            return invokePointsTransaction(takeMethod, player, amount, "扣除点券失败。");
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            return invokePointsTransaction(giveMethod, player, amount, "发放点券失败。");
        }

        private void initializePlayerPoints() {
            Plugin playerPoints = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (playerPoints == null) {
                unavailableReason = "PlayerPoints 未安装";
                return;
            }
            try {
                try {
                    api = playerPoints.getClass().getMethod("getAPI").invoke(playerPoints);
                } catch (NoSuchMethodException exception) {
                    Class<?> mainClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
                    Method getInstanceMethod = mainClass.getMethod("getInstance");
                    Object instance = getInstanceMethod.invoke(null);
                    api = mainClass.getMethod("getAPI").invoke(instance);
                }
                lookMethod = findPointsMethod(api.getClass(), "look");
                takeMethod = findPointsMethod(api.getClass(), "take");
                giveMethod = findPointsMethod(api.getClass(), "give");
                if (!available()) {
                    unavailableReason = "未找到兼容的 PlayerPoints API 方法";
                }
            } catch (ReflectiveOperationException exception) {
                unavailableReason = "PlayerPoints API 初始化失败";
            }
        }

        private CurrencyTransactionResult invokePointsTransaction(Method method, Player player, BigDecimal amount, String defaultMessage) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            int points = normalize(amount).intValue();
            if (points <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            try {
                Object raw = method.invoke(api, resolvePlayerArgument(method, player), points);
                boolean success = raw instanceof Boolean booleanValue ? booleanValue : true;
                return success ? CurrencyTransactionResult.ok() : CurrencyTransactionResult.failure(defaultMessage);
            } catch (ReflectiveOperationException exception) {
                return CurrencyTransactionResult.failure(defaultMessage);
            }
        }
    }

    private final class CommandCurrencyBridge extends AbstractCurrencyBridge {

        private final PlaceholderResolverAPI placeholderResolver;
        private String unavailableReason = "";

        private CommandCurrencyBridge(CurrencyDefinition definition, PlaceholderResolverAPI placeholderResolver) {
            super(definition);
            this.placeholderResolver = placeholderResolver;
            initializePlaceholderApi();
        }

        @Override
        public boolean available() {
            return placeholderResolver != null
                && !definition().balancePlaceholder().isBlank()
                && !definition().withdrawCommand().isBlank()
                && !definition().depositCommand().isBlank();
        }

        @Override
        public String unavailableReason() {
            return unavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (!available() || player == null) {
                return BigDecimal.ZERO;
            }
            String resolved = placeholderResolver.applyPlaceholders(player, definition().balancePlaceholder());
            return parseNumericString(resolved);
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            if (balance(player).compareTo(normalize(amount)) < 0) {
                return CurrencyTransactionResult.failure("余额不足。");
            }
            return dispatchConsoleCommand(definition().withdrawCommand(), player, amount)
                ? CurrencyTransactionResult.ok()
                : CurrencyTransactionResult.failure("执行扣款命令失败。");
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            if (!available()) {
                return CurrencyTransactionResult.failure(unavailableReason());
            }
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            return dispatchConsoleCommand(definition().depositCommand(), player, amount)
                ? CurrencyTransactionResult.ok()
                : CurrencyTransactionResult.failure("执行发放命令失败。");
        }

        private void initializePlaceholderApi() {
            if (placeholderResolver == null) {
                unavailableReason = "PlaceholderAPI 解析器未注入";
                return;
            }
            if (!placeholderResolver.available()) {
                unavailableReason = "PlaceholderAPI 未安装";
            }
        }

        private boolean dispatchConsoleCommand(String template, Player player, BigDecimal amount) {
            String rendered = template
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%amount%", formatAmount(amount));
            if (rendered.startsWith("/")) {
                rendered = rendered.substring(1);
            }
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered);
        }
    }

    private final class RondoCurrencyBridge extends AbstractCurrencyBridge {

        // Rondo 原生 API（多货币场景使用）
        private Object rondoApiInstance;
        private Method rondoGetBalanceMethod;
        private Method rondoWithdrawMethod;
        private Method rondoDepositMethod;
        private String rondoUnavailableReason = "";

        // Vault 回退（Rondo 是 Vault 提供者时，单货币走 Vault 更可靠）
        private Object vaultEconomy;
        private Method vaultBalanceMethod;
        private Method vaultWithdrawMethod;
        private Method vaultDepositMethod;
        private String vaultUnavailableReason = "";

        private RondoCurrencyBridge(CurrencyDefinition definition) {
            super(definition);
            initializeVault();
            initializeRondo();
        }

        @Override
        public boolean available() {
            return vaultAvailable() || rondoAvailable();
        }

        private boolean vaultAvailable() {
            return vaultEconomy != null && vaultBalanceMethod != null && vaultWithdrawMethod != null && vaultDepositMethod != null;
        }

        private boolean rondoAvailable() {
            return rondoApiInstance != null && rondoGetBalanceMethod != null && rondoWithdrawMethod != null && rondoDepositMethod != null;
        }

        @Override
        public String unavailableReason() {
            if (available()) {
                return "";
            }
            // 两者均不可用：优先返回 Vault 原因，其次 Rondo 原因
            if (!vaultUnavailableReason.isBlank()) {
                return vaultUnavailableReason;
            }
            return rondoUnavailableReason.isBlank() ? "Rondo/Vault 货币后端不可用" : rondoUnavailableReason;
        }

        @Override
        public BigDecimal balance(Player player) {
            if (player == null) return BigDecimal.ZERO;
            // 优先 Vault（Rondo 作为 Vault 提供者时无需货币 ID 映射）
            if (vaultEconomy != null && vaultBalanceMethod != null) {
                try {
                    Object value = invokeVaultEconomyMethod(vaultBalanceMethod, player, BigDecimal.ZERO);
                    return convertToBigDecimal(value);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            // 回退 Rondo 原生
            if (rondoApiInstance != null && rondoGetBalanceMethod != null) {
                try {
                    Object result = rondoGetBalanceMethod.invoke(rondoApiInstance, player.getUniqueId(), definition().id());
                    return result instanceof BigDecimal bd ? bd : convertToBigDecimal(result);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return BigDecimal.ZERO;
        }

        @Override
        public CurrencyTransactionResult withdraw(Player player, BigDecimal amount) {
            return invokeTransaction(vaultWithdrawMethod, rondoWithdrawMethod, player, amount, "扣款失败。");
        }

        @Override
        public CurrencyTransactionResult deposit(Player player, BigDecimal amount) {
            return invokeTransaction(vaultDepositMethod, rondoDepositMethod, player, amount, "入账失败。");
        }

        private CurrencyTransactionResult invokeTransaction(
            Method vaultMethod, Method rondoMethod, Player player, BigDecimal amount, String defaultMessage
        ) {
            if (player == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return CurrencyTransactionResult.failure("金额必须大于 0。");
            }
            // 优先 Vault
            if (vaultEconomy != null && vaultMethod != null) {
                try {
                    Object response = invokeVaultEconomyMethod(vaultMethod, player, amount);
                    return transactionSucceeded(response)
                        ? CurrencyTransactionResult.ok()
                        : CurrencyTransactionResult.failure(resolveTransactionMessage(response, defaultMessage));
                } catch (ReflectiveOperationException ignored) {
                }
            }
            // 回退 Rondo 原生
            if (rondoApiInstance != null && rondoMethod != null) {
                try {
                    Object result = rondoMethod.invoke(rondoApiInstance, player.getUniqueId(), definition().id(), normalize(amount), "ArcartXSuite");
                    boolean success = result instanceof Boolean booleanValue ? booleanValue : true;
                    return success ? CurrencyTransactionResult.ok() : CurrencyTransactionResult.failure("Rondo " + defaultMessage);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return CurrencyTransactionResult.failure(defaultMessage);
        }

        private void initializeVault() {
            try {
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
                if (registration == null || registration.getProvider() == null) {
                    vaultUnavailableReason = "Vault Economy 服务未注册";
                    return;
                }
                vaultEconomy = registration.getProvider();
                vaultBalanceMethod = findEconomyMethod(vaultEconomy.getClass(), "getBalance");
                vaultWithdrawMethod = findEconomyMethod(vaultEconomy.getClass(), "withdrawPlayer");
                vaultDepositMethod = findEconomyMethod(vaultEconomy.getClass(), "depositPlayer");
            } catch (ClassNotFoundException exception) {
                vaultUnavailableReason = "Vault 未安装";
            }
        }

        private void initializeRondo() {
            Plugin rondo = Bukkit.getPluginManager().getPlugin("Rondo");
            if (rondo == null) {
                rondoUnavailableReason = "Rondo 未安装";
                return;
            }
            try {
                ClassLoader classLoader = rondo.getClass().getClassLoader();
                Class<?> rondoApiClass = Class.forName("priv.seventeen.artist.rondo.api.RondoAPI", true, classLoader);
                rondoApiInstance = rondoApiClass.getField("INSTANCE").get(null);
                rondoGetBalanceMethod = rondoApiClass.getMethod("getBalance", java.util.UUID.class, String.class);
                rondoWithdrawMethod = rondoApiClass.getMethod("withdraw", java.util.UUID.class, String.class, BigDecimal.class, String.class);
                rondoDepositMethod = rondoApiClass.getMethod("deposit", java.util.UUID.class, String.class, BigDecimal.class, String.class);
            } catch (ReflectiveOperationException exception) {
                rondoUnavailableReason = "Rondo API 初始化失败: " + exception.getMessage();
            }
        }

        private Object invokeVaultEconomyMethod(Method method, Player player, BigDecimal amount) throws ReflectiveOperationException {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1) {
                return method.invoke(vaultEconomy, resolveFirstArgument(method, player));
            }
            if (parameterTypes.length == 2) {
                return method.invoke(vaultEconomy, resolveFirstArgument(method, player), castNumeric(parameterTypes[1], amount));
            }
            return method.invoke(
                vaultEconomy,
                resolveFirstArgument(method, player),
                player.getWorld().getName(),
                castNumeric(parameterTypes[2], amount)
            );
        }
    }

    private Method findEconomyMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() < 1) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1 && "getBalance".equals(name) && isPlayerType(parameterTypes[0])) {
                return method;
            }
            if (parameterTypes.length == 2 && isPlayerType(parameterTypes[0]) && isNumericType(parameterTypes[1])) {
                return method;
            }
            if (parameterTypes.length == 3 && isPlayerType(parameterTypes[0]) && parameterTypes[1] == String.class && isNumericType(parameterTypes[2])) {
                return method;
            }
        }
        return null;
    }

    private Method findPointsMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if ("look".equals(name) && parameterTypes.length == 1 && (parameterTypes[0].getName().equals("java.util.UUID") || Player.class.isAssignableFrom(parameterTypes[0]))) {
                return method;
            }
            if (!"look".equals(name) && parameterTypes.length == 2 && (parameterTypes[0].getName().equals("java.util.UUID") || Player.class.isAssignableFrom(parameterTypes[0])) && (parameterTypes[1] == int.class || parameterTypes[1] == Integer.class)) {
                return method;
            }
        }
        return null;
    }

    private Object resolveFirstArgument(Method method, Player player) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
            return null;
        }
        Class<?> first = parameterTypes[0];
        if (Player.class.isAssignableFrom(first)) {
            return player;
        }
        if (OfflinePlayer.class.isAssignableFrom(first)) {
            return player;
        }
        return player.getName();
    }

    private Object resolvePlayerArgument(Method method, Player player) {
        Class<?> parameterType = method.getParameterTypes()[0];
        if (Player.class.isAssignableFrom(parameterType)) {
            return player;
        }
        return player.getUniqueId();
    }

    private boolean transactionSucceeded(Object response) {
        if (response == null) {
            return true;
        }
        if (response instanceof Boolean booleanValue) {
            return booleanValue;
        }
        try {
            Object success = response.getClass().getMethod("transactionSuccess").invoke(response);
            return !(success instanceof Boolean booleanValue) || booleanValue;
        } catch (ReflectiveOperationException exception) {
            return true;
        }
    }

    private String resolveTransactionMessage(Object response, String defaultMessage) {
        if (response == null) {
            return defaultMessage;
        }
        try {
            Object error = response.getClass().getField("errorMessage").get(response);
            if (error != null && !String.valueOf(error).isBlank()) {
                return String.valueOf(error);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return defaultMessage;
    }

    private static boolean isPlayerType(Class<?> type) {
        return Player.class.isAssignableFrom(type) || OfflinePlayer.class.isAssignableFrom(type) || type == String.class;
    }

    private static boolean isNumericType(Class<?> type) {
        return type == double.class || type == Double.class || type == int.class || type == Integer.class || type == long.class || type == Long.class;
    }

    private static Object castNumeric(Class<?> targetType, BigDecimal amount) {
        if (targetType == int.class || targetType == Integer.class) {
            return amount.intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return amount.longValue();
        }
        return amount.doubleValue();
    }

    private static BigDecimal convertToBigDecimal(Object rawValue) {
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (rawValue != null) {
            return parseNumericString(String.valueOf(rawValue));
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal parseNumericString(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = rawValue
            .replace(",", "")
            .replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
