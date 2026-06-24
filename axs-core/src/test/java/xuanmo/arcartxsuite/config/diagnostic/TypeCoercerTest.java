package xuanmo.arcartxsuite.config.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.config.ValueType;

@DisplayName("TypeCoercer 类型转换器")
class TypeCoercerTest {

    @Nested
    @DisplayName("INT 转换")
    class IntCoercion {

        @Test
        @DisplayName("整数保持不变")
        void integerStaysSame() {
            assertEquals(Optional.of(5), TypeCoercer.coerce(5, ValueType.INT));
        }

        @Test
        @DisplayName("数字字符串可解析")
        void numericStringParses() {
            assertEquals(Optional.of(42), TypeCoercer.coerce("42", ValueType.INT));
        }

        @Test
        @DisplayName("带空白的数字字符串可解析")
        void numericStringWithWhitespace() {
            assertEquals(Optional.of(42), TypeCoercer.coerce("  42  ", ValueType.INT));
        }

        @Test
        @DisplayName("非数字字符串转换失败")
        void nonNumericStringFails() {
            assertEquals(Optional.empty(), TypeCoercer.coerce("abc", ValueType.INT));
        }

        @Test
        @DisplayName("超出 int 范围的 long 转换失败")
        void outOfRangeFails() {
            assertEquals(Optional.empty(), TypeCoercer.coerce(Long.MAX_VALUE, ValueType.INT));
        }

        @Test
        @DisplayName("带小数的 double 转 int 失败")
        void doubleWithFractionFails() {
            assertEquals(Optional.empty(), TypeCoercer.coerce(5.5, ValueType.INT));
        }

        @Test
        @DisplayName("整数值的 double 转 int 成功")
        void wholeDoubleToInt() {
            assertEquals(Optional.of(5), TypeCoercer.coerce(5.0, ValueType.INT));
        }
    }

    @Nested
    @DisplayName("BOOLEAN 转换")
    class BooleanCoercion {

        @Test
        @DisplayName("布尔值保持不变")
        void booleanStaysSame() {
            assertEquals(Optional.of(Boolean.TRUE), TypeCoercer.coerce(true, ValueType.BOOLEAN));
        }

        @Test
        @DisplayName("yes/on/1 转 true")
        void truthyStrings() {
            assertEquals(Optional.of(Boolean.TRUE), TypeCoercer.coerce("yes", ValueType.BOOLEAN));
            assertEquals(Optional.of(Boolean.TRUE), TypeCoercer.coerce("on", ValueType.BOOLEAN));
            assertEquals(Optional.of(Boolean.TRUE), TypeCoercer.coerce("1", ValueType.BOOLEAN));
            assertEquals(Optional.of(Boolean.TRUE), TypeCoercer.coerce("TRUE", ValueType.BOOLEAN));
        }

        @Test
        @DisplayName("no/off/0 转 false")
        void falsyStrings() {
            assertEquals(Optional.of(Boolean.FALSE), TypeCoercer.coerce("no", ValueType.BOOLEAN));
            assertEquals(Optional.of(Boolean.FALSE), TypeCoercer.coerce("off", ValueType.BOOLEAN));
            assertEquals(Optional.of(Boolean.FALSE), TypeCoercer.coerce("0", ValueType.BOOLEAN));
        }

        @Test
        @DisplayName("非法布尔字符串转换失败")
        void invalidBooleanFails() {
            assertEquals(Optional.empty(), TypeCoercer.coerce("maybe", ValueType.BOOLEAN));
        }
    }

    @Nested
    @DisplayName("STRING 转换")
    class StringCoercion {

        @Test
        @DisplayName("字符串保持不变")
        void stringStaysSame() {
            assertEquals(Optional.of("hello"), TypeCoercer.coerce("hello", ValueType.STRING));
        }

        @Test
        @DisplayName("数字不转为字符串（避免破坏用户原意）")
        void numberDoesNotBecomeString() {
            assertEquals(Optional.empty(), TypeCoercer.coerce(5, ValueType.STRING));
        }
    }

    @Nested
    @DisplayName("STRING_LIST 转换")
    class StringListCoercion {

        @Test
        @DisplayName("字符串列表保持不变")
        void stringListStaysSame() {
            List<String> input = List.of("a", "b", "c");
            assertEquals(Optional.of(input), TypeCoercer.coerce(input, ValueType.STRING_LIST));
        }

        @Test
        @DisplayName("混合类型列表转为字符串列表")
        void mixedListBecomesStringList() {
            Optional<Object> result = TypeCoercer.coerce(List.of(1, 2, 3), ValueType.STRING_LIST);
            assertEquals(Optional.of(List.of("1", "2", "3")), result);
        }

        @Test
        @DisplayName("单值不转为列表")
        void singleValueDoesNotBecomeList() {
            assertEquals(Optional.empty(), TypeCoercer.coerce("single", ValueType.STRING_LIST));
        }
    }

    @Nested
    @DisplayName("matches 类型检查")
    class Matches {

        @Test
        @DisplayName("Integer 匹配 INT 和 LONG 和 DOUBLE")
        void integerMatchesNumericTypes() {
            assertTrue(TypeCoercer.matches(5, ValueType.INT));
            assertTrue(TypeCoercer.matches(5, ValueType.LONG));
            assertTrue(TypeCoercer.matches(5, ValueType.DOUBLE));
        }

        @Test
        @DisplayName("Double 不匹配 INT")
        void doubleDoesNotMatchInt() {
            assertFalse(TypeCoercer.matches(5.5, ValueType.INT));
        }

        @Test
        @DisplayName("null 不匹配任何类型")
        void nullMatchesNothing() {
            assertFalse(TypeCoercer.matches(null, ValueType.STRING));
            assertFalse(TypeCoercer.matches(null, ValueType.INT));
        }
    }

    @Test
    @DisplayName("null 值转换返回 empty")
    void nullReturnsEmpty() {
        assertEquals(Optional.empty(), TypeCoercer.coerce(null, ValueType.STRING));
    }
}
