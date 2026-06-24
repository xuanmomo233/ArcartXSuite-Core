package xuanmo.arcartxsuite.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.ArcartXSuitePlugin;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.module.ModuleRegistry;

/**
 * /axs 主命令处理器（开源版）。
 * <p>
 * 仅提供 help / status / reload，其余子命令统一委托给 ModuleRegistry 中模块注册的命令处理器。
 */
public final class ArcartXSuiteCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final List<String> ROOT_ACTIONS = List.of("help", "status", "reload", "load", "unload", "config", "purge", "diagnostic", "migrate");

    private static final long PURGE_CONFIRM_TIMEOUT_MS = 10_000;

    private final ArcartXSuitePlugin plugin;
    private final ArcartXSuiteConfigSubCommand configSubCommand;
    private final DiagnosticDumpCommand diagnosticCommand;
    private String pendingPurgeKey;
    private long pendingPurgeTimestamp;

    public ArcartXSuiteCommand(ArcartXSuitePlugin plugin) {
        this.plugin = plugin;
        this.configSubCommand = new ArcartXSuiteConfigSubCommand(plugin);
        this.diagnosticCommand = new DiagnosticDumpCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arcartxsuite.admin")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "你没有权限执行这个命令。");
            return true;
        }

        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sendStatus(sender);
            return true;
        }
        if ("help".equalsIgnoreCase(args[0])) {
            sendHelp(sender, label);
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            return handleReload(sender, args);
        }
        if ("load".equalsIgnoreCase(args[0])) {
            return handleLoad(sender, args);
        }
        if ("unload".equalsIgnoreCase(args[0])) {
            return handleUnload(sender, args);
        }
        if ("config".equalsIgnoreCase(args[0])) {
            String[] subArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
            return configSubCommand.execute(sender, subArgs);
        }
        if ("purge".equalsIgnoreCase(args[0])) {
            return handlePurge(sender, args);
        }
        if ("migrate".equalsIgnoreCase(args[0])) {
            return handleMigrate(sender, args);
        }
        if ("diagnostic".equalsIgnoreCase(args[0])) {
            return diagnosticCommand.execute(sender);
        }

        // 委托给外部模块命令处理器
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry != null) {
            Optional<ModuleCommandHandler> handler = registry.getCommandHandler(args[0].toLowerCase(Locale.ROOT));
            if (handler.isPresent()) {
                return handler.get().onCommand(sender, label, args);
            }
        }

        sendUsage(sender, label);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(ROOT_ACTIONS);
            if (registry != null) {
                for (String id : registry.externalModuleIds()) {
                    if (!options.contains(id)) {
                        options.add(id);
                    }
                }
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && "reload".equalsIgnoreCase(args[0])) {
            List<String> options = new ArrayList<>();
            options.add("all");
            if (registry != null) {
                options.addAll(registry.externalModuleIds());
            }
            return filter(options, args[1]);
        }
        if (args.length == 2 && "load".equalsIgnoreCase(args[0])) {
            return filter(registry == null ? List.of() : registry.discoverableModuleIds(), args[1]);
        }
        if (args.length == 2 && "unload".equalsIgnoreCase(args[0])) {
            return filter(registry == null ? List.of() : registry.externalModuleIds(), args[1]);
        }
        if ("purge".equalsIgnoreCase(args[0]) && sender instanceof org.bukkit.command.ConsoleCommandSender) {
            if (args.length == 2) {
                List<String> playerOptions = new ArrayList<>();
                playerOptions.add("all");
                org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> playerOptions.add(p.getName()));
                return filter(playerOptions, args[1]);
            }
            if (args.length == 3 && registry != null) {
                List<String> moduleOptions = new ArrayList<>();
                moduleOptions.add("all");
                moduleOptions.addAll(registry.purgeableModuleIds());
                return filter(moduleOptions, args[2]);
            }
            return List.of();
        }
        if ("migrate".equalsIgnoreCase(args[0]) && sender instanceof org.bukkit.command.ConsoleCommandSender) {
            if (args.length == 2 && registry != null) {
                List<String> options = new ArrayList<>();
                options.add("all");
                options.addAll(registry.migratableModuleIds());
                return filter(options, args[1]);
            }
            if (args.length == 3) {
                return filter(List.of("sqlite-to-mysql", "mysql-to-sqlite"), args[2]);
            }
            if (args.length == 4) {
                return filter(List.of("true", "false"), args[3]);
            }
            return List.of();
        }
        if ("config".equalsIgnoreCase(args[0])) {
            String[] subArgs = new String[args.length - 1];
            System.arraycopy(args, 1, subArgs, 0, subArgs.length);
            return configSubCommand.tabComplete(subArgs);
        }
        if (registry != null && args.length >= 2) {
            Optional<ModuleCommandHandler> handler = registry.getCommandHandler(args[0].toLowerCase(Locale.ROOT));
            if (handler.isPresent()) {
                List<String> result = handler.get().onTabComplete(sender, args);
                if (result != null) {
                    return result;
                }
                if (args.length == 2) {
                    return filter(handler.get().actions(), args[1]);
                }
                return null;
            }
        }
        return List.of();
    }

    // ─── help / status / reload ───────────────────────────────

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "命令用法:");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " status" + ChatColor.GRAY + " - 查看模块状态");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " reload [all|<module>]" + ChatColor.GRAY + " - 重载模块（onDisable + onEnable）");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " load <module>" + ChatColor.GRAY + " - 热加载新模块（从 modules/ 扫描）");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " unload <module>" + ChatColor.GRAY + " - 热卸载模块（释放 ClassLoader）");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " config <子命令>" + ChatColor.GRAY + " - 智能配置体检");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " migrate <module|all> <direction> [overwrite]" + ChatColor.GRAY + " - 跨数据库一键无损迁移 (SQLite ↔ MySQL)");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " purge <玩家名|all> [模块ID|all]" + ChatColor.GRAY + " - 清除玩家模块数据 (控制台专用，10秒二次确认)");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " diagnostic" + ChatColor.GRAY + " - 生成诊断包到 diagnostics/ 目录");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "/" + label + " <module> [action]" + ChatColor.GRAY + " - 调用模块命令");
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry != null && !registry.externalModuleIds().isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "已加载模块: " + ChatColor.WHITE + String.join(", ", registry.externalModuleIds()));
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "ArcartXSuite 状态:");
        sender.sendMessage(PREFIX + ChatColor.GRAY + " - ArcartX 桥接: " + ChatColor.WHITE + plugin.describePacketBridgeMode());
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry != null && registry.currencyManager() != null) {
            int total = registry.currencyManager().currencyIds().size();
            sender.sendMessage(PREFIX + ChatColor.GRAY + " - 货币桥接: " + ChatColor.WHITE + "已注册 " + total + " 种货币");
        }
        if (registry == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + " - ModuleRegistry 未初始化");
            return;
        }
        Map<String, Boolean> status = registry.moduleStatusMap();
        sender.sendMessage(PREFIX + ChatColor.GRAY + " - 模块: 启用 " + ChatColor.GREEN + registry.countEnabled() + ChatColor.GRAY + " / 共 " + ChatColor.WHITE + status.size());
        for (Map.Entry<String, Boolean> entry : status.entrySet()) {
            sender.sendMessage(
                PREFIX + ChatColor.GRAY + "   - " + ChatColor.WHITE + entry.getKey()
                    + ": " + (entry.getValue() ? ChatColor.GREEN + "已启用" : ChatColor.GRAY + "未启用")
            );
        }
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "ModuleRegistry 未初始化。");
            return true;
        }
        if (args.length < 2 || "all".equalsIgnoreCase(args[1])) {
            registry.reloadAll();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "已重载全部模块。");
            return true;
        }
        String moduleId = args[1].toLowerCase(Locale.ROOT);
        boolean ok = registry.reloadModule(moduleId);
        sender.sendMessage(PREFIX + (ok ? ChatColor.GREEN + "已重载模块: " : ChatColor.RED + "重载失败: ") + moduleId);
        return true;
    }

    private boolean handleLoad(CommandSender sender, String[] args) {
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "ModuleRegistry 未初始化。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /axs load <moduleId>");
            List<String> available = registry.discoverableModuleIds();
            if (!available.isEmpty()) {
                sender.sendMessage(PREFIX + ChatColor.GRAY + "未加载模块: " + ChatColor.WHITE + String.join(", ", available));
            }
            return true;
        }
        String moduleId = args[1].toLowerCase(Locale.ROOT);
        sender.sendMessage(PREFIX + ChatColor.GRAY + "正在加载模块 " + ChatColor.WHITE + moduleId + ChatColor.GRAY + " ...");
        boolean ok = registry.loadModuleById(moduleId);
        sender.sendMessage(PREFIX + (ok
            ? ChatColor.GREEN + "已加载并启用模块: " + moduleId
            : ChatColor.RED + "加载失败: " + moduleId + ChatColor.GRAY + "（详情见控制台）"));
        return true;
    }

    private boolean handleUnload(CommandSender sender, String[] args) {
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "ModuleRegistry 未初始化。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /axs unload <moduleId>");
            return true;
        }
        String moduleId = args[1].toLowerCase(Locale.ROOT);
        sender.sendMessage(PREFIX + ChatColor.GRAY + "正在卸载模块 " + ChatColor.WHITE + moduleId + ChatColor.GRAY + " ...");
        boolean ok = registry.unloadModule(moduleId);
        sender.sendMessage(PREFIX + (ok
            ? ChatColor.GREEN + "已卸载模块: " + moduleId + ChatColor.GRAY + "（ClassLoader 已释放）"
            : ChatColor.RED + "卸载失败: " + moduleId + ChatColor.GRAY + "（详情见控制台）"));
        return true;
    }

    private boolean handlePurge(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "purge 命令仅允许从控制台执行。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /axs purge <玩家名|all> [模块ID|all]");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "使用 all 代表全部玩家或全部模块。省略模块ID等同 all。");
            return true;
        }
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "模块注册表未初始化。");
            return true;
        }

        String playerArg = args[1];
        String moduleArg = args.length >= 3 ? args[2] : "all";
        boolean allPlayers = "all".equalsIgnoreCase(playerArg);
        boolean allModules = "all".equalsIgnoreCase(moduleArg);

        if (!allModules && registry.purgeableModuleIds().stream()
                .noneMatch(id -> id.equalsIgnoreCase(moduleArg))) {
            sender.sendMessage(PREFIX + ChatColor.RED + "模块 \"" + moduleArg + "\" 未注册数据清除能力。");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "可用: " + String.join(", ", registry.purgeableModuleIds()));
            return true;
        }

        java.util.UUID uuid = null;
        if (!allPlayers) {
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(playerArg);
            if (target.getUniqueId() == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "找不到玩家: " + playerArg);
                return true;
            }
            uuid = target.getUniqueId();
        }

        String confirmKey = playerArg.toLowerCase(Locale.ROOT) + ":" + moduleArg.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (confirmKey.equals(pendingPurgeKey) && (now - pendingPurgeTimestamp) < PURGE_CONFIRM_TIMEOUT_MS) {
            pendingPurgeKey = null;
            pendingPurgeTimestamp = 0;
        } else {
            pendingPurgeKey = confirmKey;
            pendingPurgeTimestamp = now;
            String playerScope = allPlayers ? "全部玩家" : playerArg;
            String moduleScope = allModules ? "所有模块" : moduleArg;
            sender.sendMessage(PREFIX + ChatColor.RED + "█ 警告: 即将清除 " + ChatColor.WHITE + playerScope
                + ChatColor.RED + " 在 " + ChatColor.WHITE + moduleScope + ChatColor.RED + " 中的数据！此操作不可撤销。");
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "10 秒内再次输入相同命令以确认执行。");
            return true;
        }

        String playerScope = allPlayers ? "全部玩家" : (playerArg + " (" + uuid + ")");
        String moduleScope = allModules ? "所有模块" : ("模块 " + moduleArg);
        sender.sendMessage(PREFIX + ChatColor.GRAY + "正在清除 " + ChatColor.WHITE + playerScope
            + ChatColor.GRAY + " 在 " + ChatColor.WHITE + moduleScope + ChatColor.GRAY + " 中的数据...");

        final java.util.UUID finalUuid = uuid;
        final String fPlayerScope = playerScope;
        final String fModuleScope = moduleScope;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Integer> results;
            if (allPlayers) {
                results = allModules
                    ? registry.purgeAllPlayerData()
                    : registry.purgeAllPlayerData(moduleArg);
            } else {
                results = allModules
                    ? registry.purgePlayerData(finalUuid)
                    : registry.purgePlayerData(finalUuid, moduleArg);
            }
            writePurgeAuditLog(fPlayerScope, fModuleScope, results);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (results.isEmpty()) {
                    sender.sendMessage(PREFIX + ChatColor.YELLOW + "没有模块注册数据清除能力。");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "清除完成:");
                    for (var entry : results.entrySet()) {
                        String status = entry.getValue() >= 0
                            ? ChatColor.WHITE + String.valueOf(entry.getValue()) + " 行"
                            : ChatColor.RED + "失败";
                        sender.sendMessage(PREFIX + "  " + ChatColor.AQUA + entry.getKey() + ChatColor.GRAY + " → " + status);
                    }
                }
            });
        });
        return true;
    }

    private void writePurgeAuditLog(String playerScope, String moduleScope, Map<String, Integer> results) {
        try {
            java.io.File logDir = new java.io.File(plugin.getDataFolder(), "purge-logs");
            if (!logDir.exists()) logDir.mkdirs();
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            java.io.File logFile = new java.io.File(logDir, "purge_" + timestamp + ".log");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(logFile))) {
                pw.println("ArcartXSuite Purge Audit Log");
                pw.println("Timestamp: " + java.time.LocalDateTime.now());
                pw.println("Target Player: " + playerScope);
                pw.println("Target Module: " + moduleScope);
                pw.println("─────────────────────────────────");
                for (var entry : results.entrySet()) {
                    pw.println("  " + entry.getKey() + " → " + (entry.getValue() >= 0 ? entry.getValue() + " rows deleted" : "FAILED"));
                }
                pw.println("─────────────────────────────────");
                int total = results.values().stream().mapToInt(v -> Math.max(v, 0)).sum();
                pw.println("Total rows affected: " + total);
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("[Purge] 审计日志写入失败: " + e.getMessage());
        }
    }

    private boolean handleMigrate(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "migrate 命令仅允许从控制台执行。");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /axs migrate <模块ID|all> <sqlite-to-mysql|mysql-to-sqlite> [overwrite=true]");
            return true;
        }

        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "模块注册表未初始化。");
            return true;
        }

        String moduleArg = args[1].toLowerCase(Locale.ROOT);
        String direction = args[2].toLowerCase(Locale.ROOT);
        boolean overwrite = args.length < 4 || Boolean.parseBoolean(args[3]);

        boolean toMysql;
        if ("sqlite-to-mysql".equals(direction)) {
            toMysql = true;
        } else if ("mysql-to-sqlite".equals(direction)) {
            toMysql = false;
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "未知的迁移方向: " + direction + "。可用: sqlite-to-mysql, mysql-to-sqlite");
            return true;
        }

        List<String> targetIds = new ArrayList<>();
        if ("all".equals(moduleArg)) {
            targetIds.addAll(registry.migratableModuleIds());
        } else {
            if (!registry.migratableModuleIds().contains(moduleArg)) {
                sender.sendMessage(PREFIX + ChatColor.RED + "模块 \"" + moduleArg + "\" 未注册数据库迁移能力，或当前不可用。");
                sender.sendMessage(PREFIX + ChatColor.GRAY + "支持迁移的模块: " + String.join(", ", registry.migratableModuleIds()));
                return true;
            }
            targetIds.add(moduleArg);
        }

        if (targetIds.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "没有检测到任何具有持久化存储并可迁移的模块。");
            return true;
        }

        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== 开始执行跨源数据一键迁移 ===");
        sender.sendMessage(PREFIX + ChatColor.GRAY + "迁移方向: " + ChatColor.YELLOW + direction.toUpperCase());
        sender.sendMessage(PREFIX + ChatColor.GRAY + "覆盖模式: " + ChatColor.YELLOW + overwrite);
        sender.sendMessage(PREFIX + ChatColor.GRAY + "目标模块: " + ChatColor.YELLOW + String.join(", ", targetIds));

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String id : targetIds) {
                Optional<xuanmo.arcartxsuite.api.capability.DatabaseMigratable> opt = registry.getMigratable(id);
                if (opt.isEmpty()) {
                    sendOnMain(sender, PREFIX + ChatColor.RED + "模块 [" + id + "] 迁移接口获取失败，跳过。");
                    continue;
                }
                xuanmo.arcartxsuite.api.capability.DatabaseMigratable migratable = opt.get();
                xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDesc = migratable.currentDescriptor();

                if (currentDesc.isMysql() == toMysql) {
                    sendOnMain(sender, PREFIX + ChatColor.RED + "模块 [" + id + "] 无法从其自身进行迁移 (源方言 "
                        + (currentDesc.isMysql() ? "MySQL" : "SQLite") + " 与目标方言相同！)");
                    sendOnMain(sender, PREFIX + ChatColor.GRAY + "请检查该模块的 config.yml 中的数据库启用状态是否与搬迁方向相符。");
                    continue;
                }

                xuanmo.arcartxsuite.api.storage.StorageDescriptor targetDesc = new xuanmo.arcartxsuite.api.storage.StorageDescriptor(
                    toMysql,
                    currentDesc.host(),
                    currentDesc.port(),
                    currentDesc.database(),
                    currentDesc.username(),
                    currentDesc.password(),
                    currentDesc.poolSize(),
                    currentDesc.sqliteFileName(),
                    currentDesc.tablePrefix()
                );

                sendOnMain(sender, PREFIX + ChatColor.GRAY + "正在迁移模块 [" + ChatColor.YELLOW + id + ChatColor.GRAY + "] 的所有数据库表 ...");
                try {
                    xuanmo.arcartxsuite.api.storage.MigrationResult result = migratable.migrateDatabase(targetDesc, overwrite);
                    if (result.isSuccess()) {
                        sendOnMain(sender, PREFIX + ChatColor.GREEN + "模块 [" + id + "] 迁移成功！共迁移 "
                            + result.tablesMigrated() + " 张表，共复制 " + result.rowsCopied() + " 行数据。");
                        result.tableRows().forEach((table, rows) ->
                            sendOnMain(sender, PREFIX + ChatColor.GRAY + "  - 表 " + ChatColor.AQUA + table
                                + ChatColor.GRAY + ": " + ChatColor.WHITE + rows + " 行"));
                    } else {
                        sendOnMain(sender, PREFIX + ChatColor.RED + "模块 [" + id + "] 迁移未完全成功：");
                        for (String err : result.errors()) {
                            sendOnMain(sender, PREFIX + ChatColor.RED + "  - " + err);
                        }
                    }
                } catch (Exception e) {
                    sendOnMain(sender, PREFIX + ChatColor.RED + "模块 [" + id + "] 迁移遭遇严重异常: " + e.getMessage());
                }
            }
            sendOnMain(sender, PREFIX + ChatColor.GOLD + "=== 一键跨源迁移执行完毕 ===");
        });
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /" + label + " help|status|reload|load|unload|config|purge|diagnostic|migrate|<module>");
    }

    // ─── 工具 ─────────────────────────────────────────────────

    private static List<String> filter(List<String> source, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return source;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : source) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private void sendOnMain(CommandSender sender, String message) {
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(message);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
    }
}
