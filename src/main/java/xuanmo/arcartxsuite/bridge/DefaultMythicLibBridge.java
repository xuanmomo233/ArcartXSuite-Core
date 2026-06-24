package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.MythicLibBridge;

public final class DefaultMythicLibBridge implements MythicLibBridge {

    private static final String PLUGIN_NAME = "MythicLib";
    private static final String MYTHIC_LIB_CLASS = "io.lumine.mythic.lib.MythicLib";
    private static final String PLAYER_DATA_CLASS = "io.lumine.mythic.lib.api.player.MMOPlayerData";
    private static final String STAT_MAP_CLASS = "io.lumine.mythic.lib.api.stat.StatMap";
    private static final String STAT_INSTANCE_CLASS = "io.lumine.mythic.lib.api.stat.StatInstance";
    private static final String STAT_MODIFIER_CLASS = "io.lumine.mythic.lib.api.stat.modifier.StatModifier";
    private static final String TEMP_MODIFIER_CLASS = "io.lumine.mythic.lib.api.stat.modifier.TemporaryStatModifier";
    private static final String MODIFIER_TYPE_CLASS = "io.lumine.mythic.lib.player.modifier.ModifierType";
    private static final String EQUIPMENT_SLOT_CLASS = "io.lumine.mythic.lib.api.player.EquipmentSlot";
    private static final String MODIFIER_SOURCE_CLASS = "io.lumine.mythic.lib.player.modifier.ModifierSource";

    private final JavaPlugin plugin;

    private boolean available;
    private Object statsManager;
    private Object flatModifierType;
    private Object otherEquipmentSlot;
    private Object otherModifierSource;
    private Method statManagerIsRegisteredMethod;
    private Method playerDataGetMethod;
    private Method playerDataGetStatMapMethod;
    private Method statMapGetInstanceMethod;
    private Method statMapUpdateMethod;
    private Method statInstanceRemoveMethod;
    // 持久修饰符
    private Constructor<?> statModifierConstructor;
    private Method statModifierRegisterMethod;
    // 临时修饰符
    private Constructor<?> tempModifierConstructor;
    private Method tempModifierRegisterMethod;
    private Method tempModifierCloseMethod;
    private Class<?> playerDataType;

