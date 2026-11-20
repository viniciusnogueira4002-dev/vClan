package org.vine.vclans.manager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gerenciador da Guerra de Clãs por Temporada (placar exclusivo de disputa).
 * Fontes: PvE (NPCs/Mobs/Bosses), Farm (blocos da whitelist) e PvP (anti-freekill).
 */
public class SeasonWarManager {

    private final VClans plugin;
    private final File warsFile;
    private FileConfiguration warsConfig;

    private boolean enabled = true;
    private volatile boolean manualDoublePoints = false;
    private volatile boolean lastScheduledDoubleState = false;
    private String lastAutoCloseDate = "";

    private final Map<Material, Integer> blockPoints = new ConcurrentHashMap<>();
    private final Map<String, Integer> mobPointsByName = new ConcurrentHashMap<>();
    private final Map<String, Integer> mobPointsByType = new ConcurrentHashMap<>();
    private int defaultMonsterPoints = 0;
    private int pvpKillPoints = 25;
    private final List<TimeWindow> peakWindows = new ArrayList<>();

    private volatile List<Clan> cachedTopWar = null;
    private volatile long lastTopWarCalc = 0L;
    private static final long TOP_CACHE_MS = 3000L;

    private BukkitTask schedulerTask;

    private record TimeWindow(LocalTime start, LocalTime end) {
        boolean contains(LocalTime now) {
            if (start.equals(end)) return true;
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            // Janela que cruza meia-noite (ex: 23:00-01:00)
            return !now.isBefore(start) || now.isBefore(end);
        }
    }

    public SeasonWarManager(VClans plugin) {
        this.plugin = plugin;
        this.warsFile = new File(plugin.getDataFolder(), "wars.yml");
        loadConfig();
        startSchedulerTask();
    }

    public void loadConfig() {
        if (!warsFile.exists()) {
            plugin.saveResource("wars.yml", false);
        }
        this.warsConfig = YamlConfiguration.loadConfiguration(warsFile);
        this.enabled = warsConfig.getBoolean("season-war.enabled", true);
        this.pvpKillPoints = Math.max(0, warsConfig.getInt("season-war.points.pvp-kill", 25));
        this.defaultMonsterPoints = Math.max(0, warsConfig.getInt("season-war.points.default-monster", 1));
        this.lastAutoCloseDate = warsConfig.getString("season-war.last-auto-close-date", "");

        blockPoints.clear();
        ConfigurationSection blocksSec = warsConfig.getConfigurationSection("season-war.points.blocks");
        if (blocksSec != null) {
            for (String matName : blocksSec.getKeys(false)) {
                Material mat = Material.matchMaterial(matName.trim().toUpperCase(Locale.ROOT));
                int pts = blocksSec.getInt(matName, 0);
                if (mat != null && pts > 0) {
                    blockPoints.put(mat, pts);
                }
            }
        }

        mobPointsByName.clear();
        ConfigurationSection nameSec = warsConfig.getConfigurationSection("season-war.points.mobs-by-name");
        if (nameSec != null) {
            for (String key : nameSec.getKeys(false)) {
                int pts = nameSec.getInt(key, 0);
                if (pts > 0) {
                    mobPointsByName.put(ColorUtil.strip(key).trim().toLowerCase(Locale.ROOT), pts);
                }
            }
        }

        mobPointsByType.clear();
        ConfigurationSection typeSec = warsConfig.getConfigurationSection("season-war.points.mobs-by-type");
        if (typeSec != null) {
            for (String key : typeSec.getKeys(false)) {
                int pts = typeSec.getInt(key, 0);
                if (pts > 0) {
                    mobPointsByType.put(key.trim().toUpperCase(Locale.ROOT), pts);
                }
            }
        }

        peakWindows.clear();
        for (String raw : warsConfig.getStringList("season-war.double-points-windows")) {
            TimeWindow tw = parseWindow(raw);
            if (tw != null) {
                peakWindows.add(tw);
            }
        }
    }

