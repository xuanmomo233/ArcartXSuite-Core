package xuanmo.arcartxsuite.api.combat;

import java.util.Locale;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class EntityCombatMetadata {

    private EntityCombatMetadata() {}

    // ─── Display name ────────────────────────────────────────

    public static String resolveDisplayName(LivingEntity entity, String mythicMobId) {
        if (entity == null) {
            return "Unknown";
        }
        if (entity instanceof Player player) {
            return player.getName();
        }
        if (mythicMobId != null && !mythicMobId.isBlank()) {
            return mythicMobId;
        }
        String customName = entity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            return ChatColor.stripColor(customName);
        }
        return entity.getType().name();
    }

    // ─── Entity type helpers ─────────────────────────────────

    public static String resolveEntityType(LivingEntity entity) {
        return entity == null ? "" : entity.getType().name();
    }

    public static String normalizeEntityType(String entityType) {
        return entityType == null ? "" : entityType.trim().toUpperCase(Locale.ROOT);
    }

    public static String formatEntityTypeName(EntityType type) {
        if (type == null) return "";
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    // ─── MythicMobs integration (reflection-based) ───────────

    public static String normalizeMythicMobId(String mythicMobId) {
        return mythicMobId == null ? "" : mythicMobId.trim();
    }

    public static String resolveMythicMobId(LivingEntity entity) {
        Object activeMob = resolveActiveMob(entity);
        return nullToEmpty(resolveMythicMobIdFrom(activeMob));
    }

    @SuppressWarnings("unchecked")
    public static <T> T resolveActiveMob(LivingEntity entity) {
        if (entity == null) return null;
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            Object mobManager = mythicBukkitClass.getMethod("getMobManager").invoke(mythicBukkit);
            Object result = mobManager.getClass()
                .getMethod("getActiveMob", java.util.UUID.class)
                .invoke(mobManager, entity.getUniqueId());
            if (result instanceof Optional<?> opt) {
                return opt.isPresent() ? (T) opt.get() : null;
            }
            return (T) result;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String resolveMythicMobIdFrom(Object activeMob) {
        if (activeMob == null) return "";
        try {
            // 优先 getType().getInternalName()（返回 MythicMob 对象）
            Object mythicMobType = activeMob.getClass().getMethod("getType").invoke(activeMob);
            if (mythicMobType != null) {
                Object internalName = mythicMobType.getClass().getMethod("getInternalName").invoke(mythicMobType);
                if (internalName instanceof String s && !s.isBlank()) return s;
            }
        } catch (Exception ignored) {}
        try {
            // 退化：getMobType() 直接返回 String 形式的 ID
            Object fallback = activeMob.getClass().getMethod("getMobType").invoke(activeMob);
            return fallback instanceof String s ? s : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    // ─── Health resolution ───────────────────────────────────

    public static double resolveMaxHealth(LivingEntity entity, Object activeMob) {
        if (activeMob != null) {
            try {
                Object stat = activeMob.getClass().getMethod("getMaxHealth").invoke(activeMob);
                if (stat instanceof Number n) return n.doubleValue();
            } catch (Exception ignored) {}
        }
        if (entity != null) {
            AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) return attr.getValue();
        }
        return 20.0;
    }

    public static double resolveCurrentHealth(LivingEntity entity, double maxHealth, Object activeMob) {
        if (activeMob != null) {
            try {
                Object stat = activeMob.getClass().getMethod("getHealth").invoke(activeMob);
                if (stat instanceof Number n) return Math.min(n.doubleValue(), maxHealth);
            } catch (Exception ignored) {}
        }
        if (entity != null) {
            return Math.min(entity.getHealth(), maxHealth);
        }
        return 0.0;
    }

    // ─── Util ────────────────────────────────────────────────

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
