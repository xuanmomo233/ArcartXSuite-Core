package xuanmo.arcartxsuite.api.config;

import java.util.Objects;

/**
 * 模块声明的"内置默认配置文件 → 玩家服务器对应文件"的同步规约。
 * <p>
 * 由 {@link xuanmo.arcartxsuite.api.AbstractAXSModule#additionalSyncResources()} 
 * 等回调返回，由宿主在模块启动前批量执行：
 * <ul>
 *     <li>目标文件不存在 → 拷贝模块 Jar 内的默认值</li>
 *     <li>目标文件已存在 → 按 {@link SyncPolicy} 合并、补缺、剪枝、备份</li>
 * </ul>
 *
 * @param resourcePath       模块 Jar 内默认资源的相对路径（如 {@code "ArcartXTitle.yml"}、
 *                           {@code "chat/channels/Global.yml"}）
 * @param targetRelativePath 目标文件相对于 {@code plugins/ArcartXSuite/} 的路径
 * @param policy             同步策略
 */
public record ConfigSyncSpec(String resourcePath, String targetRelativePath, SyncPolicy policy) {

    public ConfigSyncSpec {
        resourcePath = normalize(Objects.requireNonNull(resourcePath, "resourcePath"));
        targetRelativePath = normalize(Objects.requireNonNull(targetRelativePath, "targetRelativePath"));
        policy = Objects.requireNonNull(policy, "policy");
    }

    /** 资源路径与目标路径相同的快捷构造。 */
    public static ConfigSyncSpec of(String path, SyncPolicy policy) {
        return new ConfigSyncSpec(path, path, policy);
    }

    /** 资源路径与目标路径相同、采用严格策略的快捷构造。 */
    public static ConfigSyncSpec strict(String path) {
        return new ConfigSyncSpec(path, path, SyncPolicy.strict());
    }

    private static String normalize(String value) {
        return value.replace('\\', '/');
    }
}
