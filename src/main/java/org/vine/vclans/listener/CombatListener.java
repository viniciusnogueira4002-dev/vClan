package org.vine.vclans.listener;

import org.vine.vclans.VClans;
import org.vine.vclans.manager.FriendlyFireManager;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Set;

public class CombatListener implements Listener {

    private final VClans plugin;
    private final FriendlyFireManager ffManager;

    private static final Set<PotionEffectType> HARMFUL_EFFECTS = Set.of(
            PotionEffectType.HARM,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.SLOW,
            PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS
    );

    public CombatListener(VClans plugin) {
        this.plugin = plugin;
        this.ffManager = plugin.getFriendlyFireManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = ffManager.resolvePlayerAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }

        if (ffManager.shouldBlockDamage(attacker, victim)) {
            event.setCancelled(true);
            event.setDamage(0.0);
            ffManager.notifyAttacker(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = ffManager.resolvePlayerAttacker(event.getCombuster());
        if (attacker == null) {
            return;
        }

        if (ffManager.shouldBlockDamage(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        ProjectileSource shooter = potion.getShooter();
        if (!(shooter instanceof Player attacker)) {
            return;
        }

        // Verificar se a poção contém efeitos nocivos
        boolean hasHarmful = false;
        for (PotionEffect effect : potion.getEffects()) {
            if (HARMFUL_EFFECTS.contains(effect.getType())) {
                hasHarmful = true;
                break;
            }
        }
        if (!hasHarmful) return;

        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim) {
                if (ffManager.shouldBlockDamage(attacker, victim)) {
                    event.setIntensity(victim, 0.0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        ProjectileSource source = cloud.getSource();
        if (!(source instanceof Player attacker)) {
            return;
        }

        // Remover aliados da lista de afetados se o efeito for prejudicial
        event.getAffectedEntities().removeIf(entity -> {
            if (entity instanceof Player victim) {
                return ffManager.shouldBlockDamage(attacker, victim);
            }
            return false;
        });
    }
}

