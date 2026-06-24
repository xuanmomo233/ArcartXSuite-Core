package xuanmo.arcartxsuite.module;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 每个模块 Jar 独立使用的 ClassLoader。
 * <p>
 * parent 设置为宿主插件的 ClassLoader，因此模块可以访问：
 * <ul>
 *   <li>axs-api 中的接口（AXSModule、ModuleContext 等）</li>
 *   <li>Spigot API</li>
 *   <li>宿主插件中的 bridge 类</li>
 * </ul>
 * 模块之间默认隔离，通过 ModuleContext.getModule() 进行安全的跨模块通信。
 */
public final class ModuleClassLoader extends URLClassLoader {

    private final String moduleId;

    public ModuleClassLoader(String moduleId, URL jarUrl, ClassLoader parent) {
        super(new URL[]{jarUrl}, parent);
        this.moduleId = moduleId;
    }

    public String moduleId() {
        return moduleId;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
