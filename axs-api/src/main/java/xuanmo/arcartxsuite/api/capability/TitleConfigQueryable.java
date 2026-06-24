package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.Nullable;

/**
 * 跨模块称号配置查询能力。
 * <p>
 * 由 Title 模块注册，QuestGps 等模块通过 capability 查询称号信息，
 * 避免直接依赖 Title 模块的内部配置类。
 */
public interface TitleConfigQueryable {

    /**
     * 根据称号 ID 查询称号信息。
     *
     * @param titleId 称号 ID
     * @return 称号信息，未找到时返回 null
     */
    @Nullable TitleInfo queryTitle(String titleId);

    record TitleInfo(String displayName, String qualityName, String description) {}
}
