package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.projectiles.ProjectileSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FriendlyFireManager {

    private final VClans plugin;
    private final Map<UUID, Instant> warningThrottle = new ConcurrentHashMap<>();

    public FriendlyFireManager(VClans plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private void startCleanupTask() {
        // Limpar throttles expirados a cada 2 minutos para liberar RAM
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Instant threshold = Instant.now().minusSeconds(10);
            warningThrottle.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
        }, 2400L, 2400L);
    }

    public void removeAttacker(UUID playerUuid) {
        if (playerUuid != null) {
            warningThrottle.remove(playerUuid);
        }
    }

    public Player resolvePlayerAttacker(Entity damager) {
        if (damager == null) return null;

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }

        if (damager instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return player;
            }
        }

        if (damager instanceof AreaEffectCloud cloud) {
            ProjectileSource source = cloud.getSource();
            if (source instanceof Player player) {
                return player;
            }
        }

        if (damager instanceof Tameable tameable && tameable.isTamed()) {
            AnimalTamer owner = tameable.getOwner();
            if (owner instanceof Player player) {
                return player;
            }
        }

        if (damager instanceof LightningStrike) {
            try {
                java.lang.reflect.Method m = damager.getClass().getMethod("getCausingEntity");
                Object src = m.invoke(damager);
                if (src instanceof Player player) {
                    return player;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    public boolean shouldBlockDamage(Player attacker, Player victim) {
        if (attacker == null || victim == null) return false;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return false; // Dano a si mesmo (ex: ender pearl, explosões próprias) permitido

        // Verificar permissão administrativa de bypass
        if (attacker.hasPermission("vclans.bypass.friendlyfire") || attacker.hasPermission("eclypseclans.bypass.friendlyfire")) {
            return false;
        }

        // Verificar whitelist de mundos se configurada
        List<String> whitelistWorlds = plugin.getConfig().getStringList("friendly-fire.whitelist-worlds");
        if (!whitelistWorlds.isEmpty() && whitelistWorlds.contains(victim.getWorld().getName())) {
            return false;
        }

        // Se ambos estiverem em um duelo ativo um contra o outro, o dano NÃO deve ser bloqueado
        if (plugin.getDuelManager().areOpponentsInActiveDuel(attacker.getUniqueId(), victim.getUniqueId())) {
            return false;
        }

        ClanManager clanManager = plugin.getClanManager();
        Clan clanAttacker = clanManager.getClanByPlayer(attacker);
        Clan clanVictim = clanManager.getClanByPlayer(victim);

        if (clanAttacker == null || clanVictim == null) {
            return false;
        }

        // Membros do mesmo clã
        if (clanAttacker.getId().equals(clanVictim.getId())) {
            boolean blockSameClan = plugin.getConfig().getBoolean("friendly-fire.block-same-clan", true);
            if (!blockSameClan) {
                return false;
            }
            boolean allowToggle = plugin.getConfig().getBoolean("friendly-fire.allow-clan-toggle", true);
            if (allowToggle && clanAttacker.getSettings().isFriendlyFire()) {
                return false; // Líder/Admin ativou fogo amigo internamente: dano permitido!
            }
            return true; // Bloqueado! Proteção de clã ativa.
        }

        // Membros de clãs aliados
        if (clanAttacker.isAlly(clanVictim.getId())) {
            boolean blockAllies = plugin.getConfig().getBoolean("friendly-fire.block-allies", true);
            return blockAllies;
        }

        return false;
    }

    public void notifyAttacker(Player attacker) {
        if (!plugin.getConfig().getBoolean("friendly-fire.notify-attacker", true)) return;

        Instant now = Instant.now();
        Instant lastWarn = warningThrottle.get(attacker.getUniqueId());
        if (lastWarn != null && now.isBefore(lastWarn.plusMillis(1500))) {
            return; // Throttle para não lotar o chat em ataques rápidos
        }
        warningThrottle.put(attacker.getUniqueId(), now);

        String prefix = plugin.getMessages().getString("prefix", "");
        String msg = plugin.getMessages().getString("clan.friendly-fire-blocked", "&#FF5555Você não pode ferir um aliado do seu clã!");
        attacker.sendMessage(ColorUtil.color(prefix + msg));
        try {
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
        } catch (Exception ignored) {}
    }
}

