package xuanmo.arcartxsuite.proxy.velocity;

import java.util.List;
import java.util.logging.Logger;

import com.velocitypowered.api.command.SimpleCommand;

import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;
import xuanmo.arcartxsuite.proxy.common.model.YggdrasilSource;

/**
 * Velocity 代理端管理命令 /axsproxy。
 */
public class ProxyCommand implements SimpleCommand {

    private final ArcartXSuiteVelocity plugin;
    private final ProxyConfig config;
    private final Logger logger;

    public ProxyCommand(ArcartXSuiteVelocity plugin, ProxyConfig config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            sendHelp(invocation);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                config.loadDefaults();
                invocation.source().sendMessage(net.kyori.adventure.text.Component.text("[ArcartXSuite Proxy] 配置已重载。"));
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("§6===== ArcartXSuite Proxy 状态 =====\n");
                sb.append("§7拒绝离线: §f").append(config.denyOffline()).append("\n");
                sb.append("§7调试模式: §f").append(config.debug()).append("\n");
                sb.append("§7Yggdrasil 源:\n");
                List<YggdrasilSource> sources = config.sources();
                if (sources.isEmpty()) {
                    sb.append("  §c(无)\n");
                } else {
                    for (YggdrasilSource s : sources) {
                        sb.append("  §a").append(s.name())
                          .append(" §7(").append(s.apiUrl()).append(")\n");
                    }
                }
                invocation.source().sendMessage(net.kyori.adventure.text.Component.text(sb.toString()));
            }
            case "help" -> sendHelp(invocation);
            default -> sendHelp(invocation);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of("reload", "status", "help");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("arcartxsuite.proxy.admin");
    }

    private void sendHelp(Invocation invocation) {
        StringBuilder sb = new StringBuilder();
        sb.append("§6===== ArcartXSuite Proxy 命令 =====\n");
        sb.append("§7/axsproxy reload §f- 重载配置\n");
        sb.append("§7/axsproxy status §f- 查看状态\n");
        sb.append("§7/axsproxy help  §f- 显示帮助\n");
        invocation.source().sendMessage(net.kyori.adventure.text.Component.text(sb.toString()));
    }
}
