package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaManager {

    private final VClans plugin;
    private final File arenaFile;
    private FileConfiguration arenaConfig;
    private final Map<String, Arena> arenas = new ConcurrentHashMap<>();

    public ArenaManager(VClans plugin) {
        this.plugin = plugin;
        this.arenaFile = new File(plugin.getDataFolder(), "arenas.yml");
        loadArenas();
    }

    public void loadArenas() {
        arenas.clear();
        if (!arenaFile.exists()) {
            plugin.saveResource("duels.yml", false);
        }
        arenaConfig = YamlConfiguration.loadConfiguration(arenaFile);
        ConfigurationSection section = arenaConfig.getConfigurationSection("arenas");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection arenaSec = section.getConfigurationSection(key);
            if (arenaSec == null) continue;

            Location s1 = deserializeLocation(arenaSec.getConfigurationSection("spawn1"));
            Location s2 = deserializeLocation(arenaSec.getConfigurationSection("spawn2"));
            Location spec = deserializeLocation(arenaSec.getConfigurationSection("spectator"));
            Location exit = deserializeLocation(arenaSec.getConfigurationSection("exit"));

            Arena arena = new Arena(key, s1, s2, spec, exit);
            arenas.put(key.toLowerCase(), arena);
        }
    }

    public void saveArenas() {
        for (Arena arena : arenas.values()) {
            String path = "arenas." + arena.getName();
            serializeLocation(path + ".spawn1", arena.getSpawn1());
            serializeLocation(path + ".spawn2", arena.getSpawn2());
            serializeLocation(path + ".spectator", arena.getSpectator());
            serializeLocation(path + ".exit", arena.getExitLocation());
        }
        try {
            arenaConfig.save(arenaFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Arena getArena(String name) {
        return name != null ? arenas.get(name.toLowerCase()) : null;
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Arena getAvailableArena() {
        for (Arena arena : arenas.values()) {
            if (arena.isConfigured() && !arena.isInUse()) {
                return arena;
            }
        }
        return null;
    }

    public Arena createArena(String name) {
        Arena arena = new Arena(name);
        arenas.put(name.toLowerCase(), arena);
        saveArenas();
        return arena;
    }

    public boolean deleteArena(String name) {
        Arena removed = arenas.remove(name.toLowerCase());
        if (removed != null) {
            arenaConfig.set("arenas." + removed.getName(), null);
            saveArenas();
            return true;
        }
        return false;
    }

    private void serializeLocation(String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            arenaConfig.set(path, null);
            return;
        }
        arenaConfig.set(path + ".world", loc.getWorld().getName());
        arenaConfig.set(path + ".x", loc.getX());
        arenaConfig.set(path + ".y", loc.getY());
        arenaConfig.set(path + ".z", loc.getZ());
        arenaConfig.set(path + ".yaw", loc.getYaw());
        arenaConfig.set(path + ".pitch", loc.getPitch());
    }

    private Location deserializeLocation(ConfigurationSection sec) {
        if (sec == null) return null;
        String worldName = sec.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = sec.getDouble("x");
        double y = sec.getDouble("y");
        double z = sec.getDouble("z");
        float yaw = (float) sec.getDouble("yaw");
        float pitch = (float) sec.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}

