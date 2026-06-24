package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.CraneAttributeBridge;

public final class DefaultCraneAttributeBridge implements CraneAttributeBridge {

    private static final String PLUGIN_NAME = "CraneAttribute";
    private static final String API_CLASS_NAME = "cn.org.bukkit.craneattribute.api.AttributeAPI";

    private final JavaPlugin plugin;

    private boolean available;
    private Method getAttrDataMethod;
    private Method addAttributeSourceMethod;
    private Method addStaticAttributeSourceMethod;
    private Method removeAttributeSourceMethod;
    private Method updateAttributeMethod;
    private Class<?> attributeDataType;

    public DefaultCraneAttributeBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();

        Plugin craneAttribute = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (craneAttribute == null || !craneAttribute.isEnabled()) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS_NAME, true, craneAttribute.getClass().getClassLoader());
            getAttrDataMethod = findGetAttrDataMethod(apiClass);
            if (getAttrDataMethod == null) {
                plugin.getLogger().fine("[CraneAttribute] 未找到 getAttrData 方法");
                reset();
                return;
            }

            attributeDataType = getAttrDataMethod.getReturnType();
            addAttributeSourceMethod = findAddSource(apiClass, "addAttributeSource");
            addStaticAttributeSourceMethod = findAddSource(apiClass, "addStaticAttributeSource");
            removeAttributeSourceMethod = findRemoveSource(apiClass);
            updateAttributeMethod = findUpdateAttribute(apiClass);

            if ((addAttributeSourceMethod == null && addStaticAttributeSourceMethod == null) || removeAttributeSourceMethod == null || updateAttributeMethod == null) {
                plugin.getLogger().fine("[CraneAttribute] 部分关键方法未找到");
                reset();
                return;
            }

            available = true;
            plugin.getLogger().fine("[CraneAttribute] 桥接初始化成功");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[CraneAttribute] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    @Override
    public Object getAttrData(Player player) {
        if (!available || player == null) return null;
        try { return getAttrDataMethod.invoke(null, player); } catch (ReflectiveOperationException e) { return null; }
    }

    @Override
    public void addSource(Object attrData, String sourceName, List<String> lines) {
        if (!available || attrData == null || lines == null || lines.isEmpty()) return;
        try {
            if (addAttributeSourceMethod != null) {
                addAttributeSourceMethod.invoke(null, attrData, sourceName, List.copyOf(lines));
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[CraneAttribute] addSource 失败: " + e.getMessage());
        }
    }

    @Override
    public void addStaticSource(Object attrData, String sourceName, List<String> lines) {
        if (!available || attrData == null || lines == null || lines.isEmpty()) return;
        try {
            if (addStaticAttributeSourceMethod != null) {
                addStaticAttributeSourceMethod.invoke(null, attrData, sourceName, List.copyOf(lines));
            } else if (addAttributeSourceMethod != null) {
                addAttributeSourceMethod.invoke(null, attrData, sourceName, List.copyOf(lines));
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[CraneAttribute] addStaticSource 失败: " + e.getMessage());
        }
    }

    @Override public boolean supportsStaticSource() { return addStaticAttributeSourceMethod != null; }

    @Override
    public void removeSource(Object attrData, String sourceName) {
        if (!available || attrData == null || sourceName == null) return;
        try { removeAttributeSourceMethod.invoke(null, attrData, sourceName); } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[CraneAttribute] removeSource 失败: " + e.getMessage());
        }
    }

    @Override
    public void updateAttribute(Player player) {
        if (!available || player == null) return;
        try { updateAttributeMethod.invoke(null, player); } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[CraneAttribute] updateAttribute 失败: " + e.getMessage());
        }
    }

    private void reset() {
        available = false;
        getAttrDataMethod = null;
        addAttributeSourceMethod = null;
        addStaticAttributeSourceMethod = null;
        removeAttributeSourceMethod = null;
        updateAttributeMethod = null;
        attributeDataType = null;
    }

    private static Method findGetAttrDataMethod(Class<?> apiClass) {
        for (Method m : apiClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) continue;
            if (!"getAttrData".equals(m.getName()) && !"getAttributeData".equals(m.getName())) continue;
            Class<?> p = m.getParameterTypes()[0];
            if (p.isAssignableFrom(Player.class) || p.isAssignableFrom(LivingEntity.class) || p.isAssignableFrom(Entity.class)) return m;
        }
        return null;
    }

    private Method findAddSource(Class<?> apiClass, String methodName) {
        for (Method m : apiClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !methodName.equals(m.getName()) || m.getParameterCount() != 3) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p[0].isAssignableFrom(attributeDataType) && p[1] == String.class && (p[2].isAssignableFrom(List.class) || p[2] == Object.class)) return m;
        }
        return null;
    }

    private Method findRemoveSource(Class<?> apiClass) {
        for (Method m : apiClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !"removeAttributeSource".equals(m.getName()) || m.getParameterCount() != 2) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p[0].isAssignableFrom(attributeDataType) && p[1] == String.class) return m;
        }
        return null;
    }

    private static Method findUpdateAttribute(Class<?> apiClass) {
        for (Method m : apiClass.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !"updateAttribute".equals(m.getName()) || m.getParameterCount() != 1) continue;
            Class<?> p = m.getParameterTypes()[0];
            if (p.isAssignableFrom(Player.class) || p.isAssignableFrom(LivingEntity.class) || p.isAssignableFrom(Entity.class)) return m;
        }
        return null;
    }
}
