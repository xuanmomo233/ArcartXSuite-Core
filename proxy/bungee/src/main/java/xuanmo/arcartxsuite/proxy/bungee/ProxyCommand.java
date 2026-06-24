package xuanmo.arcartxsuite.proxy.bungee;

import java.util.List;
import java.util.logging.Logger;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;
import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

public class ProxyCommand extends Command implements TabExecutor {

    private final ArcartXSuiteBungee plugin;
    private final ProxyConfig config;
    private final Logger logger;

    public ProxyCommand(ArcartXSuiteBungee plugin, ProxyConfig config, Logger logger) {
        super("axsproxy", "arcartxsuite.proxy.admin");
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                config.loadDefaults();
                sender.sendMessage(ChatColor.GREEN + "[ArcartXSuite Proxy] 配置已重载。");
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder();
                sb.append(ChatColor.GOLD).append("===== ArcartXSuite Proxy 状态 =====\n");
                sb.append(ChatColor.GRAY).append("拒绝离线: ").append(ChatColor.WHITE).append(config.denyOffline()).append("\n");
                sb.append(ChatColor.GRAY).append("调试模式: ").append(ChatColor.WHITE).append(config.debug()).append("\n");
                sb.append(ChatColor.GRAY).append("Yggdrasil 源:\n");
                List<YggdrasilSource> sources = config.sources();
                if (sources.isEmpty()) {
                    sb.append(ChatColor.RED).append("  (无)\n");
                } else {
                    for (YggdrasilSource s : sources) {
                        sb.append(ChatColor.GREEN).append("  ").append(s.name())
                          .append(ChatColor.GRAY).append(" (").append(s.apiUrl()).append(")\n");
                    }
                }
                sender.sendMessage(sb.toString());
            }
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "status", "help");
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append("===== ArcartXSuite Proxy 命令 =====\n");
        sb.append(ChatColor.GRAY).append("/axsproxy reload ").append(ChatColor.WHITE).append("- 重载配置\n");
        sb.append(ChatColor.GRAY).append("/axsproxy status ").append(ChatColor.WHITE).append("- 查看状态\n");
        sb.append(ChatColor.GRAY).append("/axsproxy help  ").append(ChatColor.WHITE).append("- 显示帮助\n");
        sender.sendMessage(sb.toString());
    }
}
