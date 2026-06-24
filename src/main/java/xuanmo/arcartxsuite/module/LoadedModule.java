package xuanmo.arcartxsuite.module;

import java.io.File;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

/**
 * 已加载模块的运行时状态。
 */
final class LoadedModule {

    private final ModuleDescriptor descriptor;
    private final AXSModule instance;
    private final ClassLoader classLoader;
    private final File jarFile;
    private final byte[] jarBytes;
    private DefaultModuleContext context;
    private boolean enabled;

    LoadedModule(ModuleDescriptor descriptor, AXSModule instance, ClassLoader classLoader, File jarFile) {
        this.descriptor = descriptor;
        this.instance = instance;
        this.classLoader = classLoader;
        this.jarFile = jarFile;
        this.jarBytes = null;
        this.enabled = false;
    }

    LoadedModule(ModuleDescriptor descriptor, AXSModule instance, ClassLoader classLoader, byte[] jarBytes) {
        this.descriptor = descriptor;
        this.instance = instance;
        this.classLoader = classLoader;
        this.jarFile = null;
        this.jarBytes = jarBytes;
        this.enabled = false;
    }

    void setContext(DefaultModuleContext context) {
        this.context = context;
    }

    DefaultModuleContext context() {
        return context;
    }

    ModuleDescriptor descriptor() {
        return descriptor;
    }

    AXSModule instance() {
        return instance;
    }

    ClassLoader classLoader() {
        return classLoader;
    }

    File jarFile() {
        return jarFile;
    }

    byte[] jarBytes() {
        return jarBytes;
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
