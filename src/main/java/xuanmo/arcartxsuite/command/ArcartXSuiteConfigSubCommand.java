package xuanmo.arcartxsuite.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import xuanmo.arcartxsuite.ArcartXSuitePlugin;
import xuanmo.arcartxsuite.api.config.ConfigDiagnosisReport;
import xuanmo.arcartxsuite.api.config.ConfigIssue;
import xuanmo.arcartxsuite.api.config.ConfigIssueSeverity;
import xuanmo.arcartxsuite.config.diagnostic.ConfigDiagnosisStore;
import xuanmo.arcartxsuite.config.diagnostic.ConfigDiagnosticEngine;

/**
 * /arcartxsuite config &lt;子命令&gt; 处理器。
 * <p>
 * 子命令：status / diagnose / preview / apply [--force] / rollback [--to ts]
 */
public final class ArcartXSuiteConfigSubCommand {

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final List<String> SUB_ACTIONS = List.of("status", "diagnose", "preview", "apply", "rollback");

    private final ArcartXSuitePlugin plugin;

    public ArcartXSuiteConfigSubCommand(ArcartXSuitePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 处理 {@code /arcartxsuite config <args...>}.
     *
     * @param subArgs 已剥离 {@code config} 之后的参数
     */
    public boolean execute(CommandSender sender, String[] subArgs) {
        if (subArgs.length == 0 || "help".equalsIgnoreCase(subArgs[0])) {
            sendHelp(sender);
            return true;
        }
        String action = subArgs[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "status" -> handleStatus(sender);
            case "diagnose" -> handleDiagnose(sender, tail(subArgs));
            case "preview" -> handlePreview(sender, tail(subArgs));
            case "apply" -> handleApply(sender, tail(subArgs));
            case "rollback" -> handleRollback(sender, tail(subArgs));
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    public List<String> tabComplete(String[] subArgs) {
        if (subArgs.length == 1) {
            return filter(SUB_ACTIONS, subArgs[0]);
        }
        if (subArgs.length == 2) {
            String action = subArgs[0].toLowerCase(Locale.ROOT);
            if (action.equals("diagnose") || action.equals("preview")
                || action.equals("apply") || action.equals("rollback")) {
                return filter(ownerIds(), subArgs[1]);
            }
        }
        if (subArgs.length == 3 && "apply".equalsIgnoreCase(subArgs[0])) {
            return filter(List.of("--force"), subArgs[2]);
        }
        return List.of();
    }

    // ─── 子命令实现 ────────────────────────────────────────────

    private boolean handleStatus(CommandSender sender) {
        ConfigDiagnosticEngine engine = plugin.getConfigDiagnosticEngine();
        ConfigDiagnosisStore store = plugin.getConfigDiagnosisStore();
        if (engine == null || store == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "诊断引擎未初始化。");
            return true;
        }
        sender.sendMessage(PREFIX + ChatColor.GRAY + "会话: " + ChatColor.WHITE + engine.sessionTimestamp());
        List<ConfigDiagnosisStore.Entry> all = store.all();
        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "尚无诊断结果。运行 /arcartxsuite config diagnose 启动一次。");
            return true;
        }
        long info = 0, warn = 0, err = 0;
        for (var e : all) {
            info += e.report().countOf(ConfigIssueSeverity.INFO);
            warn += e.report().countOf(ConfigIssueSeverity.WARN);
            err += e.report().countOf(ConfigIssueSeverity.ERROR);
        }
        sender.sendMessage(PREFIX + ChatColor.GRAY + "目标: " + ChatColor.WHITE + all.size()
            + ChatColor.GRAY + ", "
            + ChatColor.RED + err + " ERROR " + ChatColor.RESET + "/ "
            + ChatColor.YELLOW + warn + " WARN " + ChatColor.RESET + "/ "
            + ChatColor.AQUA + info + " INFO");
        for (var e : all) {
            ConfigDiagnosisReport r = e.report();
            sender.sendMessage(PREFIX + ChatColor.GRAY + "  - " + ChatColor.WHITE + e.spec().ownerId()
                + ": " + ChatColor.RED + r.countOf(ConfigIssueSeverity.ERROR) + "E "
                + ChatColor.YELLOW + r.countOf(ConfigIssueSeverity.WARN) + "W "
                + ChatColor.AQUA + r.countOf(ConfigIssueSeverity.INFO) + "I"
                + (r.proposedFile() == null ? "" : ChatColor.GRAY + "  proposal: " + r.proposedFile().getFileName()));
        }
        return true;
    }

