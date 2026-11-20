package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TopDisplayManager {

    private final VClans plugin;
    private final File topFile;
    private FileConfiguration topConfig;
    private UUID textDisplayUuid;
    private Location displayLocation;
    private BukkitTask updateTask;

    public TopDisplayManager(VClans plugin) {
        this.plugin = plugin;
        this.topFile = new File(plugin.getDataFolder(), "top.yml");
        loadConfig();
        startUpdateTask();
    }

    public void loadConfig() {
        if (!topFile.exists()) {
            plugin.saveResource("top.yml", false);
        }
        topConfig = YamlConfiguration.loadConfiguration(topFile);

        String uuidStr = topConfig.getString("location.entity-uuid", "");
        if (!uuidStr.isEmpty()) {
            try {
                this.textDisplayUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {}
        }

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
        if (location != null) {
            purgeDisplaysNearby(location, 64);
        }
        removeDisplay();
        this.displayLocation = location;
        saveConfig();
        spawnOrUpdateDisplay();
    }

    public void removeDisplay() {
        if (textDisplayUuid != null) {
            Entity entity = Bukkit.getEntity(textDisplayUuid);
            if (entity != null) {
                entity.remove();
            }
        }
        if (displayLocation != null && displayLocation.getWorld() != null) {
            purgeDisplaysInWorld(displayLocation.getWorld(), null, 0);
        }
        this.textDisplayUuid = null;
        saveConfig();
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
            java.util.Collection<? extends Entity> entities;
            if (center != null && radius > 0) {
                entities = world.getNearbyEntities(center, radius, radius, radius);
            } else {
                entities = world.getEntitiesByClass(TextDisplay.class);
            }
            for (Entity e : entities) {
                if (e instanceof TextDisplay td) {
                    String text = td.getText();
                    if (text != null) {
                        String plain = ChatColor.stripColor(text).toUpperCase();
                        if (plain.contains("ECLYPSE") || plain.contains("TOP") || plain.contains("CL") || plain.contains("VCLANS") || plain.contains("RANKING") || plain.contains("STAFF") || plain.contains("DUELOS") || plain.contains("GUERRAS") || plain.contains("ATUALIZADO")) {
                            td.remove();
                            count++;
                        }
                    }
                }
            }

            // Também limpa ArmorStands se por acaso algum plugin de holograma antigo criou
            java.util.Collection<? extends Entity> stands = (center != null && radius > 0) 
                    ? world.getNearbyEntities(center, radius, radius, radius) 
                    : world.getEntitiesByClass(ArmorStand.class);
            for (Entity e : stands) {
                if (e instanceof ArmorStand as) {
                    String name = as.getCustomName();
                    if (name != null) {
                        String plain = ChatColor.stripColor(name).toUpperCase();
                        if (plain.contains("ECLYPSE") || plain.contains("TOP") || plain.contains("VCLANS") || plain.contains("RANKING") || plain.contains("STAFF")) {
                            as.remove();
                            count++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    public void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        long intervalSeconds = topConfig.getLong("top-display.update-interval-seconds", 300);
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnOrUpdateDisplay, 100L, intervalSeconds * 20L);
    }

    public void spawnOrUpdateDisplay() {
        if (displayLocation == null || displayLocation.getWorld() == null) {
            return;
        }

        loadConfig();

        // Buscar top clãs assincronamente e atualizar o display na main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Clan> topClans = plugin.getClanManager().getTopClansByPoints(10);
            String formattedText = buildDisplayText(topClans);

            Bukkit.getScheduler().runTask(plugin, () -> {
                TextDisplay display = getTextDisplayEntity();
                if (display == null) {
                    display = displayLocation.getWorld().spawn(displayLocation, TextDisplay.class, ent -> {
                        ent.setBillboard(Display.Billboard.CENTER);
                        ent.setDefaultBackground(false);
                        ent.setBackgroundColor(Color.fromARGB(180, 15, 15, 20)); // Fundo anime RPG translúcido
                        ent.setShadowed(true);
                        ent.setAlignment(TextDisplay.TextAlignment.CENTER);
                        // Escala HD ampliada
                        ent.setTransformation(new Transformation(
                                new Vector3f(0f, 0f, 0f),
                                new AxisAngle4f(),
                                new Vector3f(1.15f, 1.15f, 1.15f),
                                new AxisAngle4f()
                        ));
                    });
                    this.textDisplayUuid = display.getUniqueId();
                    saveConfig();
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

        // Cabeçalho
        List<String> header = topConfig.getStringList("display.header");
        lines.addAll(header);

        // Top 1, 2, 3 e outros
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

        // Rodapé
        List<String> footer = topConfig.getStringList("display.footer");
        lines.addAll(footer);

        return String.join("\n", lines);
    }

    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }
}