    public DefaultMythicLibBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void initialize() {
        reset();

        Plugin mythicLib = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (mythicLib == null || !mythicLib.isEnabled()) {
            return;
        }

        try {
            ClassLoader cl = mythicLib.getClass().getClassLoader();
            Class<?> mythicLibClass = Class.forName(MYTHIC_LIB_CLASS, true, cl);
            playerDataType = Class.forName(PLAYER_DATA_CLASS, true, cl);
            Class<?> statMapClass = Class.forName(STAT_MAP_CLASS, true, cl);
            Class<?> statInstanceClass = Class.forName(STAT_INSTANCE_CLASS, true, cl);
            Class<?> statModifierClass = Class.forName(STAT_MODIFIER_CLASS, true, cl);
            Class<?> modifierTypeClass = Class.forName(MODIFIER_TYPE_CLASS, true, cl);
            Class<?> equipmentSlotClass = Class.forName(EQUIPMENT_SLOT_CLASS, true, cl);
            Class<?> modifierSourceClass = Class.forName(MODIFIER_SOURCE_CLASS, true, cl);

            Method instMethod = mythicLibClass.getMethod("inst");
            Method getStatsMethod = mythicLibClass.getMethod("getStats");
            Object mythicLibInstance = instMethod.invoke(null);
            statsManager = getStatsMethod.invoke(mythicLibInstance);
            statManagerIsRegisteredMethod = statsManager.getClass().getMethod("isRegistered", String.class);
            playerDataGetMethod = playerDataType.getMethod("get", OfflinePlayer.class);
            playerDataGetStatMapMethod = playerDataType.getMethod("getStatMap");
            statMapGetInstanceMethod = statMapClass.getMethod("getInstance", String.class);
            statMapUpdateMethod = statMapClass.getMethod("update", String.class);
            statInstanceRemoveMethod = statInstanceClass.getMethod("remove", String.class);

            flatModifierType = Enum.valueOf((Class<? extends Enum>) modifierTypeClass.asSubclass(Enum.class), "FLAT");
            otherEquipmentSlot = Enum.valueOf((Class<? extends Enum>) equipmentSlotClass.asSubclass(Enum.class), "OTHER");
            otherModifierSource = Enum.valueOf((Class<? extends Enum>) modifierSourceClass.asSubclass(Enum.class), "OTHER");

            // 持久修饰符
            statModifierConstructor = statModifierClass.getConstructor(
                String.class, String.class, double.class, modifierTypeClass, equipmentSlotClass, modifierSourceClass
            );
            statModifierRegisterMethod = statModifierClass.getMethod("register", playerDataType);

            // 临时修饰符
            try {
                Class<?> tempClass = Class.forName(TEMP_MODIFIER_CLASS, true, cl);
                tempModifierConstructor = tempClass.getConstructor(
                    String.class, String.class, double.class, modifierTypeClass, equipmentSlotClass, modifierSourceClass
                );
                tempModifierRegisterMethod = tempClass.getMethod("register", playerDataType, long.class);
                tempModifierCloseMethod = tempClass.getMethod("close");
            } catch (ReflectiveOperationException ignored) {
                // TemporaryStatModifier 可能不存在于某些版本
            }

            available = true;
            plugin.getLogger().fine("[MythicLib] 桥接初始化成功");
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("[MythicLib] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    @Override
    public boolean isRegisteredStat(String statId) {
        if (!available || statId == null) return false;
        try {
            Object result = statManagerIsRegisteredMethod.invoke(statsManager, statId);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException e) { return false; }
    }

    @Override
    public Object getPlayerData(Player player) {
        if (!available || player == null) return null;
        try { return playerDataGetMethod.invoke(null, player); } catch (ReflectiveOperationException e) { return null; }
    }

    @Override
    public Object getStatMap(Object playerData) {
        if (!available || playerData == null) return null;
        try { return playerDataGetStatMapMethod.invoke(playerData); } catch (ReflectiveOperationException e) { return null; }
    }

    // ─── 持久修饰符 ──────────────────────────────────────────────

    @Override
    public Object registerStatModifier(Object playerData, String modifierName, String statId, double value) {
        if (!available || playerData == null) return null;
        try {
            Object modifier = statModifierConstructor.newInstance(modifierName, statId, value, flatModifierType, otherEquipmentSlot, otherModifierSource);
            statModifierRegisterMethod.invoke(modifier, playerData);
            return modifier;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[MythicLib] registerStatModifier 失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void removeStatModifier(Object statMap, String statId, String modifierName) {
        if (!available || statMap == null) return;
        try {
            Object instance = statMapGetInstanceMethod.invoke(statMap, statId);
            if (instance != null) { statInstanceRemoveMethod.invoke(instance, modifierName); }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[MythicLib] removeStatModifier 失败: " + e.getMessage());
        }
    }

    @Override
    public void updateStat(Object statMap, String statId) {
        if (!available || statMap == null) return;
        try { statMapUpdateMethod.invoke(statMap, statId); } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[MythicLib] updateStat 失败: " + e.getMessage());
        }
    }

    // ─── 临时修饰符 ──────────────────────────────────────────────

    @Override
    public Object registerTemporaryModifier(Object playerData, String modifierName, String statId, double value, long durationMillis) {
        if (!available || playerData == null || tempModifierConstructor == null) return null;
        try {
            Object handle = tempModifierConstructor.newInstance(modifierName, statId, value, flatModifierType, otherEquipmentSlot, otherModifierSource);
            tempModifierRegisterMethod.invoke(handle, playerData, durationMillis);
            return handle;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[MythicLib] registerTemporaryModifier 失败: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void closeTemporaryModifier(Object handle) {
        if (!available || handle == null || tempModifierCloseMethod == null) return;
        try { tempModifierCloseMethod.invoke(handle); } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[MythicLib] closeTemporaryModifier 失败: " + e.getMessage());
        }
    }

    private void reset() {
        available = false;
        statsManager = null;
        flatModifierType = null;
        otherEquipmentSlot = null;
        otherModifierSource = null;
        statManagerIsRegisteredMethod = null;
        playerDataGetMethod = null;
        playerDataGetStatMapMethod = null;
        statMapGetInstanceMethod = null;
        statMapUpdateMethod = null;
        statInstanceRemoveMethod = null;
        statModifierConstructor = null;
        statModifierRegisterMethod = null;
        tempModifierConstructor = null;
        tempModifierRegisterMethod = null;
        tempModifierCloseMethod = null;
        playerDataType = null;
    }
}
