package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalEventManager implements Listener {

    private final VClans plugin;
    private final File configFile;
    private FileConfiguration config;

    private Location spawnLocation;
    private Location exitLocation;
    private boolean active = false;

    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();

    public GlobalEventManager(VClans plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "eventoglobal.yml");
        loadConfig();
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("eventoglobal.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);

        this.spawnLocation = deserializeLocation("spawn-location");
        this.exitLocation = deserializeLocation("exit-location");
    }

    public void saveConfig() {
        serializeLocation("spawn-location", spawnLocation);
        serializeLocation("exit-location", exitLocation);
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSpawnLocation(Location loc) {
        this.spawnLocation = loc;
        saveConfig();
    }

    public void setExitLocation(Location loc) {
        this.exitLocation = loc;
        saveConfig();
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public Location getExitLocation() {
        return exitLocation;
    }

    public boolean isActive() {
        return active;
    }

    public int getParticipantsCount() {
        return participants.size();
    }

    public int getEliminatedCount() {
        return eliminated.size();
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public boolean isEliminated(UUID uuid) {
        return eliminated.contains(uuid);
    }

    public boolean startEvent() {
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            return false;
        }

        this.participants.clear();
        this.eliminated.clear();
        this.active = true;

        String prefix = config.getString("messages.prefix", "&6[EVENTO CLÃS] ");

        // Broadcast de Anúncio
        List<String> announcement = config.getStringList("messages.announcement");
        if (announcement != null && !announcement.isEmpty()) {
            for (String line : announcement) {
                Bukkit.broadcastMessage(ColorUtil.color(line));
            }
        }

        // Título e Som para todos os jogadores online
        String title = config.getString("messages.title.title", "&c&lBOSS INVADIU A TERRA!");
        String subtitle = config.getString("messages.title.subtitle", "&eUse /evento clans para entrar!");
        int fadeIn = config.getInt("messages.title.fadeIn", 10);
        int stay = config.getInt("messages.title.stay", 70);
        int fadeOut = config.getInt("messages.title.fadeOut", 20);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(ColorUtil.color(title), ColorUtil.color(subtitle), fadeIn, stay, fadeOut);
            try {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            } catch (Exception ignored) {}
        }

        return true;
    }

    public void stopEvent() {
        this.active = false;
        String prefix = config.getString("messages.prefix", "&6[EVENTO CLÃS] ");
        String endMsg = prefix + config.getString("messages.event-ended", "&cO Evento Global de Clãs foi encerrado!");

        // Teleportar todos os participantes para a saída
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(ColorUtil.color(endMsg));
                if (exitLocation != null && exitLocation.getWorld() != null) {
                    p.teleport(exitLocation);
                }
            }
        }

        this.participants.clear();
        this.eliminated.clear();
        Bukkit.broadcastMessage(ColorUtil.color(endMsg));
    }

    public void joinEvent(Player player) {
        String prefix = config.getString("messages.prefix", "&6[EVENTO CLÃS] ");

        if (!active) {
            player.sendMessage(ColorUtil.color(prefix + config.getString("messages.event-not-active", "&cO Evento Global de Clãs não está ativo no momento.")));
            return;
        }

        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            player.sendMessage(ColorUtil.color(prefix + config.getString("messages.spawn-not-set", "&cO spawn do evento ainda não foi configurado.")));
            return;
        }

        // Se já morreu no evento, NÃO pode mais voltar!
        if (eliminated.contains(player.getUniqueId())) {
            player.sendMessage(ColorUtil.color(prefix + config.getString("messages.cannot-return", "&cVocê já morreu neste evento e foi eliminado! Não é possível retornar.")));
            return;
        }

        // Se já está dentro
        if (participants.contains(player.getUniqueId())) {
            player.sendMessage(ColorUtil.color(prefix + config.getString("messages.already-inside", "&cVocê já está dentro da arena do evento!")));
            return;
        }

        // Se requer clã
        boolean requireClan = config.getBoolean("settings.require-clan", true);
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (requireClan && clan == null) {
            player.sendMessage(ColorUtil.color(prefix + config.getString("messages.must-have-clan", "&cVocê precisa pertencer a um clã para participar do Evento Global!")));
            return;
        }

        // Teleportar e registrar
        player.teleport(spawnLocation);
        participants.add(player.getUniqueId());

        player.sendMessage(ColorUtil.color(prefix + config.getString("messages.entered", "&aVocê entrou no Evento Global de Clãs!")));
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } catch (Exception ignored) {}
    }

    public void leaveEvent(Player player) {
        String prefix = config.getString("messages.prefix", "&6[EVENTO CLÃS] ");
        if (!participants.contains(player.getUniqueId())) {
            player.sendMessage(ColorUtil.color(prefix + "&cVocê não está dentro do evento!"));
            return;
        }

        participants.remove(player.getUniqueId());
        eliminated.add(player.getUniqueId());

        if (exitLocation != null && exitLocation.getWorld() != null) {
            player.teleport(exitLocation);
        }

        player.sendMessage(ColorUtil.color(prefix + config.getString("messages.left-event", "&eVocê saiu do evento e foi desclassificado da edição atual.")));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!participants.contains(victim.getUniqueId())) {
            return;
        }

        participants.remove(victim.getUniqueId());
        eliminated.add(victim.getUniqueId());

        String prefix = config.getString("messages.prefix", "&6[EVENTO CLÃS] ");
        victim.sendMessage(ColorUtil.color(prefix + config.getString("messages.eliminated", "&cVocê foi abatido e eliminado do evento! Não é possível retornar.")));

        Clan clan = plugin.getClanManager().getClanByPlayer(victim);
        String clanTag = clan != null ? clan.getTag() : "Sem Clã";

        String broadcastMsg = config.getString("messages.eliminated-broadcast", "&c[Evento] O guerreiro &e{player} &8[{clan}&8] &cfoi eliminado da batalha!")
                .replace("{player}", victim.getName())
                .replace("{clan}", clanTag);

        for (UUID u : participants) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                p.sendMessage(ColorUtil.color(broadcastMsg));
            }
        }

        // Teleportar para a saída
        if (exitLocation != null && exitLocation.getWorld() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    victim.teleport(exitLocation);
                }
            }, 5L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!participants.contains(player.getUniqueId())) {
            return;
        }

        if (player.isOp() || player.hasPermission("vclans.admin")) {
            return;
        }

        String message = event.getMessage().toLowerCase();
        List<String> blocked = config.getStringList("settings.blocked-commands");
        if (blocked != null) {
            for (String cmd : blocked) {
                if (message.startsWith(cmd.toLowerCase())) {
                    event.setCancelled(true);
                    String prefix = config.getString("messages.prefix", "&6[EVENTO CLÃS] ");
                    player.sendMessage(ColorUtil.color(prefix + config.getString("messages.blocked-command-msg", "&cVocê não pode utilizar este comando dentro do evento!")));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (participants.remove(player.getUniqueId())) {
            eliminated.add(player.getUniqueId());
            if (exitLocation != null && exitLocation.getWorld() != null) {
                player.teleport(exitLocation);
            }
        }
    }

    public void cleanup() {
        if (active) {
            stopEvent();
        }
    }

    private Location deserializeLocation(String path) {
        if (!config.contains(path + ".world")) return null;
        String worldName = config.getString(path + ".world");
        if (worldName == null || worldName.isEmpty()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw", 0.0);
        float pitch = (float) config.getDouble(path + ".pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private void serializeLocation(String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            config.set(path + ".world", "");
            return;
        }
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }
}
