package xuanmo.arcartxsuite.api.bridge;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 稳定性标记注解集合。
 * <p>
 * 用于标记 {@code axs-api} 中的接口 / 类 / 方法的稳定性级别，
 * 帮助第三方模块开发者了解哪些 API 可以安全依赖。
 *
 * @since 1.1.0
 */
public final class ApiStability {

    private ApiStability() {}

    /**
     * 稳定 API —— 向后兼容，不会在小版本中破坏。
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface Stable {}

    /**
     * 实验性 API —— 可能在未来版本中修改或移除，不保证向后兼容。
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface Experimental {}

    /**
     * 内部 API —— 仅供 ArcartXSuite 内部模块使用，第三方不应依赖。
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface Internal {}

    /**
     * 已弃用 API —— 将在下一个大版本中移除。
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    public @interface Deprecated {
        /** 替代方案说明 */
        String replacedBy() default "";
        /** 预计移除版本 */
        String removeIn() default "";
    }
}
