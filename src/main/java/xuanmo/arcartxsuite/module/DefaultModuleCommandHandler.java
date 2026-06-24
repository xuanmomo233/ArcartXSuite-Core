package xuanmo.arcartxsuite.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/**
 * 为没有自定义 {@link ModuleCommandHandler} 的模块自动生成的默认命令处理器。
 * <p>
 * 支持 {@code /axs <module> help|status|reload}。
 */
final class DefaultModuleCommandHandler implements ModuleCommandHandler {

    static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    private static final List<String> DEFAULT_ACTIONS = List.of("help", "status", "reload");

    private final ModuleDescriptor descriptor;

    DefaultModuleCommandHandler(ModuleDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public String commandId() {
        return descriptor.id();
    }

    @Override
    public List<String> actions() {
        return DEFAULT_ACTIONS;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sender.sendMessage(PREFIX + ChatColor.GOLD + descriptor.name() + " v" + descriptor.version() + ChatColor.GREEN + " 运行中");
            case "reload" -> sender.sendMessage(PREFIX + ChatColor.YELLOW + "请使用 /" + label + " reload " + descriptor.id());
            default -> sender.sendMessage(PREFIX + ChatColor.YELLOW + "用法: /" + label + " " + descriptor.id() + " help|status|reload");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(DEFAULT_ACTIONS, args[1]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " " + descriptor.id();
        sender.sendMessage(PREFIX + ChatColor.GOLD + descriptor.name() + " v" + descriptor.version());
        sender.sendMessage(PREFIX + ChatColor.GRAY + cmd + " status" + ChatColor.WHITE + " - 查看模块状态。");
        sender.sendMessage(PREFIX + ChatColor.GRAY + cmd + " reload" + ChatColor.WHITE + " - 重载模块配置。");
    }

    static List<String> filter(List<String> candidates, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
