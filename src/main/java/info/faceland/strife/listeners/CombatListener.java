/******************************************************************************
 * Copyright (c) 2014, Richard Harrah                                         *
 *                                                                            *
 * Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.
 *                                                                            *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 ******************************************************************************/

package info.faceland.strife.listeners;

import info.faceland.messaging.Chatty;
import info.faceland.strife.StrifePlugin;
import info.faceland.strife.attributes.StrifeAttribute;
import info.faceland.strife.data.Champion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.Random;

public class CombatListener implements Listener {

    private final StrifePlugin plugin;
    private final Random random;

    public CombatListener(StrifePlugin plugin) {
        this.plugin = plugin;
        random = new Random(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntityMonitor(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            ((Player) event.getDamager()).sendMessage("final damage: " + event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        LivingEntity a;
        if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        // LET THE DATA GATHERING COMMENCE
        boolean melee = true;
        if (event.getDamager() instanceof LivingEntity) {
            a = (LivingEntity) event.getDamager();
        } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof LivingEntity) {
            a = (LivingEntity) ((Projectile) event.getDamager()).getShooter();
            melee = false;
        } else {
            return;
        }
        LivingEntity b = (LivingEntity) event.getEntity();
        if (b instanceof Player) {
            double chance = plugin.getChampionManager().getChampion(b.getUniqueId()).getAttributeValues().get(StrifeAttribute.EVASION);
            if (random.nextDouble() < chance) {
                event.setCancelled(true);
                Chatty.sendMessage((Player) b, "<green>You evaded an attack!");
                if (a instanceof Player) {
                    Chatty.sendMessage((Player) a, "<white>%player%<red> evaded your attack!",
                                       new String[][]{{"%player%", ((Player) a).getDisplayName()}});
                }
                return;
            }
        }
        double damage = (a instanceof Player) ? 0 : event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        double meleeDamageA = StrifeAttribute.MELEE_DAMAGE.getBaseValue(), attackSpeedA;
        double criticalDamageA = StrifeAttribute.CRITICAL_DAMAGE.getBaseValue(), armorPenA = StrifeAttribute.ARMOR_PENETRATION.getBaseValue();
        double lifeStealA = StrifeAttribute.LIFE_STEAL.getBaseValue(), lifeStolenA = 0D, playerHealthA = b.getHealth();
        double rangedDamageA = StrifeAttribute.RANGED_DAMAGE.getBaseValue(), criticalRateA = StrifeAttribute.CRITICAL_RATE.getBaseValue();
        double attackSpeedMultA = 1D;
        double armorB = StrifeAttribute.ARMOR.getBaseValue(), reflectDamageB = StrifeAttribute.DAMAGE_REFLECT.getBaseValue();
        double parryB, blockB = StrifeAttribute.BLOCK.getBaseValue();
        boolean blocking = false;
        boolean parried = false;
        if (a instanceof Player) {
            for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
                if (event.isApplicable(modifier)) {
                    event.setDamage(modifier, 0D);
                }
            }
            Player p = (Player) a;
            Champion champ = plugin.getChampionManager().getChampion(p.getUniqueId());
            Map<StrifeAttribute, Double> vals = champ.getAttributeValues();
            meleeDamageA = vals.get(StrifeAttribute.MELEE_DAMAGE);
            attackSpeedA = vals.get(StrifeAttribute.ATTACK_SPEED) / StrifeAttribute.ATTACK_SPEED.getBaseValue();
            criticalDamageA = vals.get(StrifeAttribute.CRITICAL_DAMAGE) / StrifeAttribute.CRITICAL_DAMAGE.getBaseValue();
            armorPenA = vals.get(StrifeAttribute.ARMOR_PENETRATION);
            lifeStealA = vals.get(StrifeAttribute.LIFE_STEAL);
            playerHealthA = a.getHealth();
            rangedDamageA = vals.get(StrifeAttribute.RANGED_DAMAGE);
            criticalRateA = vals.get(StrifeAttribute.CRITICAL_RATE);
            long timeLeft = plugin.getAttackSpeedTask().getTimeLeft(a.getUniqueId());
            long timeToSet = Math.round(Math.max(4.0 * attackSpeedA, 0.0));
            if (timeLeft > 0) {
                attackSpeedMultA = Math.min(1.0, Math.max(1.0 - 1.0 * timeLeft / timeToSet, 0.0));
            }
            plugin.getAttackSpeedTask().setTimeLeft(a.getUniqueId(), timeToSet);
            ((Player) a).sendMessage("meleeDamageA: " + meleeDamageA);
            ((Player) a).sendMessage("attackSpeedA: " + attackSpeedA);
            ((Player) a).sendMessage("attackSpeedMultA: " + attackSpeedMultA);
        } else {
            meleeDamageA = damage;
        }
        if (b instanceof Player) {
            Player p = (Player) b;
            Champion champ = plugin.getChampionManager().getChampion(p.getUniqueId());
            Map<StrifeAttribute, Double> vals = champ.getAttributeValues();
            armorB = vals.get(StrifeAttribute.ARMOR);
            reflectDamageB = vals.get(StrifeAttribute.DAMAGE_REFLECT);
            parryB = vals.get(StrifeAttribute.PARRY);
            blockB = vals.get(StrifeAttribute.BLOCK);
            if (((Player) b).isBlocking()) {
                blocking = true;
                if (random.nextDouble() < parryB) {
                    parried = true;
                }
            }
        }

        // LET THE DAMAGE CALCULATION COMMENCE
        if (melee) {
            if (blocking) {
                if (parried) {
                    damage = meleeDamageA * attackSpeedMultA;
                    a.damage(damage * 0.25);
                    event.setCancelled(true);
                    return;
                }
                damage = meleeDamageA * attackSpeedMultA;
                if (random.nextDouble() < criticalRateA) {
                    damage = damage * criticalDamageA;
                }
                double damageReducer = (1 - (armorB)) * (1 - (armorPenA));
                damage = damage * damageReducer;
                damage = damage * (1 - blockB);
                lifeStolenA = damage * lifeStealA;
                event.setDamage(damage);
                a.setHealth(Math.min(playerHealthA + lifeStolenA, a.getMaxHealth()));
                a.damage(damage * reflectDamageB);
                return;
            }
            damage = meleeDamageA * attackSpeedMultA;
            if (random.nextDouble() < criticalRateA) {
                damage = damage * criticalDamageA;
            }
            double damageReducer = (1 - (armorB)) * (1 - (armorPenA));
            damage = damage * damageReducer;
            lifeStolenA = damage * lifeStealA;
            event.setDamage(damage);
            a.setHealth(Math.min(playerHealthA + lifeStolenA, a.getMaxHealth()));
            a.damage(damage * reflectDamageB);
            return;
        }
    }

}