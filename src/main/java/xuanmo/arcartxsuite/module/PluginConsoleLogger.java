package xuanmo.arcartxsuite.module;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * 模块专用 Logger，绕开 Bukkit / JUL 默认 handler，直接把日志写到控制台并附带统一前缀，
 * 保证所有 ArcartXSuite 输出都以 {@code ◆ ArcartXSuite | <LEVEL>: [moduleId] ...} 的形式连续呈现。
 *
 * <p>关键点：
 * <ul>
 *   <li>{@code setUseParentHandlers(false)} 阻止 JUL 走默认 handler 输出 {@code [AXS-xxx] INFO: ...} 格式。</li>
 *   <li>重写 {@link #log(LogRecord)} 把 INFO/WARNING/SEVERE 转交到 {@code Bukkit.getConsoleSender()}。</li>
 *   <li>携带模块标签 {@code [moduleId]}，便于在统一前缀后区分来源。</li>
 * </ul>
 */
public final class PluginConsoleLogger extends Logger {

    /**
     * 与 {@code ArcartXSuitePlugin.CONSOLE_PREFIX} 保持一致。此处复制一份避免对 axs-core 主类形成强耦合。
     */
    private static final String CONSOLE_PREFIX =
        ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;

    private final String moduleTag;

    public PluginConsoleLogger(String name, String moduleId) {
        super(name, null);
        // moduleId 为 null 或空时，不显示标签，保持统一前缀 ◆ ArcartXSuite | INFO:
        this.moduleTag = (moduleId == null || moduleId.isBlank())
            ? ""
            : ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + moduleId + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;
        setUseParentHandlers(false);
        setLevel(Level.ALL);
    }

    @Override
    public void log(LogRecord record) {
        if (record == null) return;
        Level level = record.getLevel();
        if (level == null) level = Level.INFO;

        String message = formatMessage(record);
        Throwable thrown = record.getThrown();

        int lvl = level.intValue();
        if (lvl >= Level.SEVERE.intValue()) {
            send(ChatColor.RED + "ERROR: " + message);
        } else if (lvl >= Level.WARNING.intValue()) {
            send(ChatColor.YELLOW + "WARN: " + message);
        } else if (lvl >= Level.INFO.intValue()) {
            send("INFO: " + message);
        }
        // FINE / FINER / FINEST 默认不输出（与 Bukkit 默认行为一致）

        if (thrown != null) {
            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                thrown.printStackTrace(pw);
            }
            for (String line : sw.toString().split("\\R")) {
                send(ChatColor.RED + "  " + line);
            }
        }
    }

    private void send(String body) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + moduleTag + body);
    }

    private static String formatMessage(LogRecord record) {
        String msg = record.getMessage();
        if (msg == null) return "";
        Object[] params = record.getParameters();
        if (params == null || params.length == 0) return msg;
        try {
            return java.text.MessageFormat.format(msg, params);
        } catch (IllegalArgumentException ignored) {
            return msg;
        }
    }
}
