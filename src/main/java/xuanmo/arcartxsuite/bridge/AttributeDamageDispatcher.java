package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageListener;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageEvent.Source;
import xuanmo.arcartxsuite.api.attribute.AttributeHealEvent;
import xuanmo.arcartxsuite.api.attribute.AttributeHealListener;
import xuanmo.arcartxsuite.api.attribute.AttributeHealEvent.SourceType;

/**
 * 统一属性伤害事件分发器。
 * <p>
 * 为所有已对接的属性插件注册底层 Bukkit 事件监听，将伤害事件归一化为
 * {@link AttributeDamageEvent} 后分发给已注册的 {@link AttributeDamageListener}。
 * 新增属性插件时只需在此类中增加对应的注册逻辑，各模块无需改动。
 */
final class AttributeDamageDispatcher {

    private final JavaPlugin plugin;
    private final List<AttributeDamageListener> listeners = new CopyOnWriteArrayList<>();
    private final List<AttributeHealListener> healListeners = new CopyOnWriteArrayList<>();
    private final List<Listener> registeredListeners = new CopyOnWriteArrayList<>();

    AttributeDamageDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void registerListener(AttributeDamageListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    void unregisterListener(AttributeDamageListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    boolean hasAnyListener() {
        return !listeners.isEmpty();
    }

    void registerHealListener(AttributeHealListener listener) {
        if (listener != null) {
            healListeners.add(listener);
        }
    }

    void unregisterHealListener(AttributeHealListener listener) {
        if (listener != null) {
            healListeners.remove(listener);
        }
    }

    /** 初始化所有可用属性插件的伤害事件监听 */
    void initialize(boolean attributePlusEnabled, boolean craneAttributeEnabled, boolean mythicLibEnabled, boolean symphonyEnabled) {
        shutdown();
        if (attributePlusEnabled) registerAttributePlus();
        if (craneAttributeEnabled) {
            registerCraneAttribute();
            registerCraneAttributeHeal();
        }
        if (mythicLibEnabled) registerMythicLib();
        if (symphonyEnabled) {
            registerSymphony();
            registerSymphonyHeal();
        }
        registerBukkitFallback();
    }

    /** 清理所有 Bukkit 监听器 */
    void shutdown() {
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
        healListeners.clear();
    }

    // ─── AttributePlus ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerAttributePlus() {
        Plugin attributePlus = Bukkit.getPluginManager().getPlugin("AttributePlus");
        if (attributePlus == null || !attributePlus.isEnabled()) {
            return;
        }
        try {
            ClassLoader cl = attributePlus.getClass().getClassLoader();
            // 优先尝试 AttrEntityDamageEvent（较新版本）
            final Class<?> damageEventClass;
            Class<?> candidateClass = tryLoadClass(
                "org.serverct.ersha.api.event.AttrEntityDamageEvent", cl
            );
            if (candidateClass == null) {
                // 旧版本回退到 AttrEntityAttackEvent
                candidateClass = tryLoadClass(
                    "org.serverct.ersha.api.event.AttrEntityAttackEvent", cl
                );
            }
            if (candidateClass == null || !Event.class.isAssignableFrom(candidateClass)) {
                return;
            }
            damageEventClass = candidateClass;

            Class<? extends Event> eventClass = (Class<? extends Event>) damageEventClass;
            Listener listener;
            if (damageEventClass.getName().contains("AttrEntityDamageEvent")) {
                Method getAttacker = damageEventClass.getMethod("getAttacker");
                Method getAttackDamage = damageEventClass.getMethod("getAttackDamage");
                Method getTarget = damageEventClass.getMethod("getTarget");
                listener = new Listener() {};
                Bukkit.getPluginManager().registerEvent(
                    eventClass, listener, EventPriority.MONITOR,
                    (l, e) -> dispatchAttributePlusDamage(e, damageEventClass, getAttacker, getAttackDamage, getTarget),
                    plugin, true
                );
            } else {
                // AttrEntityAttackEvent 旧版路径
                Method getAttackerOrKiller = damageEventClass.getMethod("getAttackerOrKiller");
                Method getEntity = damageEventClass.getMethod("getEntity");
                Method getAttributeHandle = damageEventClass.getMethod("getAttributeHandle");
                listener = new Listener() {};
                Bukkit.getPluginManager().registerEvent(
                    eventClass, listener, EventPriority.MONITOR,
                    (l, e) -> dispatchAttributePlusAttack(e, damageEventClass, getAttackerOrKiller, getEntity, getAttributeHandle),
                    plugin, true
                );
            }
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchAttributePlusDamage(
        Event event, Class<?> eventClass,
        Method getAttacker, Method getAttackDamage, Method getTarget
    ) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object rawAttacker = getAttacker.invoke(event);
            Object rawDamage = getAttackDamage.invoke(event);
            Object rawTarget = getTarget.invoke(event);
            if (!(rawAttacker instanceof Player attacker) || !(rawTarget instanceof Entity target)) return;
            double damage = rawDamage instanceof Number n ? n.doubleValue() : 0.0D;
            if (damage <= 0.0D) return;
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.ATTRIBUTE_PLUS));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[AttributeDamageDispatcher] AttributePlus damage dispatch failed: " + e.getMessage());
        }
    }

    private void dispatchAttributePlusAttack(
        Event event, Class<?> eventClass,
        Method getAttackerOrKiller, Method getEntity, Method getAttributeHandle
    ) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object rawAttacker = getAttackerOrKiller.invoke(event);
            Object rawTarget = getEntity.invoke(event);
            if (!(rawAttacker instanceof Player attacker) || !(rawTarget instanceof LivingEntity target)) return;
            Object handle = getAttributeHandle.invoke(event);
            if (handle == null) return;
            Method getDamage = handle.getClass().getMethod("getDamage", LivingEntity.class);
            Object rawDamage = getDamage.invoke(handle, attacker);
            double damage = rawDamage instanceof Number n ? n.doubleValue() : 0.0D;
            if (damage <= 0.0D) return;
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.ATTRIBUTE_PLUS));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[AttributeDamageDispatcher] AttributePlus attack dispatch failed: " + e.getMessage());
        }
    }

    // ─── CraneAttribute ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerCraneAttribute() {
        Plugin crane = Bukkit.getPluginManager().getPlugin("CraneAttribute");
        if (crane == null || !crane.isEnabled()) return;
        try {
            ClassLoader cl = crane.getClass().getClassLoader();
            Class<?> rawClass = Class.forName(
                "cn.org.bukkit.craneattribute.api.event.trigger.AttackAndDefenseTriggerEvent$After",
                true, cl
            );
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getHandler = rawClass.getMethod("getHandler");
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (l, e) -> dispatchCraneAttribute(e, rawClass, getHandler),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchCraneAttribute(Event event, Class<?> eventClass, Method getHandler) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object handler = getHandler.invoke(event);
            if (handler == null) return;
            Method getEntity = handler.getClass().getMethod("getEntity");
            Object rawTarget = getEntity.invoke(handler);
            if (!(rawTarget instanceof LivingEntity target)) return;

            double damage = 0.0D;
            try {
                Method getDamage = handler.getClass().getMethod("getDamage", LivingEntity.class);
                Object rawDamage = getDamage.invoke(handler, target);
                if (rawDamage instanceof Number n) damage = n.doubleValue();
            } catch (NoSuchMethodException ignored) {
                Method getEvent = handler.getClass().getMethod("getEvent");
                Object rawEvent = getEvent.invoke(handler);
                if (rawEvent instanceof org.bukkit.event.entity.EntityDamageEvent de) {
                    damage = de.getFinalDamage();
                }
            }
            if (damage <= 0.0D) return;

            Player attacker = null;
            if (handler.getClass().getMethod("getAttacker") != null) {
                Object rawAttacker = handler.getClass().getMethod("getAttacker").invoke(handler);
                if (rawAttacker instanceof Player p) attacker = p;
            }
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.CRANE_ATTRIBUTE));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[AttributeDamageDispatcher] CraneAttribute dispatch failed: " + e.getMessage());
        }
    }

    // ─── CraneAttribute Heal ───────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerCraneAttributeHeal() {
        Plugin crane = Bukkit.getPluginManager().getPlugin("CraneAttribute");
        if (crane == null || !crane.isEnabled()) return;
        try {
            ClassLoader cl = crane.getClass().getClassLoader();
            Class<?> rawClass = Class.forName(
                "cn.org.bukkit.craneattribute.api.event.trigger.RegainHealthTriggerEvent$After",
                true, cl
            );
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getHandler = rawClass.getMethod("getHandler");
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (l, e) -> dispatchCraneAttributeHeal(e, rawClass, getHandler),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchCraneAttributeHeal(Event event, Class<?> eventClass, Method getHandler) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object handler = getHandler.invoke(event);
            if (handler == null) return;
            Method getEntity = handler.getClass().getMethod("getEntity");
            Object rawTarget = getEntity.invoke(handler);
            if (!(rawTarget instanceof LivingEntity target)) return;

            double heal = 0.0D;
            try {
                Method getHeal = handler.getClass().getMethod("getHeal", LivingEntity.class);
                Object rawHeal = getHeal.invoke(handler, target);
                if (rawHeal instanceof Number n) heal = n.doubleValue();
            } catch (NoSuchMethodException ignored) {
                Method getEvent = handler.getClass().getMethod("getEvent");
                Object rawEvent = getEvent.invoke(handler);
                if (rawEvent instanceof EntityRegainHealthEvent he) {
                    heal = he.getAmount();
                }
            }
            if (heal <= 0.0D) return;

            Player source = null;
            try {
                Object rawSource = handler.getClass().getMethod("getSource").invoke(handler);
                if (rawSource instanceof Player p) source = p;
            } catch (ReflectiveOperationException ignored) {
            }
            dispatchHeal(new AttributeHealEvent(source, target, heal, SourceType.CRANE_ATTRIBUTE));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[AttributeDamageDispatcher] CraneAttribute heal dispatch failed: " + e.getMessage());
        }
    }

    // ─── MythicLib ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerMythicLib() {
        Plugin mythicLib = Bukkit.getPluginManager().getPlugin("MythicLib");
        if (mythicLib == null || !mythicLib.isEnabled()) return;
        try {
            ClassLoader cl = mythicLib.getClass().getClassLoader();
            Class<?> rawClass = Class.forName(
                "io.lumine.mythic.lib.api.event.AttackUnregisteredEvent", true, cl
            );
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getEntity = rawClass.getMethod("getEntity");
            Method toBukkit = rawClass.getMethod("toBukkit");
            Method getDamage = rawClass.getMethod("getDamage");
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (l, e) -> dispatchMythicLib(e, rawClass, getEntity, toBukkit, getDamage),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchMythicLib(
        Event event, Class<?> eventClass,
        Method getEntity, Method toBukkit, Method getDamage
    ) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object rawTarget = getEntity.invoke(event);
            if (!(rawTarget instanceof Entity target)) return;

            double damage;
            Object bukkitEvent = toBukkit.invoke(event);
            if (bukkitEvent instanceof org.bukkit.event.entity.EntityDamageEvent de) {
                damage = de.getFinalDamage();
            } else {
                Object rawDamage = getDamage.invoke(event);
                if (rawDamage instanceof Number n) {
                    damage = n.doubleValue();
                } else {
                    // getDamage() returns DamageMetadata, try getDamage() on it
                    Method getDamageValue = rawDamage.getClass().getMethod("getDamage");
                    Object rawValue = getDamageValue.invoke(rawDamage);
                    damage = rawValue instanceof Number n2 ? n2.doubleValue() : 0.0D;
                }
            }
            if (damage <= 0.0D) return;

            Player attacker = target instanceof LivingEntity le ? resolveMythicLibAttacker(le) : null;
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.MYTHIC_LIB));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[AttributeDamageDispatcher] MythicLib dispatch failed: " + e.getMessage());
        }
    }

    private Player resolveMythicLibAttacker(LivingEntity target) {
        if (target.getLastDamageCause() instanceof EntityDamageByEntityEvent de) {
            Entity damager = de.getDamager();
            if (damager instanceof Player p) return p;
            if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) return p;
        }
        return null;
    }

    // ─── Symphony ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerSymphony() {
        Plugin symphony = Bukkit.getPluginManager().getPlugin("Symphony");
        if (symphony == null || !symphony.isEnabled()) return;
        try {
            ClassLoader cl = symphony.getClass().getClassLoader();
            Class<?> rawClass = Class.forName(
                "priv.seventeen.artist.symphony.api.event.SymphonyDamageEvent", true, cl
            );
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getAttacker = rawClass.getMethod("getAttacker");
            Method getVictim = rawClass.getMethod("getVictim");
            Method getFinalDamage = rawClass.getMethod("getFinalDamage");
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (l, e) -> dispatchSymphony(e, rawClass, getAttacker, getVictim, getFinalDamage),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchSymphony(
        Event event, Class<?> eventClass,
        Method getAttacker, Method getVictim, Method getFinalDamage
    ) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object rawAttacker = getAttacker.invoke(event);
            Object rawVictim = getVictim.invoke(event);
            if (!(rawVictim instanceof Entity target)) return;
            Player attacker = null;
            if (rawAttacker instanceof Player p) attacker = p;
            Object rawDamage = getFinalDamage.invoke(event);
            double damage = rawDamage instanceof Number n ? n.doubleValue() : 0.0D;
            if (damage <= 0.0D) return;
            dispatch(new AttributeDamageEvent(attacker, target, damage, Source.SYMPHONY));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[AttributeDamageDispatcher] Symphony damage dispatch failed: " + e.getMessage());
        }
    }

    // ─── Symphony Heal ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void registerSymphonyHeal() {
        Plugin symphony = Bukkit.getPluginManager().getPlugin("Symphony");
        if (symphony == null || !symphony.isEnabled()) return;
        try {
            ClassLoader cl = symphony.getClass().getClassLoader();
            Class<?> rawClass = Class.forName(
                "priv.seventeen.artist.symphony.api.event.SymphonyHealEvent", true, cl
            );
            if (!Event.class.isAssignableFrom(rawClass)) return;
            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Method getSource = rawClass.getMethod("getSource");
            Method getTarget = rawClass.getMethod("getTarget");
            Method getAmount = rawClass.getMethod("getAmount");
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                eventClass, listener, EventPriority.MONITOR,
                (l, e) -> dispatchSymphonyHeal(e, rawClass, getSource, getTarget, getAmount),
                plugin, true
            );
            registeredListeners.add(listener);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void dispatchSymphonyHeal(
        Event event, Class<?> eventClass,
        Method getSource, Method getTarget, Method getAmount
    ) {
        if (!eventClass.isInstance(event)) return;
        try {
            Object rawSource = getSource.invoke(event);
            Object rawTarget = getTarget.invoke(event);
            if (!(rawTarget instanceof LivingEntity target)) return;
            Player source = null;
            if (rawSource instanceof Player p) source = p;
            Object rawAmount = getAmount.invoke(event);
            double amount = rawAmount instanceof Number n ? n.doubleValue() : 0.0D;
            if (amount <= 0.0D) return;
            dispatchHeal(new AttributeHealEvent(source, target, amount, SourceType.SYMPHONY));
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("[AttributeDamageDispatcher] Symphony heal dispatch failed: " + e.getMessage());
        }
    }

    // ─── Bukkit Fallback ───────────────────────────────────────

    private void registerBukkitFallback() {
        // Bukkit EntityDamageByEntityEvent 已由模块自行决定是否监听，
        // 此处不重复注册，避免与模块自身的 Bukkit 监听冲突。
    }

    // ─── Dispatch ──────────────────────────────────────────────

    private void dispatch(AttributeDamageEvent event) {
        for (AttributeDamageListener listener : listeners) {
            try {
                listener.onAttributeDamage(event);
            } catch (Exception e) {
                plugin.getLogger().warning("[AttributeDamageDispatcher] Listener " + listener.getClass().getName() + " threw: " + e.getMessage());
            }
        }
    }

    private void dispatchHeal(AttributeHealEvent event) {
        for (AttributeHealListener listener : healListeners) {
            try {
                listener.onAttributeHeal(event);
            } catch (Exception e) {
                plugin.getLogger().warning("[AttributeDamageDispatcher] Heal listener " + listener.getClass().getName() + " threw: " + e.getMessage());
            }
        }
    }

    private static Class<?> tryLoadClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, true, cl);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
