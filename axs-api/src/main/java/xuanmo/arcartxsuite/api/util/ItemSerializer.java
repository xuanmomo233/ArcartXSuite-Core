package xuanmo.arcartxsuite.api.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemSerializer {

    private static final String WRAPPER_CLASS_NAME = "org.bukkit.util.io.Wrapper";

    private ItemSerializer() {
    }

    public static byte[] serialize(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new byte[0];
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataStream = new BukkitObjectOutputStream(outputStream)) {
            dataStream.writeObject(itemStack);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize item stack.", exception);
        }
    }

    public static ItemStack deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataStream = new BukkitObjectInputStream(inputStream)) {
            Object object = dataStream.readObject();
            return object instanceof ItemStack itemStack ? itemStack : null;
        } catch (IOException | ClassNotFoundException | RuntimeException primaryException) {
            try {
                return deserializeCompat(bytes);
            } catch (IOException | ReflectiveOperationException compatException) {
                compatException.addSuppressed(primaryException);
                throw new IllegalStateException("Unable to deserialize item stack.", compatException);
            }
        }
    }

    private static ItemStack deserializeCompat(byte[] bytes) throws IOException, ClassNotFoundException, ReflectiveOperationException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             ObjectInputStream dataStream = new ObjectInputStream(inputStream)) {
            Object object = unwrapLegacyObject(dataStream.readObject());
            if (object instanceof ItemStack itemStack) {
                return itemStack;
            }
            if (object instanceof Map<?, ?> map) {
                return deserializeItemStackMap(asStringObjectMap(map));
            }
            return null;
        }
    }

    private static ItemStack deserializeItemStackMap(Map<String, Object> map) {
        Map<String, Object> copy = new LinkedHashMap<>(map);
        Object rawMeta = copy.get("meta");
        if (rawMeta instanceof Map<?, ?> metaMap) {
            copy.put("meta", deserializeItemMetaMap(asStringObjectMap(metaMap)));
        }
        return ItemStack.deserialize(copy);
    }

    private static ItemMeta deserializeItemMetaMap(Map<String, Object> map) {
        try {
            Object direct = ConfigurationSerialization.deserializeObject(map);
            if (direct instanceof ItemMeta itemMeta) {
                return itemMeta;
            }
        } catch (RuntimeException ignored) {
            // Mohist may reject nested NBT-shaped values that Spigot accepts.
        }

        for (Map<String, Object> candidate : List.of(sanitizeMetaMap(map, false), sanitizeMetaMap(map, true))) {
            try {
                Object fallback = ConfigurationSerialization.deserializeObject(candidate);
                if (fallback instanceof ItemMeta itemMeta) {
                    return itemMeta;
                }
            } catch (RuntimeException ignored) {
                // Try the next, more aggressive candidate.
            }
        }
        throw new IllegalStateException("Unable to deserialize item meta.");
    }

    private static Map<String, Object> sanitizeMetaMap(Map<String, Object> map, boolean aggressive) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sanitized.put(entry.getKey(), sanitizeMetaValue(entry.getKey(), entry.getValue(), aggressive));
        }
        return sanitized;
    }

    private static Object sanitizeMetaValue(String key, Object value, boolean aggressive) {
        if (shouldConvertToSnbt(key, value, aggressive)) {
            return toSnbt(value);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String nestedKey = String.valueOf(entry.getKey());
                copy.put(nestedKey, sanitizeMetaValue(nestedKey, entry.getValue(), aggressive));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(sanitizeMetaValue(key, element, aggressive));
            }
            return copy;
        }
        return value;
    }

    private static boolean shouldConvertToSnbt(String key, Object value, boolean aggressive) {
        if (!isComplexNbtCandidate(value)) {
            return false;
        }
        if (aggressive) {
            return true;
        }
        String normalizedKey = key == null ? "" : key.toLowerCase();
        return normalizedKey.contains("tag")
            || normalizedKey.contains("nbt")
            || normalizedKey.contains("internal")
            || normalizedKey.contains("unhandled")
            || normalizedKey.contains("component")
            || normalizedKey.contains("custom")
            || normalizedKey.contains("publicbukkitvalues")
            || normalizedKey.contains("blockentity")
            || normalizedKey.contains("entitytag")
            || normalizedKey.contains("bucketentity");
    }

    private static boolean isComplexNbtCandidate(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    return true;
                }
                Object nested = entry.getValue();
                if (nested instanceof Map<?, ?> || nested instanceof List<?>) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> || element instanceof List<?>) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object unwrapLegacyObject(Object value) throws ReflectiveOperationException {
        if (value == null) {
            return null;
        }
        if (WRAPPER_CLASS_NAME.equals(value.getClass().getName())) {
            Field mapField = value.getClass().getDeclaredField("map");
            mapField.setAccessible(true);
            return unwrapLegacyObject(mapField.get(value));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), unwrapLegacyObject(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(unwrapLegacyObject(element));
            }
            return copy;
        }
        return value;
    }

    private static Map<String, Object> asStringObjectMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static String toSnbt(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(escapeKey(String.valueOf(entry.getKey())))
                    .append(':')
                    .append(toSnbt(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(toSnbt(list.get(index)));
            }
            return builder.append(']').toString();
        }
        if (value instanceof String string) {
            return '"' + escapeString(string) + '"';
        }
        if (value instanceof Byte byteValue) {
            return byteValue + "b";
        }
        if (value instanceof Short shortValue) {
            return shortValue + "s";
        }
        if (value instanceof Long longValue) {
            return longValue + "L";
        }
        if (value instanceof Float floatValue) {
            return stripTrailingZero(floatValue) + "f";
        }
        if (value instanceof Double doubleValue) {
            return stripTrailingZero(doubleValue) + "d";
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? "1b" : "0b";
        }
        return String.valueOf(value);
    }

    private static String escapeKey(String value) {
        return value.matches("[A-Za-z0-9._+-]+") ? value : '"' + escapeString(value) + '"';
    }

    private static String escapeString(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static String stripTrailingZero(Number value) {
        String text = String.valueOf(value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
