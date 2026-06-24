package xuanmo.arcartxsuite.api.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MessageProvider 消息提供者")
class MessageProviderTest {

    @TempDir
    File tempDir;

    private static final Logger LOGGER = Logger.getLogger("MessageProviderTest");

    private MessageProvider newProviderWithContent(String yamlContent) throws IOException {
        File messagesFile = new File(tempDir, "messages.yml");
        Files.writeString(messagesFile.toPath(), yamlContent);
        MessageProvider provider = new MessageProvider(
            tempDir, "messages.yml", getClass().getClassLoader(), LOGGER);
        provider.load();
        return provider;
    }

    @Test
    @DisplayName("加载简单消息")
    void loadSimpleMessage() throws IOException {
        MessageProvider provider = newProviderWithContent("greeting: Hello World");
        assertEquals("Hello World", provider.get("greeting"));
    }

    @Test
    @DisplayName("嵌套键使用点号路径")
    void nestedKeys() throws IOException {
        MessageProvider provider = newProviderWithContent(
            "purge:\n  confirm: Confirm within seconds\n  done: Purge complete");
        assertEquals("Confirm within seconds", provider.get("purge.confirm"));
        assertEquals("Purge complete", provider.get("purge.done"));
    }

    @Test
    @DisplayName("占位符 {0} 被替换")
    void singlePlaceholder() throws IOException {
        MessageProvider provider = newProviderWithContent("confirm: Confirm within {0} seconds");
        assertEquals("Confirm within 10 seconds", provider.get("confirm", 10));
    }

    @Test
    @DisplayName("多占位符按顺序替换")
    void multiplePlaceholders() throws IOException {
        MessageProvider provider = newProviderWithContent("purge: Purged {0} rows in {1}");
        assertEquals("Purged 42 rows in chat", provider.get("purge", 42, "chat"));
    }

    @Test
    @DisplayName("& 颜色码被翻译")
    void colorCodesTranslated() throws IOException {
        MessageProvider provider = newProviderWithContent("warning: \"&cDanger\"");
        // ChatColor.translateAlternateColorCodes 把 &c 转成 §c
        assertEquals("\u00a7cDanger", provider.get("warning"));
    }

    @Test
    @DisplayName("缺失键返回键名本身")
    void missingKeyReturnsKey() throws IOException {
        MessageProvider provider = newProviderWithContent("greeting: Hello");
        assertEquals("nonexistent.key", provider.get("nonexistent.key"));
    }

    @Test
    @DisplayName("has 正确判断键是否存在")
    void hasChecksExistence() throws IOException {
        MessageProvider provider = newProviderWithContent("greeting: Hello");
        assertTrue(provider.has("greeting"));
        assertFalse(provider.has("missing"));
    }

    @Test
    @DisplayName("null 参数不抛异常")
    void nullArgsSafe() throws IOException {
        MessageProvider provider = newProviderWithContent("greeting: Hello {0}");
        // 传入 null 占位符应被替换为空字符串
        assertEquals("Hello ", provider.get("greeting", (Object) null));
    }

    @Test
    @DisplayName("文件不存在时 size 为 0")
    void noFileEmptySize() {
        MessageProvider provider = new MessageProvider(
            new File(tempDir, "nonexistent-subdir"), "missing.yml", getClass().getClassLoader(), LOGGER);
        provider.load();
        assertEquals(0, provider.size());
    }

    @Test
    @DisplayName("reload 后更新内容")
    void reloadUpdatesContent() throws IOException {
        File messagesFile = new File(tempDir, "messages.yml");
        Files.writeString(messagesFile.toPath(), "greeting: First");
        MessageProvider provider = new MessageProvider(
            tempDir, "messages.yml", getClass().getClassLoader(), LOGGER);
        provider.load();
        assertEquals("First", provider.get("greeting"));

        Files.writeString(messagesFile.toPath(), "greeting: Second");
        provider.load();
        assertEquals("Second", provider.get("greeting"));
    }
}