    private TimeWindow parseWindow(String raw) {
        if (raw == null || !raw.contains("-")) return null;
        String[] parts = raw.trim().split("-");
        if (parts.length != 2) return null;
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            return new TimeWindow(start, end);
        } catch (Exception e) {
            plugin.getLogger().warning("[vClans] Horário de janela 2x inválido em wars.yml: " + raw);
            return null;
        }
    }

    private void startSchedulerTask() {
        this.schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled) return;

            boolean scheduledNow = isInsideScheduledWindow();
            if (scheduledNow != lastScheduledDoubleState) {
                lastScheduledDoubleState = scheduledNow;
                String prefix = plugin.getMessages().getString("prefix", "");
                if (scheduledNow && !manualDoublePoints) {
                    Bukkit.broadcastMessage(ColorUtil.color(prefix + "&#FFAA00&l[GUERRA 2X] &#55FF55O Horário de Pico começou! Pontos de Guerra em DOBRO!"));
                } else if (!scheduledNow && !manualDoublePoints) {
                    Bukkit.broadcastMessage(ColorUtil.color(prefix + "&#FFAA00&l[GUERRA 2X] &7O Horário de Pico terminou."));
                }
            }

            checkAutoSeasonClose();
        }, 20L * 30, 20L * 60);
    }

    private void checkAutoSeasonClose() {
        if (!warsConfig.getBoolean("season-war.auto-close.enabled", true)) {
            return;
        }
        String dayStr = warsConfig.getString("season-war.auto-close.day-of-week", "SUNDAY").toUpperCase(Locale.ROOT);
        String timeStr = warsConfig.getString("season-war.auto-close.time", "22:00");
        DayOfWeek targetDay;
        LocalTime targetTime;
        try {
            targetDay = DayOfWeek.valueOf(dayStr);
            targetTime = LocalTime.parse(timeStr.trim());
        } catch (Exception e) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String todayStr = LocalDate.now().toString();
        if (now.getDayOfWeek() == targetDay
                && !now.toLocalTime().isBefore(targetTime)
                && !todayStr.equals(lastAutoCloseDate)) {
            lastAutoCloseDate = todayStr;
            warsConfig.set("season-war.last-auto-close-date", todayStr);
            try {
                warsConfig.save(warsFile);
            } catch (IOException ignored) {}
            endSeasonAndReward();
        }
    }

    private boolean isInsideScheduledWindow() {
        LocalTime now = LocalTime.now();
        for (TimeWindow window : peakWindows) {
            if (window.contains(now)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDoublePointsActive() {
        return manualDoublePoints || isInsideScheduledWindow();
    }

    public boolean toggleManualDoublePoints() {
        this.manualDoublePoints = !this.manualDoublePoints;
        String prefix = plugin.getMessages().getString("prefix", "");
        if (manualDoublePoints) {
            Bukkit.broadcastMessage(ColorUtil.color(prefix + "&#FFAA00&l[GUERRA 2X] &#55FF55Multiplicador 2x de Pontos de Guerra ATIVADO pela Staff!"));
        } else {
            Bukkit.broadcastMessage(ColorUtil.color(prefix + "&#FFAA00&l[GUERRA 2X] &cMultiplicador 2x manual encerrado pela Staff."));
        }
        return manualDoublePoints;
    }

    public boolean isTrackedWarBlock(Material material) {
        return enabled && material != null && blockPoints.containsKey(material);
    }

    public int getBlockWarPoints(Material material) {
        if (!enabled || material == null) return 0;
        return blockPoints.getOrDefault(material, 0);
    }

    public int getEntityWarPoints(LivingEntity entity) {
        if (!enabled || entity == null) return 0;

        String customName = entity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            String stripped = ColorUtil.strip(customName).trim().toLowerCase(Locale.ROOT);
            Integer exactMatch = mobPointsByName.get(stripped);
            if (exactMatch != null) {
                return exactMatch;
            }
            for (Map.Entry<String, Integer> entry : mobPointsByName.entrySet()) {
                if (stripped.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        String typeName = entity.getType().name();
        Integer byType = mobPointsByType.get(typeName);
        if (byType != null) {
            return byType;
        }

        // Entidades de mods no Mohist costumam herdar Monster ou ter nomes customizados
        if (entity instanceof Monster && defaultMonsterPoints > 0) {
            return defaultMonsterPoints;
        }
        return 0;
    }

    public int getPvpKillWarPoints() {
        return enabled ? pvpKillPoints : 0;
    }

    public int addWarPoints(Clan clan, Player contributor, int basePoints, String reason) {
        if (!enabled || clan == null || basePoints <= 0) {
            return 0;
        }

        double clanFactor = plugin.getBankUpgradeManager().getWarMultiplierFactor(clan);
        double peakFactor = isDoublePointsActive() ? 2.0 : 1.0;
        int finalPoints = Math.max(1, (int) Math.round(basePoints * clanFactor * peakFactor));

        clan.addSeasonWarPoints(finalPoints);
        ClanMember member = contributor != null ? clan.getMember(contributor.getUniqueId()) : null;
        if (member != null) {
            member.addWarPointsContributed(finalPoints);
        }

        invalidateCache();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRepository().saveEconomy(clan);
            if (member != null) {
                plugin.getRepository().saveMember(clan.getId(), member);
            }
        });

        return finalPoints;
    }

    public void invalidateCache() {
        cachedTopWar = null;
    }

    public List<Clan> getTopSeasonClans(int limit) {
        long now = System.currentTimeMillis();
        List<Clan> cached = cachedTopWar;
        if (cached == null || now - lastTopWarCalc > TOP_CACHE_MS) {
            cached = plugin.getClanManager().getAllClans().stream()
                    .sorted(Comparator.comparingInt(Clan::getSeasonWarPoints).reversed()
                            .thenComparing(Clan::getName))
                    .collect(Collectors.toList());
            cachedTopWar = cached;
            lastTopWarCalc = now;
        }
        return cached.size() <= limit ? cached : cached.subList(0, limit);
    }

    public int getClanRank(UUID clanId) {
        if (clanId == null) return -1;
        List<Clan> all = getTopSeasonClans(Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(clanId)) {
                return i + 1;
            }
        }
        return -1;
    }

    public void endSeasonAndReward() {
        List<Clan> podium = getTopSeasonClans(3);
        String prefix = plugin.getMessages().getString("prefix", "");

        Bukkit.broadcastMessage(ColorUtil.color("&8&m---------------------------------------------"));
        Bukkit.broadcastMessage(ColorUtil.color(prefix + "&#FFAA00&lFIM DA TEMPORADA DE GUERRA DE CLÃS!"));

        if (podium.isEmpty() || podium.get(0).getSeasonWarPoints() <= 0) {
            Bukkit.broadcastMessage(ColorUtil.color(" &7Nenhum clã pontuou nesta temporada."));
        } else {
            String[] medals = {"&e🥇 #1 ", "&f🥈 #2 ", "&6🥉 #3 "};
            String[] rewardKeys = {"top1", "top2", "top3"};

            for (int i = 0; i < podium.size(); i++) {
                Clan c = podium.get(i);
                if (c.getSeasonWarPoints() <= 0) break;

                Bukkit.broadcastMessage(ColorUtil.color(" " + medals[i] + c.getName() + " &8[" + c.getTagColor() + c.getTag()
                        + "&8] &7- &#FFAA00" + c.getSeasonWarPoints() + " pts"));

                String base = "season-war.rewards." + rewardKeys[i];
                long rewardTp = Math.max(0L, warsConfig.getLong(base + ".bank-tp", i == 0 ? 100000L : (i == 1 ? 50000L : 25000L)));
                double rewardMoney = Math.max(0.0, warsConfig.getDouble(base + ".bank-money", i == 0 ? 50000.0 : (i == 1 ? 25000.0 : 10000.0)));
                int clanPts = Math.max(0, warsConfig.getInt(base + ".clan-points", i == 0 ? 500 : (i == 1 ? 250 : 100)));

                if (i == 0) {
                    c.getStats().addWarWin();
                }
                if (rewardTp > 0L) c.getBank().addTp(rewardTp);
                if (rewardMoney > 0.0) c.getBank().addMoney(rewardMoney);
                if (clanPts > 0) plugin.getClanManager().addPoints(c, clanPts);
                c.addLog(null, "Ficou em #" + (i + 1) + " na Temporada de Guerra (" + c.getSeasonWarPoints() + " pts)");
            }
        }
        Bukkit.broadcastMessage(ColorUtil.color("&8&m---------------------------------------------"));

        // Zerar pontos da temporada atual em memória e no banco
        for (Clan c : plugin.getClanManager().getAllClans()) {
            c.setSeasonWarPoints(0);
            for (ClanMember m : c.getMembers().values()) {
                m.setWarPointsContributed(0);
            }
        }
        invalidateCache();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRepository().resetAllSeasonWarPoints();
            for (Clan c : plugin.getClanManager().getAllClans()) {
                plugin.getRepository().saveClan(c);
            }
        });
    }

    public void cleanup() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        blockPoints.clear();
        mobPointsByName.clear();
        mobPointsByType.clear();
        peakWindows.clear();
        cachedTopWar = null;
    }
}
