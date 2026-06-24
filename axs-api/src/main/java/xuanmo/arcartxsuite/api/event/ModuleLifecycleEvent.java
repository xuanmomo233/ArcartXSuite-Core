package xuanmo.arcartxsuite.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 模块生命周期事件 —— 模块加载/卸载/重载时触发。
 * <p>
 * 第三方插件可通过标准 Bukkit 事件机制监听此事件，
 * 以便在 ArcartXSuite 模块状态变化时执行相应逻辑。
 *
 * <pre>{@code
 * @EventHandler
 * public void onModuleLifecycle(ModuleLifecycleEvent event) {
 *     if (event.phase() == ModuleLifecycleEvent.Phase.ENABLED
 *         && "warehouse".equals(event.moduleId())) {
 *         // warehouse 模块已加载，可以查找其 capability
 *     }
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
@ApiStability.Stable
public final class ModuleLifecycleEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Phase {
        /** 模块即将启用（onEnable 之前） */
        ENABLING,
        /** 模块已成功启用 */
        ENABLED,
        /** 模块启用失败 */
        ENABLE_FAILED,
        /** 模块即将禁用（onDisable 之前） */
        DISABLING,
        /** 模块已禁用 */
        DISABLED,
        /** 模块开始重载 */
        RELOADING,
        /** 模块重载完成 */
        RELOADED
    }

    private final String moduleId;
    private final String moduleName;
    private final Phase phase;
    private final AXSModule module;

    public ModuleLifecycleEvent(@NotNull String moduleId, @NotNull String moduleName,
                                @NotNull Phase phase, @NotNull AXSModule module) {
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.phase = phase;
        this.module = module;
    }

    /** 模块 id（如 "warehouse", "chat"） */
    @NotNull public String moduleId() { return moduleId; }

    /** 模块显示名称 */
    @NotNull public String moduleName() { return moduleName; }

    /** 生命周期阶段 */
    @NotNull public Phase phase() { return phase; }

    /** 模块实例（ENABLE_FAILED 时可能处于不完整状态） */
    @NotNull public AXSModule module() { return module; }

    @Override
    @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
