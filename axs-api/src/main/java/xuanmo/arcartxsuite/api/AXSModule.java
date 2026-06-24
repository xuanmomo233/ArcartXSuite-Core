package xuanmo.arcartxsuite.api;

import java.util.List;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;

/**
 * 可插拔模块的生命周期接口。
 * <p>
 * 每个独立模块 Jar 需提供一个实现此接口的主类，
 * 并在 {@code module.yml} 的 {@code main} 字段中声明。
 */
public interface AXSModule {

    /**
     * 模块描述符，提供元数据信息。
     */
    ModuleDescriptor descriptor();

    /**
     * 模块声明的配置同步与诊断规约列表。
     * <p>
     * 默认返回空列表，表示模块不参与智能配置体检系统。
     * 模块可返回多个 {@link ModuleConfigSpec}，用于覆盖主 yml 与附属 yml。
     * 引擎会在 {@link #onEnable} 之前以 dry-run 方式跑结构 / 类型 / 迁移 / 校验诊断，
     * 报告写入 {@code plugins/ArcartXSuite/diagnosis/}，由玩家通过命令显式应用。
     */
    default List<ModuleConfigSpec> configSpecs() {
        return List.of();
    }

    /**
     * 启用模块。宿主在密码验证和依赖检查通过后调用此方法。
     *
     * @param context 模块上下文，提供基础设施访问
     * @return {@code true} 表示启动成功
     * @throws Exception 启动失败时抛出
     */
    boolean onEnable(ModuleContext context) throws Exception;

    /**
     * 禁用模块，释放所有资源（事件、任务、连接池等）。
     */
    void onDisable();

    /**
     * 热重载模块配置，不卸载 ClassLoader。
     *
     * @throws Exception 重载失败时抛出
     */
    void onReload() throws Exception;

    /**
     * 当前模块是否处于正常运行状态。
     */
    boolean isReady();
}
