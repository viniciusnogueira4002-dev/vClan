package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.database.ClanRepository;
import org.vine.vclans.model.*;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.menu.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClanManager {

    private final VClans plugin;
    private final ClanRepository repository;

    private final Map<UUID, Clan> clansById = new ConcurrentHashMap<>();
    private final Map<String, UUID> clanIdByName = new ConcurrentHashMap<>();
    private final Map<String, UUID> clanIdByTag = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerClanMap = new ConcurrentHashMap<>();
    private final Map<UUID, KickNotice> kickNotices = new ConcurrentHashMap<>();

    // Cooldown para entrar em outro clã após sair
    private final Map<UUID, Instant> leaveCooldowns = new ConcurrentHashMap<>();

    // Caches de Top Ranking para economizar CPU e evitar alocações de streams e GC
    private volatile List<Clan> cachedTopPoints = null;
    private volatile long lastTopPointsCalc = 0;
    private volatile List<Clan> cachedTopDuels = null;
    private volatile long lastTopDuelsCalc = 0;
    private volatile List<Clan> cachedTopWars = null;
    private volatile long lastTopWarsCalc = 0;
    private volatile List<Clan> cachedTopKdr = null;
    private volatile long lastTopKdrCalc = 0;
    private static final long TOP_CACHE_MS = 5000L; // 5 segundos

    public ClanManager(VClans plugin, ClanRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        loadAll();
        startCleanupTask();
    }

    private void startCleanupTask() {
        // Limpar cooldowns de saída expirados a cada 5 minutos
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Instant now = Instant.now();
            leaveCooldowns.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
        }, 6000L, 6000L);
    }

    public void invalidateTopCache() {
        cachedTopPoints = null;
        cachedTopDuels = null;
        cachedTopWars = null;
        cachedTopKdr = null;
    }

    public void loadAll() {
        clansById.clear();
        clanIdByName.clear();
        clanIdByTag.clear();
        playerClanMap.clear();
        kickNotices.clear();

        Map<UUID, Clan> loaded = repository.loadAllClans();
        for (Clan clan : loaded.values()) {
            clansById.put(clan.getId(), clan);
            clanIdByName.put(clan.getName().toLowerCase(), clan.getId());
            clanIdByTag.put(clan.getTag().toLowerCase(), clan.getId());

            for (UUID memberUuid : clan.getMembers().keySet()) {
                playerClanMap.put(memberUuid, clan.getId());
            }
        }
        kickNotices.putAll(repository.loadAllKickNotices());
        plugin.getLogger().info("Carregados " + clansById.size() + " clãs e " + kickNotices.size() + " avisos de expulsão.");
    }

    public Collection<Clan> getAllClans() {
        return clansById.values();
    }

    public Clan getClanById(UUID id) {
        return id != null ? clansById.get(id) : null;
    }

    public Clan getClanByPlayer(UUID playerUuid) {
        if (playerUuid == null) return null;
        UUID clanId = playerClanMap.get(playerUuid);
        return clanId != null ? clansById.get(clanId) : null;
    }

    public Clan getClanByPlayer(Player player) {
        return player != null ? getClanByPlayer(player.getUniqueId()) : null;
    }

    public Clan getClanByName(String name) {
        if (name == null) return null;
        UUID id = clanIdByName.get(name.toLowerCase());
        return id != null ? clansById.get(id) : null;
    }

    public Clan getClanByTag(String tag) {
        if (tag == null) return null;
        UUID id = clanIdByTag.get(tag.toLowerCase());
        return id != null ? clansById.get(id) : null;
    }

    public boolean isNameTaken(String name) {
        return name != null && clanIdByName.containsKey(name.toLowerCase());
    }

    public boolean isTagTaken(String tag) {
        return tag != null && clanIdByTag.containsKey(tag.toLowerCase());
    }

    public Clan createClan(String name, String tag, Player leader) {
        UUID clanId = UUID.randomUUID();
        String defaultColor = plugin.getConfig().getString("clan.tag.default-color", "&#FF5555");
        String defaultDesc = plugin.getConfig().getString("clan.description.default", "Clã da Rede Eclypse");

        Clan clan = new Clan(clanId, name, tag, defaultColor, defaultDesc, leader.getUniqueId(), Instant.now());
        ClanMember leaderMember = new ClanMember(leader.getUniqueId(), leader.getName(), ClanRole.LEADER);
        clan.addMember(leaderMember);

        clansById.put(clanId, clan);
        clanIdByName.put(name.toLowerCase(), clanId);
        clanIdByTag.put(tag.toLowerCase(), clanId);
        playerClanMap.put(leader.getUniqueId(), clanId);

        clan.addLog(leader.getUniqueId(), "Clã fundado por " + leader.getName());

        // Salvar assincronamente
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            repository.saveClan(clan);
            repository.saveMember(clanId, leaderMember);
        });

        plugin.getScoreboardManager().updatePlayer(leader);
        invalidateTopCache();
        return clan;
    }

    public void disbandClan(Clan clan) {
        UUID clanId = clan.getId();
        clansById.remove(clanId);
        clanIdByName.remove(clan.getName().toLowerCase());
        clanIdByTag.remove(clan.getTag().toLowerCase());
        invalidateTopCache();

        for (UUID memberUuid : clan.getMembers().keySet()) {
            playerClanMap.remove(memberUuid);
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null) {
                plugin.getScoreboardManager().updatePlayer(p);
            }
        }

        // Remover de alianças
        for (Clan other : clansById.values()) {
            if (other.getAllies().remove(clanId)) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveAllies(other.getId(), other.getAllies()));
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.deleteClan(clanId));
    }

    public KickNotice getKickNotice(UUID playerUuid) {
        return playerUuid != null ? kickNotices.get(playerUuid) : null;
    }

    public void dismissKickNotice(UUID playerUuid) {
        if (playerUuid != null) {
            kickNotices.remove(playerUuid);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.deleteKickNotice(playerUuid));
        }
    }

    public void addMember(Clan clan, Player player, ClanRole role) {
        dismissKickNotice(player.getUniqueId());

        ClanMember member = new ClanMember(player.getUniqueId(), player.getName(), role);
        clan.addMember(member);
        playerClanMap.put(player.getUniqueId(), clan.getId());
        clan.addLog(player.getUniqueId(), player.getName() + " entrou no clã como " + role.name());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            repository.saveMember(clan.getId(), member);
            repository.saveClan(clan);
        });

        plugin.getScoreboardManager().updatePlayer(player);

        // Anúncio global de recrutamento para todo o servidor
        String prefix = plugin.getMessages().getString("prefix", "");
        String broadcast = plugin.getMessages().getString("clan.member-joined-broadcast",
                "&#FFAA00O guerreiro &e{player} &#FFAA00foi recrutado pelo clã &#FF5555{name} &8[{color}{tag}&8]&#FFAA00!")
                .replace("{player}", player.getName())
                .replace("{name}", clan.getName())
                .replace("{tag}", clan.getTag())
                .replace("{color}", clan.getTagColor());
        Bukkit.broadcastMessage(ColorUtil.color(prefix + broadcast));
    }

    public void removeMember(Clan clan, UUID playerUuid, String actorName, boolean wasKicked) {
        ClanMember member = clan.getMember(playerUuid);
        if (member == null) return;

        clan.removeMember(playerUuid);
        playerClanMap.remove(playerUuid);

        int cooldownSeconds = plugin.getConfig().getInt("clan.leave-cooldown-seconds", 3600);
        if (cooldownSeconds > 0) {
            leaveCooldowns.put(playerUuid, Instant.now().plusSeconds(cooldownSeconds));
        }

        String action = wasKicked ? member.getLastKnownName() + " foi expulso por " + actorName : member.getLastKnownName() + " saiu do clã";
        clan.addLog(playerUuid, action);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            repository.removeMember(playerUuid);
            repository.saveClan(clan);
        });

        String prefix = plugin.getMessages().getString("prefix", "");

        if (wasKicked) {
            // Registrar aviso persistente para o menu do jogador
            KickNotice notice = new KickNotice(clan.getName(), clan.getTag(), clan.getTagColor(), actorName, Instant.now());
            kickNotices.put(playerUuid, notice);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveKickNotice(playerUuid, notice));

            // Anúncio global de expulsão para todo o servidor
            String broadcast = plugin.getMessages().getString("clan.member-kicked-broadcast",
                    "&#FF5555O jogador &e{player} &#FF5555foi expulso do clã &#FFAA00{name} &8[{color}{tag}&8] &#FF5555por &e{actor}&c!")
                    .replace("{player}", member.getLastKnownName())
                    .replace("{name}", clan.getName())
                    .replace("{tag}", clan.getTag())
                    .replace("{color}", clan.getTagColor())
                    .replace("{actor}", actorName);
            Bukkit.broadcastMessage(ColorUtil.color(prefix + broadcast));
        }

        Player p = Bukkit.getPlayer(playerUuid);
        if (p != null) {
            plugin.getScoreboardManager().updatePlayer(p);

            if (wasKicked && p.isOnline()) {
                String notify = plugin.getMessages().getString("clan.kicked-notify",
                        "&#FF5555Você foi expulso do clã &e{name} &8[{color}{tag}&8] &#FF5555por &e{actor}&c!")
                        .replace("{name}", clan.getName())
                        .replace("{tag}", clan.getTag())
                        .replace("{color}", clan.getTagColor())
                        .replace("{actor}", actorName);
                p.sendMessage(ColorUtil.color(prefix + notify));
                try {
                    p.sendTitle(ColorUtil.color("&#FF5555&lEXPULSO!"),
                            ColorUtil.color("&7Você foi expulso do clã " + clan.getTagColor() + clan.getTag()), 10, 60, 20);
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                } catch (Exception ignored) {}

                if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                    p.closeInventory();
                }
            }
        }
    }

    public void setRole(Clan clan, UUID playerUuid, ClanRole newRole, UUID actorUuid) {
        ClanMember member = clan.getMember(playerUuid);
        if (member == null) return;

        member.setRole(newRole);
        clan.addLog(actorUuid, member.getLastKnownName() + " teve seu cargo alterado para " + newRole.name());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveMember(clan.getId(), member));

        Player p = Bukkit.getPlayer(playerUuid);
        if (p != null) {
            plugin.getScoreboardManager().updatePlayer(p);
        }
    }

    public void transferLeadership(Clan clan, UUID newLeaderUuid, UUID oldLeaderUuid) {
        ClanMember oldLeader = clan.getMember(oldLeaderUuid);
        ClanMember newLeader = clan.getMember(newLeaderUuid);
        if (oldLeader == null || newLeader == null) return;

        oldLeader.setRole(ClanRole.CO_LEADER);
        newLeader.setRole(ClanRole.LEADER);
        clan.setLeaderUuid(newLeaderUuid);

        clan.addLog(oldLeaderUuid, "Liderança transferida para " + newLeader.getLastKnownName());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            repository.saveMember(clan.getId(), oldLeader);
            repository.saveMember(clan.getId(), newLeader);
            repository.saveClan(clan);
        });

        Player pOld = Bukkit.getPlayer(oldLeaderUuid);
        Player pNew = Bukkit.getPlayer(newLeaderUuid);
        if (pOld != null) plugin.getScoreboardManager().updatePlayer(pOld);
        if (pNew != null) plugin.getScoreboardManager().updatePlayer(pNew);
    }

    public void setTag(Clan clan, String newTag) {
        clanIdByTag.remove(clan.getTag().toLowerCase());
        clan.setTag(newTag);
        clanIdByTag.put(newTag.toLowerCase(), clan.getId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveClan(clan));

        for (Player p : clan.getOnlinePlayers()) {
            plugin.getScoreboardManager().updatePlayer(p);
        }
    }

    public void setTagColor(Clan clan, String newColor) {
        clan.setTagColor(newColor);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveClan(clan));

        for (Player p : clan.getOnlinePlayers()) {
            plugin.getScoreboardManager().updatePlayer(p);
        }
    }

    public void setDescription(Clan clan, String newDesc) {
        clan.setDescription(newDesc);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveClan(clan));
    }

    public void addPoints(Clan clan, int amount) {
        clan.getStats().addPoints(amount);
        invalidateTopCache();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveStats(clan));
    }

    public void removePoints(Clan clan, int amount) {
        clan.getStats().removePoints(amount);
        invalidateTopCache();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.saveStats(clan));
    }

    public boolean hasLeaveCooldown(UUID playerUuid) {
        Instant exp = leaveCooldowns.get(playerUuid);
        if (exp == null) return false;
        if (Instant.now().isAfter(exp)) {
            leaveCooldowns.remove(playerUuid);
            return false;
        }
        return true;
    }

    public long getRemainingLeaveCooldown(UUID playerUuid) {
        Instant exp = leaveCooldowns.get(playerUuid);
        if (exp == null) return 0;
        return Math.max(0, exp.getEpochSecond() - Instant.now().getEpochSecond());
    }

    public List<Clan> getTopClansByPoints(int limit) {
        long now = System.currentTimeMillis();
        List<Clan> cached = cachedTopPoints;
        if (cached == null || now - lastTopPointsCalc > TOP_CACHE_MS) {
            cached = clansById.values().stream()
                    .sorted(Comparator.comparingInt((Clan c) -> c.getStats().getPoints()).reversed()
                            .thenComparing(Clan::getName))
                    .collect(Collectors.toList());
            cachedTopPoints = cached;
            lastTopPointsCalc = now;
        }
        return cached.size() <= limit ? cached : cached.subList(0, limit);
    }

    public List<Clan> getTopClansByDuels(int limit) {
        long now = System.currentTimeMillis();
        List<Clan> cached = cachedTopDuels;
        if (cached == null || now - lastTopDuelsCalc > TOP_CACHE_MS) {
            cached = clansById.values().stream()
                    .sorted(Comparator.comparingInt((Clan c) -> c.getStats().getDuelWins()).reversed()
                            .thenComparing(Clan::getName))
                    .collect(Collectors.toList());
            cachedTopDuels = cached;
            lastTopDuelsCalc = now;
        }
        return cached.size() <= limit ? cached : cached.subList(0, limit);
    }

    public List<Clan> getTopClansByWars(int limit) {
        long now = System.currentTimeMillis();
        List<Clan> cached = cachedTopWars;
        if (cached == null || now - lastTopWarsCalc > TOP_CACHE_MS) {
            cached = clansById.values().stream()
                    .sorted(Comparator.comparingInt((Clan c) -> c.getStats().getWarWins()).reversed()
                            .thenComparing(Clan::getName))
                    .collect(Collectors.toList());
            cachedTopWars = cached;
            lastTopWarsCalc = now;
        }
        return cached.size() <= limit ? cached : cached.subList(0, limit);
    }

    public List<Clan> getTopClansByKDR(int limit) {
        long now = System.currentTimeMillis();
        List<Clan> cached = cachedTopKdr;
        if (cached == null || now - lastTopKdrCalc > TOP_CACHE_MS) {
            cached = clansById.values().stream()
                    .sorted(Comparator.comparingDouble((Clan c) -> c.getStats().getKdr()).reversed()
                            .thenComparing(Clan::getName))
                    .collect(Collectors.toList());
            cachedTopKdr = cached;
            lastTopKdrCalc = now;
        }
        return cached.size() <= limit ? cached : cached.subList(0, limit);
    }

    public void flushAll() {
        for (Clan clan : clansById.values()) {
            repository.saveClan(clan);
            for (ClanMember member : clan.getMembers().values()) {
                repository.saveMember(clan.getId(), member);
            }
            repository.saveAllies(clan.getId(), clan.getAllies());
        }
    }

    public void sendClanChat(Player sender, String rawMessage) {
        if (sender == null) return;

        Clan clan = getClanByPlayer(sender);
        if (clan == null) {
            String prefix = plugin.getMessages().getString("prefix", "");
            sender.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "&#FF5555Você não pertence a nenhum clã.")));
            return;
        }

        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            sender.sendMessage(ColorUtil.color(plugin.getMessages().getString("clan.chat-usage", "&#FF5555Uso: &e/. <mensagem>")));
            return;
        }

        ClanMember member = clan.getMember(sender.getUniqueId());
        ClanRole role = member != null ? member.getRole() : ClanRole.MEMBER;

        // Cor padrão do chat do clã: &b
        String defaultMsgColor = plugin.getConfig().getString("clan-chat.default-message-color", "&b");
        boolean allowColors = plugin.getConfig().getBoolean("clan-chat.allow-colors", true);

        String messageFormatted;
        if (allowColors && (sender.hasPermission("vclans.chat.color") || sender.isOp())) {
            messageFormatted = ColorUtil.color(defaultMsgColor + rawMessage);
        } else {
            messageFormatted = ColorUtil.color(defaultMsgColor + ColorUtil.strip(rawMessage));
        }

        String format = plugin.getConfig().getString("clan-chat.format", "&8[&bClã&8] &r{role_prefix}&f{player}&8: {message}");
        String finalOutput = ColorUtil.color(format
                .replace("{clan}", clan.getName())
                .replace("{tag}", clan.getTag())
                .replace("{color}", clan.getTagColor())
                .replace("{role}", role.getDefaultDisplayName())
                .replace("{role_prefix}", role.getDefaultPrefix())
                .replace("{player}", sender.getName())
                .replace("{message}", messageFormatted));

        List<Player> onlineMembers = clan.getOnlinePlayers();
        long otherOnlineCount = onlineMembers.stream()
                .filter(p -> !p.getUniqueId().equals(sender.getUniqueId()))
                .count();

        // Enviar para todos os membros online do clã
        for (Player p : onlineMembers) {
            p.sendMessage(finalOutput);
        }

        // Se ninguém do clã estiver online no momento, avisa no chat
        if (otherOnlineCount == 0) {
            sender.sendMessage(ColorUtil.color(plugin.getMessages().getString(
                    "clan.chat-no-members-online",
                    "&#FF5555[Clã] Ninguém do clã online no momento.")));
        }

        plugin.getLogger().info("[Clan Chat - " + clan.getTag() + "] " + sender.getName() + ": " + rawMessage);
    }
}

