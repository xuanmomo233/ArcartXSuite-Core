package xuanmo.arcartxsuite.api;

import org.jetbrains.annotations.Nullable;

/**
 * ArcartX UI 注册结果。
 *
 * @param runtimeUiId    运行时使用的 UI id（已规范化）
 * @param registeredUiId 实际注册到 ArcartX 的 UI id（注销时使用），
 *                       如果未注册则为 null
 */
public record UiBinding(String runtimeUiId, @Nullable String registeredUiId) {
}
