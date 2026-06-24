package xuanmo.arcartxsuite.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * TACZ（创世战术武器）枪械伤害事件。
 * <p>
 * 当 TACZ Mod 的 {@code EntityHurtByGunEvent.Pre} 触发时，由 {@code TaczCombatBridge}
 * 转换为标准 Bukkit 事件并广播。AXS 各模块可通过标准 Bukkit 事件机制监听此事件，
 * 以获取 TACZ 枪械伤害信息，而无需关心 Forge/NeoForge 事件总线的反射细节。
 * <p>
 * 此事件与 {@link org.bukkit.event.entity.EntityDamageByEntityEvent} 完全解耦：
 * TACZ 伤害不再被伪装为 Bukkit 原版事件，模块可明确区分枪械伤害与近战/弓箭等原版伤害。
 *
 * <pre>{@code
 * @EventHandler
 * public void onTaczDamage(TaczGunDamageEvent event) {
 *     Player attacker = event.getAttacker();
 *     LivingEntity target = event.getTarget();
 *     double damage = event.getDamage();
 *     boolean headshot = event.isHeadShot();
 *     // ...
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
@ApiStability.Stable
public final class TaczGunDamageEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player attacker;
    private final LivingEntity target;
    private final double damage;
    private final boolean headShot;
    private final String gunId;

    public TaczGunDamageEvent(@NotNull Player attacker, @NotNull LivingEntity target,
                               double damage, boolean headShot, @NotNull String gunId) {
        this.attacker = attacker;
        this.target = target;
        this.damage = damage;
        this.headShot = headShot;
        this.gunId = gunId;
    }

    /** 攻击者（开枪的玩家） */
    @NotNull
    public Player getAttacker() {
        return attacker;
    }

    /** 被击中的实体 */
    @NotNull
    public LivingEntity getTarget() {
        return target;
    }

    /** 伤害值（对应 TACZ {@code getBaseAmount()}，尚未经过护甲/抗性等减免） */
    public double getDamage() {
        return damage;
    }

    /** 是否为爆头 */
    public boolean isHeadShot() {
        return headShot;
    }

    /** 枪械 ID（如 {@code tacz:modern_kinetic_gun}） */
    @NotNull
    public String getGunId() {
        return gunId;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
