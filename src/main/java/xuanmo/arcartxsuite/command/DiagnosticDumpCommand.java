package xuanmo.arcartxsuite.command;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import xuanmo.arcartxsuite.ArcartXSuitePlugin;
import xuanmo.arcartxsuite.module.ModuleRegistry;

/**
 * /axs diagnostic 子命令：生成诊断包文件供客服排查。
 */
public final class DiagnosticDumpCommand {

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;

    private final ArcartXSuitePlugin plugin;

    public DiagnosticDumpCommand(ArcartXSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GRAY + "正在生成诊断包...");

        File dumpDir = new File(plugin.getDataFolder(), "diagnostics");
        if (!dumpDir.exists()) {
            dumpDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File dumpFile = new File(dumpDir, "diagnostic_" + timestamp + ".txt");

        try (PrintWriter pw = new PrintWriter(new FileWriter(dumpFile))) {
            writeHeader(pw, timestamp);
            writeServerInfo(pw);
            writeJvmInfo(pw);
            writePluginInfo(pw);
            writeModuleStatus(pw);
            writeRecentErrors(pw);
            pw.flush();
        } catch (IOException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "生成诊断包失败: " + e.getMessage());
            return true;
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + "诊断包已生成: " + ChatColor.WHITE + dumpFile.getAbsolutePath());
        sender.sendMessage(PREFIX + ChatColor.GRAY + "请将此文件发送给客服以协助排查问题。");
        return true;
    }

    private void writeHeader(PrintWriter pw, String timestamp) {
        pw.println("═══════════════════════════════════════════════════════════");
        pw.println("  ArcartXSuite Diagnostic Dump");
        pw.println("  Generated: " + timestamp);
        pw.println("═══════════════════════════════════════════════════════════");
        pw.println();
    }

    private void writeServerInfo(PrintWriter pw) {
        pw.println("── Server ──────────────────────────────────────────────");
        pw.println("  Platform: " + Bukkit.getName() + " " + Bukkit.getVersion());
        pw.println("  Bukkit API: " + Bukkit.getBukkitVersion());
        pw.println("  Online Mode: " + Bukkit.getOnlineMode());
        pw.println("  Players: " + Bukkit.getOnlinePlayers().size() + " / " + Bukkit.getMaxPlayers());
        pw.println("  Worlds: " + Bukkit.getWorlds().size());
        pw.println("  Plugins: " + Bukkit.getPluginManager().getPlugins().length);
        try {
            java.lang.reflect.Method getTPS = Bukkit.class.getMethod("getTPS");
            double[] tps = (double[]) getTPS.invoke(null);
            pw.println("  TPS: " + String.format("%.2f / %.2f / %.2f", tps[0], tps[1], tps[2]));
        } catch (Exception ignored) {
            pw.println("  TPS: unavailable (non-Paper)");
        }
        pw.println();
    }

    private void writeJvmInfo(PrintWriter pw) {
        pw.println("── JVM ─────────────────────────────────────────────────");
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        pw.println("  Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        pw.println("  VM: " + runtime.getVmName() + " " + runtime.getVmVersion());
        pw.println("  Uptime: " + formatDuration(runtime.getUptime()));
        pw.println("  Heap Used: " + formatBytes(memory.getHeapMemoryUsage().getUsed()));
        pw.println("  Heap Max: " + formatBytes(memory.getHeapMemoryUsage().getMax()));
        pw.println("  Non-Heap: " + formatBytes(memory.getNonHeapMemoryUsage().getUsed()));
        pw.println("  Processors: " + Runtime.getRuntime().availableProcessors());
        pw.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        pw.println();
    }

    private void writePluginInfo(PrintWriter pw) {
        pw.println("── ArcartXSuite ────────────────────────────────────────");
        pw.println("  Version: " + plugin.getDescription().getVersion());
        pw.println("  Data Folder: " + plugin.getDataFolder().getAbsolutePath());
        pw.println("  Packet Bridge: " + plugin.describePacketBridgeMode());
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry != null) {
            pw.println("  Modules Enabled: " + registry.countEnabled());
            pw.println("  Purgeable Modules: " + registry.purgeableModuleIds().size());
            if (registry.currencyManager() != null) {
                pw.println("  Currencies: " + registry.currencyManager().currencyIds().size());
            }
        } else {
            pw.println("  ModuleRegistry: NOT INITIALIZED");
        }
        pw.println();
    }

    private void writeModuleStatus(PrintWriter pw) {
        pw.println("── Module Status ───────────────────────────────────────");
        ModuleRegistry registry = plugin.getModuleRegistry();
        if (registry == null) {
            pw.println("  (ModuleRegistry unavailable)");
            pw.println();
            return;
        }
        Map<String, Boolean> status = registry.moduleStatusMap();
        for (Map.Entry<String, Boolean> entry : status.entrySet()) {
            pw.println("  " + (entry.getValue() ? "[OK]" : "[--]") + " " + entry.getKey());
        }
        if (status.isEmpty()) {
            pw.println("  (no modules loaded)");
        }
        pw.println();
    }

    private void writeRecentErrors(PrintWriter pw) {
        pw.println("── Environment ─────────────────────────────────────────");
        pw.println("  ArcartX Plugin: " + (Bukkit.getPluginManager().getPlugin("ArcartX") != null ? "PRESENT" : "MISSING"));
        pw.println("  PlaceholderAPI: " + (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null ? "PRESENT" : "MISSING"));
        pw.println("  Vault: " + (Bukkit.getPluginManager().getPlugin("Vault") != null ? "PRESENT" : "MISSING"));
        pw.println("  MythicMobs: " + (Bukkit.getPluginManager().getPlugin("MythicMobs") != null ? "PRESENT" : "MISSING"));
        pw.println("  PlayerPoints: " + (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null ? "PRESENT" : "MISSING"));
        pw.println();
        pw.println("── End of Diagnostic Dump ──────────────────────────────");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%dh %dm %ds", hours, minutes, secs);
    }
}
