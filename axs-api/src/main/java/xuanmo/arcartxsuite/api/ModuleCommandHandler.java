package xuanmo.arcartxsuite.api;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 模块可选实现的命令处理接口。
 * <p>
 * 实现此接口的模块将自动被宿主命令系统识别，
 * 在 {@code /axs <moduleId> ...} 下注册子命令。
 */
public interface ModuleCommandHandler {

    /**
     * 模块子命令 id（与 module.yml 中的 id 一致）。
     */
    String commandId();

    /**
     * 模块子命令别名列表（缩写），如 "ess" 可作为 "essentials" 的别名。
     * 默认返回空列表。
     */
    default List<String> commandAliases() {
        return List.of();
    }

    /**
     * 该模块支持的子命令动作列表（用于 Tab 补全第 2 级）。
     * 默认返回 help / status / reload。
     */
    default List<String> actions() {
        return List.of("help", "status", "reload");
    }

    /**
     * 执行子命令。
     *
     * @param sender 命令发送者
     * @param label  根命令标签
     * @param args   完整参数数组（args[0] 为模块 id，args[1] 开始为子命令）
     * @return true 表示已处理
     */
    boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args);

    /**
     * Tab 补全。
     *
     * @param sender 命令发送者
     * @param args   完整参数数组
     * @return 补全列表，null 表示无补全
     */
    @Nullable
    default List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return null;
    }
}
