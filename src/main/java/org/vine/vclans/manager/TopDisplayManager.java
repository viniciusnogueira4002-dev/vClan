package org.vine.vclans.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TopDisplayManager implements Listener {

    private final VClans plugin;
    private final File topFile;
    private final NamespacedKey displayTagKey;

    private FileConfiguration topConfig;
    private UUID textDisplayUuid;
    private Location displayLocation;
    private BukkitTask updateTask;
    private volatile int generation = 0;

    public TopDisplayManager(VClans plugin) {
        this.plugin = plugin;
        this.topFile = new File(plugin.getDataFolder(), "top.yml");
        this.displayTagKey = new NamespacedKey(plugin, "vclans_top_display");
        loadConfig();
        cleanupStaleInLoadedWorlds();
        startUpdateTask();
    }

    public void loadConfig() {
        if (!topFile.exists()) {
            plugin.saveResource("top.yml", false);
        }
        topConfig = YamlConfiguration.loadConfiguration(topFile);

        this.textDisplayUuid = null;
        String uuidStr = topConfig.getString("location.entity-uuid", "");
        if (!uuidStr.isEmpty()) {
            try {
                this.textDisplayUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {}
        }

        this.displayLocation = null;
        String worldName = topConfig.getString("location.world", "");
        if (!worldName.isEmpty()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = topConfig.getDouble("location.x");
                double y = topConfig.getDouble("location.y");
                double z = topConfig.getDouble("location.z");
                float yaw = (float) topConfig.getDouble("location.yaw");
                float pitch = (float) topConfig.getDouble("location.pitch");
                this.displayLocation = new Location(world, x, y, z, yaw, pitch);
            }
        }
    }

    public void saveConfig() {
        if (displayLocation != null && displayLocation.getWorld() != null) {
            topConfig.set("location.world", displayLocation.getWorld().getName());
            topConfig.set("location.x", displayLocation.getX());
            topConfig.set("location.y", displayLocation.getY());
            topConfig.set("location.z", displayLocation.getZ());
            topConfig.set("location.yaw", displayLocation.getYaw());
            topConfig.set("location.pitch", displayLocation.getPitch());
        } else {
            topConfig.set("location.world", "");
        }
        topConfig.set("location.entity-uuid", textDisplayUuid != null ? textDisplayUuid.toString() : "");

        try {
            topConfig.save(topFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setLocation(Location location) {
        generation++;
        Location oldLocation = this.displayLocation;
        removeActiveEntityOnly();

        if (oldLocation != null && oldLocation.getWorld() != null) {
            try {
                oldLocation.getChunk().load();
            } catch (Exception ignored) {}
            purgeDisplaysNearby(oldLocation, 64);
        }

        if (location != null && location.getWorld() != null) {
            try {
                location.getChunk().load();
            } catch (Exception ignored) {}
            purgeDisplaysNearby(location, 64);
        }

        this.textDisplayUuid = null;
        this.displayLocation = location;
        saveConfig();
        spawnOrUpdateDisplay(true);
    }

    public void removeDisplay() {
        generation++;
        Location oldLocation = this.displayLocation;
        removeActiveEntityOnly();

        if (oldLocation != null && oldLocation.getWorld() != null) {
            try {
                oldLocation.getChunk().load();
            } catch (Exception ignored) {}
            purgeDisplaysInWorld(oldLocation.getWorld(), null, 0);
        }

        this.textDisplayUuid = null;
        this.displayLocation = null;
        saveConfig();
    }

    private void removeActiveEntityOnly() {
        if (textDisplayUuid != null) {
            Entity entity = Bukkit.getEntity(textDisplayUuid);
            if (entity != null) {
                entity.remove();
            }
        }
        if (displayLocation != null && displayLocation.getWorld() != null
                && displayLocation.getWorld().isChunkLoaded(displayLocation.getBlockX() >> 4, displayLocation.getBlockZ() >> 4)) {
            purgeStaleDuplicatesNear(displayLocation, 16, null);
        }
    }

    private void cleanupStaleInLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay td : world.getEntitiesByClass(TextDisplay.class)) {
                if (isManagedOrLegacyDisplay(td)) {
                    // Remove entidades persistentes antigas para recriar apenas a versão volátil atual
                    if (td.isPersistent() || !td.getUniqueId().equals(textDisplayUuid)) {
                        td.remove();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!plugin.isEnabled()) return;

        for (Entity entity : event.getEntities()) {
            if (isManagedOrLegacyDisplay(entity)) {
                // Remove qualquer entidade antiga persistente ou com UUID diferente do ativo
                if (entity.isPersistent() || textDisplayUuid == null || !entity.getUniqueId().equals(textDisplayUuid)) {
                    entity.remove();
                }
            }
        }

        if (displayLocation != null
                && displayLocation.getWorld() != null
                && displayLocation.getWorld().equals(event.getWorld())
                && (displayLocation.getBlockX() >> 4) == event.getChunk().getX()
                && (displayLocation.getBlockZ() >> 4) == event.getChunk().getZ()) {
            if (getTextDisplayEntity() == null) {
                spawnOrUpdateDisplay(false);
            }
        }
    }

    private boolean isManagedOrLegacyDisplay(Entity entity) {
        if (entity instanceof TextDisplay td) {
            if (td.getPersistentDataContainer().has(displayTagKey, PersistentDataType.BYTE)) {
                return true;
            }
            if (textDisplayUuid != null && td.getUniqueId().equals(textDisplayUuid)) {
                return true;
            }
            return isLegacyTextMatch(td.getText());
        }
        if (entity instanceof ArmorStand as) {
            return isLegacyTextMatch(as.getCustomName());
        }
        return false;
    }

    private boolean isLegacyTextMatch(String rawText) {
        if (rawText == null || rawText.isEmpty()) return false;
        String plain = ChatColor.stripColor(rawText).toUpperCase();
        return plain.contains("TOP CLÃS")
                || plain.contains("TOP CLANS")
                || plain.contains("REDE ECLYPSE")
                || plain.contains("VCLANS")
                || plain.contains("ATUALIZADO A CADA")
                || plain.contains("USE /CLAN TOP");
    }

    private void purgeStaleDuplicatesNear(Location center, double radius, UUID keepUuid) {
        if (center == null || center.getWorld() == null) return;
        try {
            for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (keepUuid != null && e.getUniqueId().equals(keepUuid)) {
                    continue;
                }
                if (isManagedOrLegacyDisplay(e)) {
                    e.remove();
                }
            }
        } catch (Exception ignored) {}
    }

    public int purgeDisplaysNearby(Location location, double radius) {
        if (location == null || location.getWorld() == null) return 0;
        int count = purgeDisplaysInWorld(location.getWorld(), location, radius);
        if (count == 0) {
            count += purgeDisplaysInWorld(location.getWorld(), null, 0);
        }
        return count;
    }

    public int purgeDisplaysInWorld(World world, Location center, double radius) {
        if (world == null) return 0;
        int count = 0;
        try {
            Collection<? extends Entity> entities = (center != null && radius > 0)
                    ? world.getNearbyEntities(center, radius, radius, radius)
                    : world.getEntitiesByClass(TextDisplay.class);
            for (Entity e : entities) {
                if (e instanceof TextDisplay && isManagedOrLegacyDisplay(e)) {
                    e.remove();
                    count++;
                }
            }

            Collection<? extends Entity> stands = (center != null && radius > 0)
                    ? world.getNearbyEntities(center, radius, radius, radius)
                    : world.getEntitiesByClass(ArmorStand.class);
            for (Entity e : stands) {
                if (e instanceof ArmorStand && isManagedOrLegacyDisplay(e)) {
                    e.remove();
                    count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    public void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        long intervalSeconds = Math.max(30L, topConfig.getLong("top-display.update-interval-seconds", 300));
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> spawnOrUpdateDisplay(false), 100L, intervalSeconds * 20L);
    }

    public void spawnOrUpdateDisplay() {
        spawnOrUpdateDisplay(false);
    }

    private void spawnOrUpdateDisplay(boolean forceChunkLoad) {
        if (!plugin.isEnabled() || displayLocation == null || displayLocation.getWorld() == null) {
            return;
        }
        if (!topConfig.getBoolean("display.enabled", true)) {
            return;
        }

        final Location targetLoc = displayLocation.clone();
        final World world = targetLoc.getWorld();
        if (world == null) return;

        int chunkX = targetLoc.getBlockX() >> 4;
        int chunkZ = targetLoc.getBlockZ() >> 4;
        if (!forceChunkLoad && !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        if (forceChunkLoad && !world.isChunkLoaded(chunkX, chunkZ)) {
            targetLoc.getChunk().load();
        }

        final int opGen = this.generation;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Clan> topClans = plugin.getClanManager().getTopClansByPoints(10);
            String formattedText = buildDisplayText(topClans);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || opGen != this.generation || this.displayLocation == null) {
                    return;
                }
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return;
                }

                TextDisplay display = getTextDisplayEntity();
                if (display != null && display.isPersistent()) {
                    // Substitui entidade persistente legada por uma versão volátil (setPersistent(false))
                    display.remove();
                    display = null;
                }

                purgeStaleDuplicatesNear(targetLoc, 16, display != null ? display.getUniqueId() : null);

                if (display == null) {
                    display = world.spawn(targetLoc, TextDisplay.class, ent -> {
                        ent.setPersistent(false);
                        ent.getPersistentDataContainer().set(displayTagKey, PersistentDataType.BYTE, (byte) 1);
                        ent.setBillboard(Display.Billboard.CENTER);
                        ent.setDefaultBackground(false);
                        ent.setBackgroundColor(Color.fromARGB(180, 15, 15, 20));
                        ent.setShadowed(true);
                        ent.setAlignment(TextDisplay.TextAlignment.CENTER);
                        ent.setTransformation(new Transformation(
                                new Vector3f(0f, 0f, 0f),
                                new AxisAngle4f(),
                                new Vector3f(1.15f, 1.15f, 1.15f),
                                new AxisAngle4f()
                        ));
                    });
                    this.textDisplayUuid = display.getUniqueId();
                    saveConfig();
                } else {
                    display.setPersistent(false);
                    display.getPersistentDataContainer().set(displayTagKey, PersistentDataType.BYTE, (byte) 1);
                }

                display.setText(ColorUtil.color(formattedText));
            });
        });
    }

    private TextDisplay getTextDisplayEntity() {
        if (textDisplayUuid == null || displayLocation == null || displayLocation.getWorld() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(textDisplayUuid);
        if (entity instanceof TextDisplay textDisplay && entity.isValid()) {
            return textDisplay;
        }
        return null;
    }

    public String buildDisplayText(List<Clan> topClans) {
        List<String> lines = new ArrayList<>();

        List<String> header = topConfig.getStringList("display.header");
        lines.addAll(header);

        int maxEntries = topConfig.getInt("display.max-entries", 3);
        for (int i = 0; i < maxEntries; i++) {
            int rank = i + 1;
            if (i < topClans.size()) {
                Clan clan = topClans.get(i);
                String leaderName = "Desconhecido";
                var leader = clan.getMember(clan.getLeaderUuid());
                if (leader != null) leaderName = leader.getLastKnownName();

                List<String> formatList;
                if (rank == 1) {
                    formatList = topConfig.getStringList("display.format-top-1");
                } else if (rank == 2) {
                    formatList = topConfig.getStringList("display.format-top-2");
                } else if (rank == 3) {
                    formatList = topConfig.getStringList("display.format-top-3");
                } else {
                    formatList = topConfig.getStringList("display.format-others");
                }

                for (String fmt : formatList) {
                    String line = fmt
                            .replace("{rank}", String.valueOf(rank))
                            .replace("{name}", clan.getName())
                            .replace("{tag}", clan.getTag())
                            .replace("{color}", clan.getTagColor())
                            .replace("{leader}", leaderName)
                            .replace("{members}", String.valueOf(clan.getMembersCount()))
                            .replace("{points}", String.valueOf(clan.getStats().getPoints()))
                            .replace("{kdr}", clan.getStats().getFormattedKdr())
                            .replace("{duel_wins}", String.valueOf(clan.getStats().getDuelWins()))
                            .replace("{war_wins}", String.valueOf(clan.getStats().getWarWins()));
                    lines.add(line);
                }
            } else if (rank <= 3) {
                if (rank == 1) {
                    lines.add(" &e🥇 &6&l#1 &7--- &8(Vazio)");
                } else if (rank == 2) {
                    lines.add(" &f🥈 &f&l#2 &7--- &8(Vazio)");
                } else {
                    lines.add(" &6🥉 &c&l#3 &7--- &8(Vazio)");
                }
            } else {
                lines.add(" &8#" + rank + " &7--- &8(Vazio)");
            }
        }

        List<String> footer = topConfig.getStringList("display.footer");
        lines.addAll(footer);

        return String.join("\n", lines);
    }

    public void cleanup() {
        generation++;
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        removeActiveEntityOnly();
    }
}
