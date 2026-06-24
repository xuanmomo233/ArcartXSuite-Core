package xuanmo.arcartxsuite.module;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleLoadException;

/**
 * 从模块 Jar 中的 module.yml 解析 {@link ModuleDescriptor}。
 */
final class ModuleDescriptorParser {

    private static final String MODULE_YML = "module.yml";

    private ModuleDescriptorParser() {
    }

    static ModuleDescriptor parse(JarFile jarFile) throws ModuleLoadException {
        JarEntry entry = jarFile.getJarEntry(MODULE_YML);
        if (entry == null) {
            throw new ModuleLoadException("模块 Jar 缺少 " + MODULE_YML + ": " + jarFile.getName());
        }

        YamlConfiguration yaml;
        try (InputStream input = jarFile.getInputStream(entry)) {
            yaml = loadYaml(input);
        } catch (IOException exception) {
            throw new ModuleLoadException("读取 " + MODULE_YML + " 失败: " + jarFile.getName(), exception);
        }

        return buildDescriptor(yaml, jarFile.getName());
    }

    /**
     * 从内存中的 Jar 字节数组解析模块描述符（用于云端模块）。
     */
    static ModuleDescriptor parse(byte[] jarBytes) throws ModuleLoadException {
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (MODULE_YML.equals(entry.getName())) {
                    YamlConfiguration yaml = loadYaml(jis);
                    return buildDescriptor(yaml, "<memory>");
                }
            }
        } catch (IOException exception) {
            throw new ModuleLoadException("解析内存 Jar 失败", exception);
        }
        throw new ModuleLoadException("模块 Jar 缺少 " + MODULE_YML);
    }

    private static YamlConfiguration loadYaml(InputStream input) throws ModuleLoadException {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return yaml;
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new ModuleLoadException("解析 " + MODULE_YML + " 失败", exception);
        } catch (IOException exception) {
            throw new ModuleLoadException("读取 " + MODULE_YML + " 失败", exception);
        }
    }

    private static ModuleDescriptor buildDescriptor(YamlConfiguration yaml, String sourceName) throws ModuleLoadException {
        String id = yaml.getString("id");
        if (id == null || id.isBlank()) {
            throw new ModuleLoadException(MODULE_YML + " 缺少 id 字段: " + sourceName);
        }

        String mainClass = yaml.getString("main");
        if (mainClass == null || mainClass.isBlank()) {
            throw new ModuleLoadException(MODULE_YML + " 缺少 main 字段: " + sourceName);
        }

        return ModuleDescriptor.builder(id.trim().toLowerCase(java.util.Locale.ROOT))
            .name(yaml.getString("name", id))
            .version(yaml.getString("version", "1.0.0"))
            .mainClass(mainClass.trim())
            .depends(toStringList(yaml, "depends"))
            .softDepends(toStringList(yaml, "softdepends"))
            .externalDepends(toStringList(yaml, "external-depends"))
            .externalSoftDepends(toStringList(yaml, "external-softdepends"))
            .signature(yaml.getString("signature"))
            .build();
    }

    private static List<String> toStringList(YamlConfiguration yaml, String key) {
        List<String> list = yaml.getStringList(key);
        return list == null ? List.of() : list;
    }
}
