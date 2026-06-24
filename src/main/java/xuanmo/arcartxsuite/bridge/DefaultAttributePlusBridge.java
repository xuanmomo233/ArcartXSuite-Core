package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;

public final class DefaultAttributePlusBridge implements AttributePlusBridge {

    private static final String PLUGIN_NAME = "AttributePlus";
    private static final String API_CLASS_NAME = "org.serverct.ersha.api.AttributeAPI";

    private final JavaPlugin plugin;

    private boolean available;
    private Method getAttrDataMethod;
    private Method getAttributeSourceMethod;
    private Method addSourceAttributeMethod;
    private Method takeSourceAttributeMethod;
    private Method addSourceLines4Method;
    private Method addStaticSource3Method;
    private Method updateAttributeMethod;
    private Class<?> attributeDataType;

    public DefaultAttributePlusBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();

        Plugin attributePlus = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (attributePlus == null || !attributePlus.isEnabled()) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName(API_CLASS_NAME, true, attributePlus.getClass().getClassLoader());
            getAttrDataMethod = findMethod(apiClass, "getAttrData", 1, cls -> Player.class.isAssignableFrom(cls[0]));
            if (getAttrDataMethod == null) {
                plugin.getLogger().fine("[AttributePlus] 未找到 getAttrData 方法");
                reset();
                return;
            }
            attributeDataType = getAttrDataMethod.getReturnType();
            getAttributeSourceMethod = findMethod(apiClass, "getAttributeSource", 1, cls -> List.class.isAssignableFrom(cls[0]));
            addSourceAttributeMethod = findMethod(apiClass, "addSourceAttribute", 3, cls -> cls[0].isAssignableFrom(attributeDataType) && cls[1] == String.class);
            takeSourceAttributeMethod = findMethod(apiClass, "takeSourceAttribute", 2, cls -> cls[0].isAssignableFrom(attributeDataType) && cls[1] == String.class);
            addSourceLines4Method = findMethod(apiClass, "addSourceAttribute", 4, cls -> cls[0].isAssignableFrom(attributeDataType) && cls[1] == String.class && (cls[3] == boolean.class || cls[3] == Boolean.class));
            addStaticSource3Method = findMethod(apiClass, "addStaticAttributeSource", 3, cls -> cls[0].isAssignableFrom(attributeDataType) && cls[1] == String.class);
            updateAttributeMethod = findUpdateAttributeMethod(apiClass, attributeDataType);

            boolean hasBasicApi = getAttributeSourceMethod != null && addSourceAttributeMethod != null && takeSourceAttributeMethod != null;
            boolean hasLinesApi = (addSourceLines4Method != null || addStaticSource3Method != null) && takeSourceAttributeMethod != null;
            available = hasBasicApi || hasLinesApi;
            if (available) {
                plugin.getLogger().fine("[AttributePlus] 桥接初始化成功");
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[AttributePlus] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public Object getAttrData(Player player) {
        if (!available || player == null) {
            return null;
        }
        try {
            return getAttrDataMethod.invoke(null, player);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    @Override
    public Object getAttributeSource(List<String> lines) {
        if (!available || lines == null || lines.isEmpty()) {
            return null;
        }
        try {
            return getAttributeSourceMethod.invoke(null, List.copyOf(lines));
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    @Override
    public void addSource(Object attrData, String sourceId, Object attributeSource) {
        if (!available || attrData == null || sourceId == null || attributeSource == null) {
            return;
        }
        try {
            addSourceAttributeMethod.invoke(null, attrData, sourceId, attributeSource);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[AttributePlus] addSource 失败: " + exception.getMessage());
        }
    }

    @Override
    public void addSourceLines(Object attrData, String sourceName, List<String> lines) {
        if (!available || attrData == null || lines == null || lines.isEmpty()) {
            return;
        }
        try {
            List<String> copy = List.copyOf(lines);
            if (addSourceLines4Method != null) {
                addSourceLines4Method.invoke(null, attrData, sourceName, copy, false);
            } else if (addStaticSource3Method != null) {
                addStaticSource3Method.invoke(null, attrData, sourceName, copy);
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[AttributePlus] addSourceLines 失败: " + exception.getMessage());
        }
    }

    @Override
    public void removeSource(Object attrData, String sourceId) {
        if (!available || attrData == null || sourceId == null) {
            return;
        }
        try {
            takeSourceAttributeMethod.invoke(null, attrData, sourceId);
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[AttributePlus] removeSource 失败: " + exception.getMessage());
        }
    }

    @Override
    public void updateAttribute(Player player, Object attrData) {
        if (!available || updateAttributeMethod == null || player == null) {
            return;
        }
        try {
            Object[] args = buildUpdateArgs(updateAttributeMethod, player, attrData);
            if (args != null) {
                updateAttributeMethod.invoke(null, args);
            }
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[AttributePlus] updateAttribute 失败: " + exception.getMessage());
        }
    }

    private void reset() {
        available = false;
        getAttrDataMethod = null;
        getAttributeSourceMethod = null;
        addSourceAttributeMethod = null;
        takeSourceAttributeMethod = null;
        addSourceLines4Method = null;
        addStaticSource3Method = null;
        updateAttributeMethod = null;
        attributeDataType = null;
    }

    @FunctionalInterface
    private interface ParamMatcher {
        boolean matches(Class<?>[] paramTypes);
    }

    private static Method findMethod(Class<?> apiClass, String name, int paramCount, ParamMatcher matcher) {
        for (Method method : apiClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !name.equals(method.getName()) || method.getParameterCount() != paramCount) {
                continue;
            }
            if (matcher.matches(method.getParameterTypes())) {
                return method;
            }
        }
        return null;
    }

    private static Method findUpdateAttributeMethod(Class<?> apiClass, Class<?> attributeDataType) {
        for (Method method : apiClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !"updateAttribute".equals(method.getName())) {
                continue;
            }
            boolean allSupported = true;
            for (Class<?> p : method.getParameterTypes()) {
                if (p.isPrimitive()) { if (p != boolean.class) { allSupported = false; break; } continue; }
                if (p.isAssignableFrom(Player.class) || p.isAssignableFrom(LivingEntity.class) || p.isAssignableFrom(Entity.class)) continue;
                if (p.isAssignableFrom(attributeDataType)) continue;
                if (p == UUID.class || p == String.class) continue;
                allSupported = false;
                break;
            }
            if (allSupported) return method;
        }
        return null;
    }

    private static Object[] buildUpdateArgs(Method method, Player player, Object attrData) {
        Class<?>[] params = method.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            if (p.isPrimitive()) { if (p == boolean.class) { args[i] = false; continue; } return null; }
            if (p.isAssignableFrom(Player.class) || p.isAssignableFrom(LivingEntity.class) || p.isAssignableFrom(Entity.class)) { args[i] = player; continue; }
            if (attrData != null && p.isInstance(attrData)) { args[i] = attrData; continue; }
            if (p == UUID.class) { args[i] = player.getUniqueId(); continue; }
            if (p == String.class) { args[i] = player.getName(); continue; }
            return null;
        }
        return args;
    }
}
