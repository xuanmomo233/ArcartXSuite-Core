package xuanmo.arcartxsuite.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 反射缓存工具，减少桥接类中重复查找 Class/Method/Field 的样板代码。
 * <p>
 * 仅用于核心内部（bridge 包等），不暴露给模块。
 */
public final class ReflectionCache {

    private final ClassLoader primaryLoader;
    private final ConcurrentMap<String, Class<?>> classCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<MethodKey, Method> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<MethodKey, Constructor<?>> constructorCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<FieldKey, Field> fieldCache = new ConcurrentHashMap<>();

    public ReflectionCache(ClassLoader primaryLoader) {
        this.primaryLoader = primaryLoader;
    }

    /**
     * 按全限定名加载类，优先使用指定 classLoader，回退当前线程 classLoader。
     */
    public Class<?> forName(String className) throws ClassNotFoundException {
        Class<?> cached = classCache.get(className);
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> clazz = Class.forName(className, true, primaryLoader);
            classCache.put(className, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            ClassLoader fallback = Thread.currentThread().getContextClassLoader();
            if (fallback != null && fallback != primaryLoader) {
                Class<?> clazz = Class.forName(className, true, fallback);
                classCache.put(className, clazz);
                return clazz;
            }
            throw e;
        }
    }

    /** 获取公开方法。 */
    public Method method(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        MethodKey key = new MethodKey(clazz, name, parameterTypes);
        Method cached = methodCache.get(key);
        if (cached != null) {
            return cached;
        }
        Method method = clazz.getMethod(name, parameterTypes);
        methodCache.put(key, method);
        return method;
    }

    /** 获取类自身声明的方法（含私有）。 */
    public Method declaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        MethodKey key = new MethodKey(clazz, name, parameterTypes);
        Method cached = methodCache.get(key);
        if (cached != null) {
            return cached;
        }
        Method method = clazz.getDeclaredMethod(name, parameterTypes);
        methodCache.put(key, method);
        return method;
    }

    /** 获取公开字段。 */
    public Field field(Class<?> clazz, String name) throws NoSuchFieldException {
        FieldKey key = new FieldKey(clazz, name);
        Field cached = fieldCache.get(key);
        if (cached != null) {
            return cached;
        }
        Field field = clazz.getField(name);
        fieldCache.put(key, field);
        return field;
    }

    /** 获取类自身声明的字段（含私有）。 */
    public Field declaredField(Class<?> clazz, String name) throws NoSuchFieldException {
        FieldKey key = new FieldKey(clazz, name);
        Field cached = fieldCache.get(key);
        if (cached != null) {
            return cached;
        }
        Field field = clazz.getDeclaredField(name);
        fieldCache.put(key, field);
        return field;
    }

    /**
     * 在类继承链中查找字段（从自身到 Object.class）。
     * 返回 null 表示未找到，不抛异常。
     */
    public Field findFieldInHierarchy(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return declaredField(current, name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** 按签名查找构造函数。 */
    public Constructor<?> constructor(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodException {
        MethodKey key = new MethodKey(clazz, "<init>", parameterTypes);
        Constructor<?> cached = constructorCache.get(key);
        if (cached != null) {
            return cached;
        }
        Constructor<?> constructor = clazz.getConstructor(parameterTypes);
        constructorCache.put(key, constructor);
        return constructor;
    }

    /** 查找声明的构造函数。 */
    public Constructor<?> declaredConstructor(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodException {
        MethodKey key = new MethodKey(clazz, "<init>", parameterTypes);
        Constructor<?> cached = constructorCache.get(key);
        if (cached != null) {
            return cached;
        }
        Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
        constructorCache.put(key, constructor);
        return constructor;
    }

    /**
     * 扫描类所有方法，返回指定名称且参数数量匹配的方法列表。
     * 结果不缓存，用于需要根据运行时类型做重载选择的场景。
     */
    public List<Method> methodsByName(Class<?> clazz, String name, int parameterCount) {
        return Arrays.stream(clazz.getMethods())
            .filter(m -> m.getName().equals(name) && m.getParameterCount() == parameterCount)
            .toList();
    }

    public void clear() {
        classCache.clear();
        methodCache.clear();
        constructorCache.clear();
        fieldCache.clear();
    }

    private record MethodKey(Class<?> clazz, String name, Class<?>[] parameterTypes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodKey other)) return false;
            return clazz.equals(other.clazz)
                && name.equals(other.name)
                && Arrays.equals(parameterTypes, other.parameterTypes);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * clazz.hashCode() + name.hashCode()) + Arrays.hashCode(parameterTypes);
        }
    }

    private record FieldKey(Class<?> clazz, String name) {
        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof FieldKey other
                && clazz.equals(other.clazz) && name.equals(other.name));
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, name);
        }
    }
}