    private boolean handleDiagnose(CommandSender sender, String[] args) {
        if (plugin.getConfigDiagnosticEngine() == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "诊断引擎未初始化。");
            return true;
        }
        plugin.runConfigDiagnosis(args.length > 0 ? args[0] : null, true);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "诊断已完成。报告: "
            + plugin.getConfigDiagnosticEngine().diagnosisRoot().getAbsolutePath());
        return handleStatus(sender);
    }

    private boolean handlePreview(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /arcartxsuite config preview <ownerId>");
            return true;
        }
        ConfigDiagnosisStore store = plugin.getConfigDiagnosisStore();
        if (store == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "诊断引擎未初始化。");
            return true;
        }
        Optional<ConfigDiagnosisStore.Entry> opt = store.get(args[0]);
        if (opt.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "未找到 " + args[0] + " 的诊断结果。");
            return true;
        }
        ConfigDiagnosisReport report = opt.get().report();
        sender.sendMessage(PREFIX + ChatColor.GRAY + "===== " + args[0] + " =====");
        if (report.issues().isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "无 issue。");
            return true;
        }
        for (ConfigIssue issue : report.issues()) {
            ChatColor color = switch (issue.severity()) {
                case ERROR -> ChatColor.RED;
                case WARN -> ChatColor.YELLOW;
                case INFO -> ChatColor.AQUA;
            };
            String path = issue.configPath().isEmpty() ? "(全局)" : issue.configPath();
            sender.sendMessage(PREFIX + color + "[" + issue.severity() + "] "
                + ChatColor.WHITE + path + ChatColor.GRAY + " - " + issue.message());
            if (issue.currentValue() != null || issue.suggestedValue() != null) {
                sender.sendMessage(PREFIX + ChatColor.DARK_GRAY + "    现值: " + issue.currentValue()
                    + " -> 建议: " + issue.suggestedValue());
            }
        }
        if (report.proposedFile() != null) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Proposal 文件: " + report.proposedFile());
        }
        return true;
    }

    private boolean handleApply(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /arcartxsuite config apply <ownerId> [--force]");
            return true;
        }
        boolean force = args.length > 1 && "--force".equalsIgnoreCase(args[1]);
        ConfigDiagnosisStore store = plugin.getConfigDiagnosisStore();
        ConfigDiagnosticEngine engine = plugin.getConfigDiagnosticEngine();
        if (store == null || engine == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "诊断引擎未初始化。");
            return true;
        }
        Optional<ConfigDiagnosisStore.Entry> opt = store.get(args[0]);
        if (opt.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "未找到 " + args[0] + " 的诊断结果。");
            return true;
        }
        var entry = opt.get();
        var result = engine.apply(entry.spec(), entry.report(), force);
        sender.sendMessage(PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
        if (result.success()) {
            // 应用后重跑一遍诊断刷新结果
            plugin.runConfigDiagnosis(args[0], true);
        }
        return true;
    }

    private boolean handleRollback(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /arcartxsuite config rollback <ownerId> [--to <timestamp>]");
            return true;
        }
        String timestamp = null;
        for (int i = 1; i < args.length; i++) {
            if ("--to".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                timestamp = args[i + 1];
                break;
            }
        }
        ConfigDiagnosisStore store = plugin.getConfigDiagnosisStore();
        ConfigDiagnosticEngine engine = plugin.getConfigDiagnosticEngine();
        if (store == null || engine == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "诊断引擎未初始化。");
            return true;
        }
        Optional<ConfigDiagnosisStore.Entry> opt = store.get(args[0]);
        if (opt.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "未找到 " + args[0] + " 的诊断结果。");
            return true;
        }
        var result = engine.rollback(opt.get().spec(), timestamp);
        sender.sendMessage(PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
        if (result.success()) {
            plugin.runConfigDiagnosis(args[0], true);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "智能配置体检命令:");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  config status" + ChatColor.GRAY + " - 当前会话诊断概况");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  config diagnose [ownerId]" + ChatColor.GRAY + " - 重新跑一次诊断");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  config preview <ownerId>" + ChatColor.GRAY + " - 控制台输出 issue 详情");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  config apply <ownerId> [--force]" + ChatColor.GRAY + " - 应用 proposal");
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "  config rollback <ownerId> [--to <timestamp>]" + ChatColor.GRAY + " - 还原备份");
    }

    private List<String> ownerIds() {
        ConfigDiagnosisStore store = plugin.getConfigDiagnosisStore();
        if (store == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (var e : store.all()) {
            ids.add(e.spec().ownerId());
        }
        return ids;
    }

    private static String[] tail(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }

    private static List<String> filter(List<String> source, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(source);
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : source) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(s);
            }
        }
        return out;
    }
}
