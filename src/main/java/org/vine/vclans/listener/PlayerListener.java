package org.vine.vclans.listener;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanInvite;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener {

    private final VClans plugin;
    // killerUuid -> (victimUuid -> Instant)
    private final Map<UUID, Map<UUID, Instant>> killCooldowns = new ConcurrentHashMap<>();

    public PlayerListener(VClans plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private void startCleanupTask() {
        // Limpar cooldowns de mortes expirados a cada 5 minutos para liberar memória
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int cooldownSeconds = plugin.getConfig().getInt("stats.kill-cooldown-seconds", 300);
            Instant threshold = Instant.now().minusSeconds(cooldownSeconds);
            for (Map.Entry<UUID, Map<UUID, Instant>> entry : killCooldowns.entrySet()) {
                entry.getValue().values().removeIf(instant -> instant.isBefore(threshold));
                if (entry.getValue().isEmpty()) {
                    killCooldowns.remove(entry.getKey());
                }
            }
        }, 6000L, 6000L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan != null) {
            ClanMember member = clan.getMember(player.getUniqueId());
            if (member != null) {
                member.setLastKnownName(player.getName());
                member.updateLastSeen();
            }
        }

        plugin.getScoreboardManager().updatePlayer(player);
        plugin.getScoreboardManager().syncAllForPlayer(player);

        // Delay de 20 ticks (1s) para re-aplicar após plugins de scoreboard/hub definirem scoreboards customizados
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().updatePlayer(player);
                plugin.getScoreboardManager().syncAllForPlayer(player);
            }
        }, 20L);

        // Notificar sobre convites pendentes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            List<ClanInvite> invites = plugin.getInviteManager().getInvites(player.getUniqueId());
            if (!invites.isEmpty()) {
                String prefix = plugin.getMessages().getString("prefix", "");
                player.sendMessage(ColorUtil.color(prefix + "&eVocê possui &6" + invites.size() + " &econvite(s) de clã pendente(s)! Use &f/clan &epara visualizar."));
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan != null) {
            ClanMember member = clan.getMember(uuid);
            if (member != null) {
                member.updateLastSeen();
            }
        }

        plugin.getDuelManager().handlePlayerQuit(player);
        plugin.getScoreboardManager().removePlayer(player);

        // Liberar referências de memória no quit
        killCooldowns.remove(uuid);
        plugin.getFriendlyFireManager().removeAttacker(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // 1. Duelos
        if (plugin.getDuelManager().isPlayerInDuel(victim.getUniqueId())) {
            plugin.getDuelManager().handlePlayerDeath(victim, killer);
            return;
        }

        // 2. Guerras e PvP comum
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            Clan clanKiller = plugin.getClanManager().getClanByPlayer(killer);
            Clan clanVictim = plugin.getClanManager().getClanByPlayer(victim);

            // Se pertencerem à mesma guerra
            if (clanKiller != null && clanVictim != null && plugin.getWarManager().areAtWar(clanKiller.getId(), clanVictim.getId())) {
                plugin.getWarManager().handleWarKill(killer, victim);
            }

            // Estatísticas gerais de combate entre clãs
            if (clanKiller != null && (clanVictim == null || !clanKiller.getId().equals(clanVictim.getId()))) {
                // Verificar anti-farming
                if (isLegitimateKill(killer, victim)) {
                    clanKiller.getStats().addKill();
                    int ptsGain = plugin.getConfig().getInt("stats.points-per-kill", 5);
                    plugin.getClanManager().addPoints(clanKiller, ptsGain);

                    if (clanVictim != null) {
                        clanVictim.getStats().addDeath();
                        int ptsLoss = plugin.getConfig().getInt("stats.points-lost-on-death", 2);
                        plugin.getClanManager().removePoints(clanVictim, ptsLoss);
                    }
                }
            }
        }
    }

    private boolean isLegitimateKill(Player killer, Player victim) {
        // Anti-Same-IP farming
        if (plugin.getConfig().getBoolean("stats.prevent-same-ip-farming", true)) {
            if (killer.getAddress() != null && victim.getAddress() != null) {
                String ipKiller = killer.getAddress().getHostString();
                String ipVictim = victim.getAddress().getHostString();
                if (ipKiller.equalsIgnoreCase(ipVictim)) {
                    return false;
                }
            }
        }

        // Anti-Repeat Kill cooldown
        int cooldownSeconds = plugin.getConfig().getInt("stats.kill-cooldown-seconds", 300);
        if (cooldownSeconds > 0) {
            Map<UUID, Instant> killerMap = killCooldowns.computeIfAbsent(killer.getUniqueId(), k -> new ConcurrentHashMap<>());
            Instant lastKill = killerMap.get(victim.getUniqueId());
            if (lastKill != null && Instant.now().isBefore(lastKill.plusSeconds(cooldownSeconds))) {
                return false;
            }
            killerMap.put(victim.getUniqueId(), Instant.now());
        }

        return true;
    }
}

