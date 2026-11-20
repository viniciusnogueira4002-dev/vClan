package org.vine.vclans.model;

import org.vine.vclans.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Clan {

    private final UUID id;
    private String name;
    private String tag;
    private String tagColor;
    private String description;
    private UUID leaderUuid;
    private final Map<UUID, ClanMember> members;
    private final ClanStats stats;
    private final ClanSettings settings;
    private final ClanBank bank;
    private final ClanUpgrades upgrades;
    private volatile int seasonWarPoints;
    private final Set<UUID> allies;
    private final Set<UUID> rivals;
    private final List<ClanLogEntry> auditLogs;
    private final Instant createdAt;

    public Clan(UUID id, String name, String tag, String tagColor, String description, UUID leaderUuid, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.tagColor = tagColor != null ? tagColor : "&#FF5555";
        this.description = description != null ? description : "Clã da Rede Eclypse";
        this.leaderUuid = leaderUuid;
        this.members = new ConcurrentHashMap<>();
        this.stats = new ClanStats();
        this.settings = new ClanSettings();
        this.bank = new ClanBank();
        this.upgrades = new ClanUpgrades();
        this.seasonWarPoints = 0;
        this.allies = ConcurrentHashMap.newKeySet();
        this.rivals = ConcurrentHashMap.newKeySet();
        this.auditLogs = new CopyOnWriteArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTagColor() {
        return tagColor;
    }

    public void setTagColor(String tagColor) {
        this.tagColor = tagColor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public Map<UUID, ClanMember> getMembers() {
        return members;
    }

    public ClanStats getStats() {
        return stats;
    }

    public ClanSettings getSettings() {
        return settings;
    }

    public ClanBank getBank() {
        return bank;
    }

    public ClanUpgrades getUpgrades() {
        return upgrades;
    }

    public int getSeasonWarPoints() {
        return seasonWarPoints;
    }

    public synchronized void setSeasonWarPoints(int seasonWarPoints) {
        this.seasonWarPoints = Math.max(0, seasonWarPoints);
    }

    public synchronized void addSeasonWarPoints(int delta) {
        if (delta <= 0) return;
        try {
            this.seasonWarPoints = Math.addExact(this.seasonWarPoints, delta);
        } catch (ArithmeticException overflow) {
            this.seasonWarPoints = Integer.MAX_VALUE;
        }
    }

    public Set<UUID> getAllies() {
        return allies;
    }

    public Set<UUID> getRivals() {
        return rivals;
    }

    public List<ClanLogEntry> getAuditLogs() {
        return auditLogs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public ClanMember getMember(UUID uuid) {
        return members.get(uuid);
    }

    public ClanRole getRole(UUID uuid) {
        ClanMember member = members.get(uuid);
        return member != null ? member.getRole() : null;
    }

    public boolean hasPermission(UUID playerUuid, ClanPermission permission) {
        if (isLeader(playerUuid)) return true;
        ClanRole role = getRole(playerUuid);
        if (role == null) return false;
        return role.getDefaultPermissions().contains(permission);
    }

    public void addMember(ClanMember member) {
        members.put(member.getUuid(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public int getMembersCount() {
        return members.size();
    }

    public List<Player> getOnlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : members.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                list.add(p);
            }
        }
        return list;
    }

    public String getColoredTag() {
        return ColorUtil.color(tagColor + tag);
    }

    public String getFormattedTag() {
        return ColorUtil.color("&8[" + tagColor + tag + "&8]");
    }

    public void addLog(UUID actorUuid, String action) {
        ClanLogEntry entry = new ClanLogEntry(actorUuid, action);
        auditLogs.add(0, entry);
        if (auditLogs.size() > 50) {
            auditLogs.remove(auditLogs.size() - 1);
        }
    }

    public boolean isAlly(UUID otherClanId) {
        return allies.contains(otherClanId);
    }

    public boolean isRival(UUID otherClanId) {
        return rivals.contains(otherClanId);
    }

    public void broadcast(String message) {
        for (Player p : getOnlinePlayers()) {
            p.sendMessage(ColorUtil.color(message));
        }
    }
}

