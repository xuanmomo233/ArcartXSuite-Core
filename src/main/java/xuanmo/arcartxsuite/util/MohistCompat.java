package xuanmo.arcartxsuite.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mohist 混合端兼容层。
 */
public final class MohistCompat {

    private static final Logger LOGGER = Logger.getLogger("AXS-MohistCompat");

    private static volatile Boolean mohistDetected;

    private MohistCompat() {}

    public static boolean isMohist() {
        if (mohistDetected == null) {
            synchronized (MohistCompat.class) {
                if (mohistDetected == null) {
                    mohistDetected = detectMohist();
                }
            }
        }
        return mohistDetected;
    }

    private static boolean detectMohist() {
        try {
            Class.forName("com.mohistmc.MohistMC");
            return true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("com.mohistmc.api.ServerAPI");
            return true;
        } catch (ClassNotFoundException ignored) {}
        String version = org.bukkit.Bukkit.getVersion();
        return version != null && version.toLowerCase().contains("mohist");
    }

    public static InputStream getResourceSafe(String path, ClassLoader classLoader) {
        if (classLoader != null) {
            InputStream input = classLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null && contextLoader != classLoader) {
            InputStream input = contextLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        ClassLoader selfLoader = MohistCompat.class.getClassLoader();
        if (selfLoader != classLoader && selfLoader != contextLoader) {
            InputStream input = selfLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        if (systemLoader != selfLoader && systemLoader != classLoader) {
            InputStream input = systemLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        return null;
    }

    public static boolean saveResourceSafe(JavaPlugin plugin, String resourcePath, File target) {
        try {
            if (!target.exists()) {
                plugin.saveResource(resourcePath, false);
            }
            if (target.exists()) return true;
        } catch (Exception exception) {
            if (isMohist()) {
                LOGGER.fine("Mohist 环境下 saveResource(\"" + resourcePath + "\") 失败: " + exception.getMessage());
            } else {
                LOGGER.log(Level.WARNING, "saveResource(\"" + resourcePath + "\") 失败", exception);
            }
        }

        ClassLoader pluginLoader = plugin.getClass().getClassLoader();
        InputStream input = getResourceSafe(resourcePath, pluginLoader);
        if (input != null) {
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException ioException) {
                LOGGER.warning("手动写出资源 \"" + resourcePath + "\" 失败: " + ioException.getMessage());
            } finally {
                try { input.close(); } catch (IOException ignored) {}
            }
        }

        try {
            URL jarUrl = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (jarUrl != null) {
                String jarPath = jarUrl.toExternalForm();
                if (jarPath.endsWith(".jar") || jarPath.endsWith(".jar!/")) {
                    String entryUrl = "jar:" + jarPath + "!/" + resourcePath;
                    URL resourceUrl = new URL(entryUrl);
                    URLConnection connection = resourceUrl.openConnection();
                    connection.setUseCaches(false);
                    try (InputStream jarInput = connection.getInputStream()) {
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        Files.copy(jarInput, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        return true;
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.fine("通过 JAR URL 直接读取 \"" + resourcePath + "\" 失败: " + exception.getMessage());
        }

        LOGGER.severe("无法加载资源 \"" + resourcePath + "\"，所有策略均已失败" +
            (isMohist() ? "（Mohist 混合端环境）" : ""));
        return false;
    }

    public static ClassLoader effectiveClassLoader(JavaPlugin plugin) {
        if (plugin != null) {
            return plugin.getClass().getClassLoader();
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return contextLoader;
        }
        return MohistCompat.class.getClassLoader();
    }
}
