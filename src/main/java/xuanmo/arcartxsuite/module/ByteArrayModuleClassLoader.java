package xuanmo.arcartxsuite.module;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * 从内存中的 Jar 字节数组加载模块的 ClassLoader。
 * <p>
 * 用于云端模块：下载加密 .axb → 解密为 jar bytes → 直接内存加载，不落盘。
 */
public final class ByteArrayModuleClassLoader extends ClassLoader {

    private final String moduleId;
    private final byte[] jarBytes;
    private final Map<String, byte[]> entries = new HashMap<>();

    public ByteArrayModuleClassLoader(String moduleId, byte[] jarBytes, ClassLoader parent) {
        super(parent);
        this.moduleId = moduleId;
        this.jarBytes = jarBytes;
        indexEntries();
    }

    private void indexEntries() {
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] data = jis.readAllBytes();
                    entries.put(entry.getName(), data);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("无法索引 Jar 条目: " + moduleId, e);
        }
    }

    public String moduleId() {
        return moduleId;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        byte[] classBytes = entries.get(path);
        if (classBytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, classBytes, 0, classBytes.length);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] data = entries.get(name);
        if (data != null) {
            return new ByteArrayInputStream(data);
        }
        return super.getResourceAsStream(name);
    }

    @Override
    protected URL findResource(String name) {
        byte[] data = entries.get(name);
        if (data == null) {
            return null;
        }
        try {
            return new URL("bytes", "", -1, "/" + name, new ByteArrayURLStreamHandler(data));
        } catch (IOException e) {
            return null;
        }
    }

    private static class ByteArrayURLStreamHandler extends URLStreamHandler {
        private final byte[] data;

        ByteArrayURLStreamHandler(byte[] data) {
            this.data = data;
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new URLConnection(u) {
                @Override
                public void connect() {}

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(data);
                }

                @Override
                public long getContentLengthLong() {
                    return data.length;
                }
            };
        }
    }

    public void close() throws IOException {
        entries.clear();
    }
}
