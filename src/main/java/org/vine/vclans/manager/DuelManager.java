package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.ActiveDuel;
import org.vine.vclans.model.Arena;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.DuelChallenge;
import org.vine.vclans.util.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DuelManager {

    private final VClans plugin;
    // targetClanId -> (challengerClanId -> DuelChallenge)
    private final Map<UUID, Map<UUID, DuelChallenge>> pendingChallenges = new ConcurrentHashMap<>();
    // active duels
    private final Map<UUID, ActiveDuel> activeDuels = new ConcurrentHashMap<>();
    // playerUuid -> activeDuel
    private final Map<UUID, ActiveDuel> playerDuelMap = new ConcurrentHashMap<>();

    private BukkitTask duelLoopTask;

    public DuelManager(VClans plugin) {
        this.plugin = plugin;
        startDuelLoop();
    }

    private void startDuelLoop() {
        this.duelLoopTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Limpar desafios expirados
            for (Map.Entry<UUID, Map<UUID, DuelChallenge>> entry : pendingChallenges.entrySet()) {
                entry.getValue().values().removeIf(DuelChallenge::isExpired);
                if (entry.getValue().isEmpty()) {
                    pendingChallenges.remove(entry.getKey());
                }
            }

            // Atualizar duelos ativos
            for (ActiveDuel duel : activeDuels.values()) {
                if (duel.getState() == ActiveDuel.DuelState.COUNTDOWN) {
                    duel.decrementCountdown();
                    int cd = duel.getCountdown();
                    if (cd > 0) {
                        broadcastToDuel(duel, "&eO duelo começará em &c" + cd + " &esegundos!", Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                    } else {
                        duel.setState(ActiveDuel.DuelState.FIGHTING);
                        broadcastToDuel(duel, "&a&lO DUELO COMEÇOU! LUTEM PELA GLÓRIA DO SEU CLÃ!", Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }
                } else if (duel.getState() == ActiveDuel.DuelState.FIGHTING) {
                    duel.decrementMatchTimer();
                    if (duel.isFinished()) {
                        finishDuel(duel, duel.getWinnerClanId());
                    }
                }
            }
        }, 20L, 20L); // a cada 1 segundo
    }

    public boolean isDueling(UUID clanId) {
        return activeDuels.containsKey(clanId);
    }

    public boolean isPlayerInDuel(UUID playerUuid) {
        return playerDuelMap.containsKey(playerUuid);
    }

    public ActiveDuel getPlayerDuel(UUID playerUuid) {
        return playerDuelMap.get(playerUuid);
    }

    public List<DuelChallenge> getPendingChallengesForTarget(UUID targetClanId) {
        if (targetClanId == null) return Collections.emptyList();
        Map<UUID, DuelChallenge> map = pendingChallenges.get(targetClanId);
        if (map == null || map.isEmpty()) return Collections.emptyList();
        List<DuelChallenge> result = new ArrayList<>();
        for (DuelChallenge challenge : map.values()) {
            if (!challenge.isExpired()) {
                result.add(challenge);
            }
        }
        return result;
    }

    public boolean areOpponentsInActiveDuel(UUID playerA, UUID playerB) {
        ActiveDuel duelA = playerDuelMap.get(playerA);
        ActiveDuel duelB = playerDuelMap.get(playerB);
        if (duelA == null || duelB == null) return false;
        if (duelA != duelB) return false;
        if (duelA.getState() != ActiveDuel.DuelState.FIGHTING) return false;

        boolean aInTeamA = duelA.getTeamA().contains(playerA);
        boolean bInTeamA = duelA.getTeamA().contains(playerB);
        return aInTeamA != bInTeamA; // Times opostos
    }

    public boolean sendChallenge(Clan challenger, Clan target, String arenaName, Player sender) {
        int availA = challenger.getOnlinePlayers().size();
        int availB = target.getOnlinePlayers().size();
        int defaultSize = Math.max(2, Math.min(5, Math.min(availA, availB)));
        return sendChallenge(challenger, target, arenaName, sender, defaultSize);
    }

    public boolean sendChallenge(Clan challenger, Clan target, String arenaName, Player sender, int teamSize) {
        String prefix = plugin.getMessages().getString("prefix", "");

        if (teamSize < 2 || teamSize > 5) {
            sender.sendMessage(ColorUtil.color(prefix + "&cO tamanho do duelo deve ser entre 2v2 e 5v5 (digite 2, 3, 4 ou 5)!"));
            return false;
        }

        if (isDueling(challenger.getId()) || isDueling(target.getId())) {
            sender.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("duel.already-dueling", "&cUm dos clãs já está participando de um duelo ativo.")));
            return false;
        }

        List<Player> challengerOnline = challenger.getOnlinePlayers();
        if (challengerOnline.size() < teamSize) {
            sender.sendMessage(ColorUtil.color(prefix + "&cSeu clã precisa de pelo menos &e" + teamSize + " &cmembros online para um duelo &e" + teamSize + "v" + teamSize + "&c! (Atualmente: &f" + challengerOnline.size() + "&c)"));
            return false;
        }

        List<Player> targetOnline = target.getOnlinePlayers();
        if (targetOnline.size() < teamSize) {
            sender.sendMessage(ColorUtil.color(prefix + "&cO clã &e" + target.getName() + " &cprecisa de pelo menos &e" + teamSize + " &cmembros online para um duelo &e" + teamSize + "v" + teamSize + "&c! (Atualmente: &f" + targetOnline.size() + "&c)"));
            return false;
        }

        int timeout = plugin.getConfig().getInt("duels.settings.challenge-timeout-seconds", 60);
        DuelChallenge challenge = new DuelChallenge(challenger.getId(), target.getId(), arenaName, sender.getUniqueId(), timeout, teamSize);

        pendingChallenges.computeIfAbsent(target.getId(), k -> new ConcurrentHashMap<>()).put(challenger.getId(), challenge);

        String msgChallenger = plugin.getMessages().getString("duel.challenged", "&aO seu clã desafiou o clã &e{target} &apara um duelo &6{size}v{size} &ana arena &e{arena}&a!")
                .replace("{target}", target.getName())
                .replace("{arena}", arenaName)
                .replace("{size}", String.valueOf(teamSize));
        sender.sendMessage(ColorUtil.color(prefix + msgChallenger));

        String msgTarget = plugin.getMessages().getString("duel.received", "&eSeu clã recebeu um desafio de DUELO &6{size}v{size} &edo clã &6{challenger} &8[{color}{tag}&8]&e!")
                .replace("{challenger}", challenger.getName())
                .replace("{tag}", challenger.getTag())
                .replace("{color}", challenger.getTagColor())
                .replace("{size}", String.valueOf(teamSize));

        TextComponent actionMsg = new TextComponent(ColorUtil.color(prefix + "&7Clique para responder: "));

        TextComponent acceptBtn = new TextComponent(ColorUtil.color("&a&l[ACEITAR DUELO " + teamSize + "v" + teamSize + "]"));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan duelo aceitar " + challenger.getTag()));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.color("&aClique aqui para ACEITAR o duelo " + teamSize + "v" + teamSize + " contra &e" + challenger.getName() + " &8[" + challenger.getTagColor() + challenger.getTag() + "&8]&a!")).create()));

        TextComponent space = new TextComponent(" ");

        TextComponent declineBtn = new TextComponent(ColorUtil.color("&c&l[RECUSAR]"));
        declineBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan recusar " + challenger.getTag()));
        declineBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.color("&cClique aqui para RECUSAR o duelo.")).create()));

        actionMsg.addExtra(acceptBtn);
        actionMsg.addExtra(space);
        actionMsg.addExtra(declineBtn);

        for (Player p : target.getOnlinePlayers()) {
            p.sendMessage(ColorUtil.color(prefix + msgTarget));
            p.spigot().sendMessage(actionMsg);
            try {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
            } catch (Exception ignored) {}
        }

        return true;
    }

    public boolean acceptChallenge(Clan target, Clan challenger) {
        return acceptChallenge(target, challenger, null);
    }

    public boolean acceptChallenge(Clan target, Clan challenger, Player acceptor) {
        String prefix = plugin.getMessages().getString("prefix", "");
        Map<UUID, DuelChallenge> map = pendingChallenges.get(target.getId());
        if (map == null) return false;
        DuelChallenge challenge = map.remove(challenger.getId());
        if (challenge == null || challenge.isExpired()) {
            if (acceptor != null) acceptor.sendMessage(ColorUtil.color(prefix + "&cEste desafio de duelo expirou ou não existe mais!"));
            return false;
        }

        int teamSize = challenge.getTeamSize();

        Arena arena = plugin.getArenaManager().getArena(challenge.getArenaName());
        if (arena == null || !arena.isConfigured() || arena.isInUse()) {
            arena = plugin.getArenaManager().getAvailableArena();
            if (arena == null) {
                target.broadcast(prefix + plugin.getMessages().getString("duel.no-arenas", ""));
                challenger.broadcast(prefix + plugin.getMessages().getString("duel.no-arenas", ""));
                return false;
            }
        }

        List<Player> allChallenger = challenger.getOnlinePlayers();
        List<Player> allTarget = target.getOnlinePlayers();

        if (allChallenger.size() < teamSize) {
            String msg = prefix + "&cO clã desafiante não possui mais " + teamSize + " membros online para o duelo " + teamSize + "v" + teamSize + "!";
            target.broadcast(msg);
            challenger.broadcast(msg);
            return false;
        }

        if (allTarget.size() < teamSize) {
            String msg = prefix + "&cSeu clã não possui mais " + teamSize + " membros online para o duelo " + teamSize + "v" + teamSize + "!";
            target.broadcast(msg);
            challenger.broadcast(msg);
            return false;
        }

        // Selecionar exatamente teamSize jogadores de cada clã
        List<Player> selectedA = new ArrayList<>();
        Player senderA = Bukkit.getPlayer(challenge.getSenderUuid());
        if (senderA != null && senderA.isOnline() && challenger.isMember(senderA.getUniqueId())) {
            selectedA.add(senderA);
        }
        for (Player p : allChallenger) {
            if (selectedA.size() >= teamSize) break;
            if (!selectedA.contains(p)) {
                selectedA.add(p);
            }
        }

        List<Player> selectedB = new ArrayList<>();
        if (acceptor != null && acceptor.isOnline() && target.isMember(acceptor.getUniqueId())) {
            selectedB.add(acceptor);
        }
        for (Player p : allTarget) {
            if (selectedB.size() >= teamSize) break;
            if (!selectedB.contains(p)) {
                selectedB.add(p);
            }
        }

        // Garantir que os times estejam 100% IGUAIS
        if (selectedA.size() != teamSize || selectedB.size() != teamSize || selectedA.size() != selectedB.size()) {
            String msg = prefix + "&cOs times não estão com a mesma quantidade de jogadores (" + selectedA.size() + " vs " + selectedB.size() + "). O duelo só inicia com times iguais (" + teamSize + "v" + teamSize + ")!";
            target.broadcast(msg);
            challenger.broadcast(msg);
            return false;
        }

        Set<UUID> teamA = new HashSet<>();
        Set<UUID> teamB = new HashSet<>();
        for (Player p : selectedA) teamA.add(p.getUniqueId());
        for (Player p : selectedB) teamB.add(p.getUniqueId());

        int cd = plugin.getConfig().getInt("duels.settings.countdown-seconds", 5);
        int maxDur = plugin.getConfig().getInt("duels.settings.max-match-duration-seconds", 300);

        ActiveDuel duel = new ActiveDuel(arena, challenger.getId(), target.getId(), teamA, teamB, cd, maxDur);
        activeDuels.put(challenger.getId(), duel);
        activeDuels.put(target.getId(), duel);

        for (UUID u : teamA) playerDuelMap.put(u, duel);
        for (UUID u : teamB) playerDuelMap.put(u, duel);

        // Teleportar jogadores: Todos do Time A para spawn1, Todos do Time B para spawn2
        for (Player p : selectedA) {
            p.teleport(arena.getSpawn1());
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
        }
        for (Player p : selectedB) {
            p.teleport(arena.getSpawn2());
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
        }

        String msgAccepted = prefix + plugin.getMessages().getString("duel.accepted", "&aO duelo contra &e{opponent} &afoi aceito! Preparando arena...")
                .replace("{opponent}", target.getName())
                .replace("{size}", String.valueOf(teamSize));
        challenger.broadcast(msgAccepted);
        target.broadcast(msgAccepted);

        broadcastToDuel(duel, "&e&l[DUELO " + teamSize + "v" + teamSize + "] &aDuelo iniciado! Preparem-se!", Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        return true;
    }

    public void handlePlayerDeath(Player victim, Player killer) {
        ActiveDuel duel = playerDuelMap.get(victim.getUniqueId());
        if (duel == null) return;

        boolean finished = duel.handleElimination(victim.getUniqueId());
        String msg = plugin.getMessages().getString("prefix", "") +
                "&#FF5555" + victim.getName() + " &7foi abatido no duelo! &8(&a" + duel.getAliveA().size() + " &7vs &c" + duel.getAliveB().size() + " &7restantes&8)";
        broadcastToDuel(duel, msg, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);

        // Teleportar espectador ou saída
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (duel.getArena().getExitLocation() != null) {
                victim.teleport(duel.getArena().getExitLocation());
            }
        }, 5L);

        if (finished) {
            finishDuel(duel, duel.getWinnerClanId());
        }
    }

    public void handlePlayerQuit(Player player) {
        ActiveDuel duel = playerDuelMap.get(player.getUniqueId());
        if (duel == null) return;

        boolean finished = duel.handleElimination(player.getUniqueId());
        String msg = plugin.getMessages().getString("prefix", "") +
                plugin.getMessages().getString("duel.player-quit", "").replace("{player}", player.getName());
        broadcastToDuel(duel, msg, Sound.ENTITY_VILLAGER_HURT, 1.0f, 1.0f);

        if (finished) {
            finishDuel(duel, duel.getWinnerClanId());
        }
    }

    public void finishDuel(ActiveDuel duel, UUID winnerClanId) {
        activeDuels.remove(duel.getClanAId());
        activeDuels.remove(duel.getClanBId());

        Clan clanA = plugin.getClanManager().getClanById(duel.getClanAId());
        Clan clanB = plugin.getClanManager().getClanById(duel.getClanBId());

        String prefix = plugin.getMessages().getString("prefix", "");

        if (winnerClanId != null) {
            Clan winner = winnerClanId.equals(duel.getClanAId()) ? clanA : clanB;
            Clan loser = winnerClanId.equals(duel.getClanAId()) ? clanB : clanA;

            if (winner != null && loser != null) {
                int winPts = plugin.getConfig().getInt("stats.points-per-duel-win", 25);
                int lossPts = plugin.getConfig().getInt("stats.points-lost-on-duel-loss", 10);

                winner.getStats().addDuelWin();
                plugin.getClanManager().addPoints(winner, winPts);

                loser.getStats().addDuelLoss();
                plugin.getClanManager().removePoints(loser, lossPts);

                String winBroadcast = prefix + plugin.getMessages().getString("duel.won", "&aO clã &e{winner} &8[{color}{tag}&8] &avenceu o duelo &6{size}v{size} &acontra &c{loser}&a!")
                        .replace("{winner}", winner.getName())
                        .replace("{tag}", winner.getTag())
                        .replace("{color}", winner.getTagColor())
                        .replace("{loser}", loser.getName())
                        .replace("{size}", String.valueOf(duel.getTeamSize()));
                Bukkit.broadcastMessage(ColorUtil.color(winBroadcast));
            }
        } else {
            String drawMsg = prefix + plugin.getMessages().getString("duel.cancelled", "Duelo empatado/cancelado!");
            if (clanA != null) clanA.broadcast(drawMsg);
            if (clanB != null) clanB.broadcast(drawMsg);
        }

        // Teleportar sobreviventes para o ponto de saída
        for (UUID u : duel.getTeamA()) {
            playerDuelMap.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && duel.getArena().getExitLocation() != null) {
                p.teleport(duel.getArena().getExitLocation());
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
            }
        }
        for (UUID u : duel.getTeamB()) {
            playerDuelMap.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && duel.getArena().getExitLocation() != null) {
                p.teleport(duel.getArena().getExitLocation());
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
            }
        }

        duel.cleanup();
    }

    private void broadcastToDuel(ActiveDuel duel, String message, Sound sound, float volume, float pitch) {
        String colored = ColorUtil.color(message);
        Set<UUID> all = new HashSet<>(duel.getTeamA());
        all.addAll(duel.getTeamB());

        for (UUID u : all) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                p.sendMessage(colored);
                if (sound != null) {
                    try {
                        p.playSound(p.getLocation(), sound, volume, pitch);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public void cleanup() {
        if (duelLoopTask != null) {
            duelLoopTask.cancel();
        }
        for (ActiveDuel duel : new ArrayList<>(activeDuels.values())) {
            finishDuel(duel, null);
        }
    }
}

