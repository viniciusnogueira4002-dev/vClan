package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanWar;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarManager {

    private final VClans plugin;
    // warId -> ClanWar
    private final Map<UUID, ClanWar> activeWars = new ConcurrentHashMap<>();
    // clanId -> warId
    private final Map<UUID, UUID> clanWarMap = new ConcurrentHashMap<>();
    // declarações pendentes: targetClanId -> (declaringClanId -> Instant)
    private final Map<UUID, Map<UUID, Instant>> pendingDeclarations = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask warCheckTask;

    public WarManager(VClans plugin) {
        this.plugin = plugin;
        loadWars();
        startWarCheckTask();
    }

    private void loadWars() {
        activeWars.clear();
        clanWarMap.clear();
        List<ClanWar> loaded = plugin.getRepository().loadAllWars();
        for (ClanWar war : loaded) {
            if (war.getState() == ClanWar.WarState.ACTIVE && !war.isExpired()) {
                activeWars.put(war.getWarId(), war);
                clanWarMap.put(war.getClanAId(), war.getWarId());
                clanWarMap.put(war.getClanBId(), war.getWarId());
            }
        }
    }

    private void startWarCheckTask() {
        this.warCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (ClanWar war : activeWars.values()) {
                if (war.isExpired()) {
                    war.checkTimeExpiration();
                    endWar(war);
                }
            }

            // Limpar declarações de guerra expiradas para não acumular na RAM
            Instant now = Instant.now();
            for (Map.Entry<UUID, Map<UUID, Instant>> entry : pendingDeclarations.entrySet()) {
                entry.getValue().values().removeIf(now::isAfter);
                if (entry.getValue().isEmpty()) {
                    pendingDeclarations.remove(entry.getKey());
                }
            }
        }, 1200L, 1200L); // a cada 1 minuto
    }

    public boolean isAtWar(UUID clanId) {
        return clanWarMap.containsKey(clanId);
    }

    public boolean areAtWar(UUID clanA, UUID clanB) {
        UUID warIdA = clanWarMap.get(clanA);
        UUID warIdB = clanWarMap.get(clanB);
        return warIdA != null && warIdA.equals(warIdB);
    }

    public ClanWar getActiveWar(UUID clanId) {
        UUID warId = clanWarMap.get(clanId);
        return warId != null ? activeWars.get(warId) : null;
    }

    public Set<UUID> getPendingWarDeclarationsForTarget(UUID targetClanId) {
        if (targetClanId == null) return Collections.emptySet();
        Map<UUID, Instant> map = pendingDeclarations.get(targetClanId);
        if (map == null || map.isEmpty()) return Collections.emptySet();
        Instant now = Instant.now();
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, Instant> entry : map.entrySet()) {
            if (now.isBefore(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public boolean declareWar(Clan clan, Clan target) {
        if (isAtWar(clan.getId()) || isAtWar(target.getId())) {
            return false;
        }

        pendingDeclarations.computeIfAbsent(target.getId(), k -> new ConcurrentHashMap<>())
                .put(clan.getId(), Instant.now().plusSeconds(86400)); // 24h

        String prefix = plugin.getMessages().getString("prefix", "");
        String msgDeclared = plugin.getMessages().getString("war.declared", "")
                .replace("{clan}", clan.getName())
                .replace("{target}", target.getName());

        Bukkit.broadcastMessage(ColorUtil.color(prefix + msgDeclared));
        return true;
    }

    public boolean acceptWar(Clan target, Clan challenger) {
        Map<UUID, Instant> map = pendingDeclarations.get(target.getId());
        if (map == null || !map.containsKey(challenger.getId())) return false;
        map.remove(challenger.getId());

        int targetKills = plugin.getConfig().getInt("wars.settings.target-kills-to-win", 30);
        int durationHours = plugin.getConfig().getInt("wars.settings.max-duration-hours", 48);

        ClanWar war = new ClanWar(UUID.randomUUID(), challenger.getId(), target.getId(), targetKills, durationHours);
        activeWars.put(war.getWarId(), war);
        clanWarMap.put(challenger.getId(), war.getWarId());
        clanWarMap.put(target.getId(), war.getWarId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveWar(war));

        String prefix = plugin.getMessages().getString("prefix", "");
        String msgAccepted = plugin.getMessages().getString("war.accepted", "")
                .replace("{clanA}", challenger.getName())
                .replace("{clanB}", target.getName());
        Bukkit.broadcastMessage(ColorUtil.color(prefix + msgAccepted));

        return true;
    }

    public void handleWarKill(Player killer, Player victim) {
        ClanManager cm = plugin.getClanManager();
        Clan clanKiller = cm.getClanByPlayer(killer);
        Clan clanVictim = cm.getClanByPlayer(victim);
        if (clanKiller == null || clanVictim == null) return;

        ClanWar war = getActiveWar(clanKiller.getId());
        if (war == null || !areAtWar(clanKiller.getId(), clanVictim.getId())) return;

        boolean finished = war.addKill(clanKiller.getId());

        String prefix = plugin.getMessages().getString("prefix", "");
        String killMsg = plugin.getMessages().getString("war.kill", "")
                .replace("{killer}", killer.getName())
                .replace("{victim}", victim.getName());
        clanKiller.broadcast(prefix + killMsg);
        clanVictim.broadcast(prefix + killMsg);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveWar(war));

        if (finished) {
            endWar(war);
        }
    }

    public void surrender(Clan surrenderingClan) {
        ClanWar war = getActiveWar(surrenderingClan.getId());
        if (war == null) return;

        war.surrender(surrenderingClan.getId());
        endWar(war);
    }

    private void endWar(ClanWar war) {
        activeWars.remove(war.getWarId());
        clanWarMap.remove(war.getClanAId());
        clanWarMap.remove(war.getClanBId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveWar(war));

        Clan clanA = plugin.getClanManager().getClanById(war.getClanAId());
        Clan clanB = plugin.getClanManager().getClanById(war.getClanBId());
        if (clanA == null || clanB == null) return;

        String prefix = plugin.getMessages().getString("prefix", "");
        UUID winnerId = war.getWinnerClanId();

        if (winnerId != null) {
            Clan winner = winnerId.equals(clanA.getId()) ? clanA : clanB;
            Clan loser = winnerId.equals(clanA.getId()) ? clanB : clanA;
            int kills = winnerId.equals(clanA.getId()) ? war.getKillsA() : war.getKillsB();

            winner.getStats().addWarWin();
            loser.getStats().addWarLoss();

            int winPts = plugin.getConfig().getInt("stats.points-per-war-win", 100);
            int lossPts = plugin.getConfig().getInt("stats.points-lost-on-war-loss", 50);

            plugin.getClanManager().addPoints(winner, winPts);
            plugin.getClanManager().removePoints(loser, lossPts);

            String endMsg = prefix + plugin.getMessages().getString("war.ended", "")
                    .replace("{winner}", winner.getName())
                    .replace("{kills}", String.valueOf(kills));
            Bukkit.broadcastMessage(ColorUtil.color(endMsg));
        }
    }

    public void cleanup() {
        if (warCheckTask != null) {
            warCheckTask.cancel();
            warCheckTask = null;
        }
        activeWars.clear();
        clanWarMap.clear();
        pendingDeclarations.clear();
    }
}

