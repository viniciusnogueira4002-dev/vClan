package org.vine.vclans.manager;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanInvite;
import org.vine.vclans.util.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InviteManager {

    private final VClans plugin;
    // targetUuid -> (clanId -> ClanInvite)
    private final Map<UUID, Map<UUID, ClanInvite>> pendingInvites = new ConcurrentHashMap<>();
    // Jogadores com convites desativados
    private final Set<UUID> disabledInvites = ConcurrentHashMap.newKeySet();

    private org.bukkit.scheduler.BukkitTask cleanupTask;

    public InviteManager(VClans plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private void startCleanupTask() {
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Map<UUID, ClanInvite>> entry : pendingInvites.entrySet()) {
                entry.getValue().values().removeIf(ClanInvite::isExpired);
                if (entry.getValue().isEmpty()) {
                    pendingInvites.remove(entry.getKey());
                }
            }
        }, 100L, 100L); // a cada 5 segundos
    }

    public boolean areInvitesDisabled(UUID playerUuid) {
        return disabledInvites.contains(playerUuid);
    }

    public boolean toggleInvites(UUID playerUuid) {
        if (disabledInvites.contains(playerUuid)) {
            disabledInvites.remove(playerUuid);
            return true; // Agora ativado
        } else {
            disabledInvites.add(playerUuid);
            return false; // Agora desativado
        }
    }

    public boolean hasInvite(UUID playerUuid, UUID clanId) {
        Map<UUID, ClanInvite> playerMap = pendingInvites.get(playerUuid);
        if (playerMap == null) return false;
        ClanInvite invite = playerMap.get(clanId);
        return invite != null && !invite.isExpired();
    }

    public ClanInvite getInvite(UUID playerUuid, UUID clanId) {
        Map<UUID, ClanInvite> playerMap = pendingInvites.get(playerUuid);
        if (playerMap == null) return null;
        ClanInvite invite = playerMap.get(clanId);
        return (invite != null && !invite.isExpired()) ? invite : null;
    }

    public List<ClanInvite> getInvites(UUID playerUuid) {
        Map<UUID, ClanInvite> playerMap = pendingInvites.get(playerUuid);
        if (playerMap == null) return Collections.emptyList();
        List<ClanInvite> list = new ArrayList<>();
        for (ClanInvite inv : playerMap.values()) {
            if (!inv.isExpired()) {
                list.add(inv);
            }
        }
        return list;
    }

    public boolean sendInvite(Clan clan, Player target, Player sender) {
        int timeout = plugin.getConfig().getInt("clan.invite-timeout-seconds", 60);
        ClanInvite invite = new ClanInvite(clan.getId(), target.getUniqueId(), sender.getUniqueId(), timeout);

        pendingInvites.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>()).put(clan.getId(), invite);

        // Notificar jogador convidado com botões interativos e clicáveis
        String prefix = plugin.getMessages().getString("prefix", "");
        String msgReceived = plugin.getMessages().getString("invite.received", "")
                .replace("{name}", clan.getName())
                .replace("{tag}", clan.getTag())
                .replace("{color}", clan.getTagColor());

        target.sendMessage(ColorUtil.color(prefix + msgReceived));

        // Componentes clicáveis para Aceitar ou Recusar
        TextComponent actionMsg = new TextComponent(ColorUtil.color(prefix + "&7Clique para responder: "));

        TextComponent acceptBtn = new TextComponent(ColorUtil.color("&a&l[ACEITAR]"));
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan aceitar " + clan.getTag()));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.color("&aClique aqui para ACEITAR o convite de &e" + clan.getName() + " &8[" + clan.getTagColor() + clan.getTag() + "&8]&a!")).create()));

        TextComponent space = new TextComponent(" ");

        TextComponent declineBtn = new TextComponent(ColorUtil.color("&c&l[RECUSAR]"));
        declineBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan recusar " + clan.getTag()));
        declineBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.color("&cClique aqui para RECUSAR o convite de &e" + clan.getName() + "&c.")).create()));

        TextComponent menuBtn = new TextComponent(ColorUtil.color(" &8| &e[ABRIR MENU]"));
        menuBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan"));
        menuBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtil.color("&eClique aqui para gerenciar seus convites pela interface gráfica.")).create()));

        actionMsg.addExtra(acceptBtn);
        actionMsg.addExtra(space);
        actionMsg.addExtra(declineBtn);
        actionMsg.addExtra(menuBtn);

        target.spigot().sendMessage(actionMsg);

        try {
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } catch (Exception ignored) {}

        return true;
    }

    public void removeInvite(UUID playerUuid, UUID clanId) {
        Map<UUID, ClanInvite> playerMap = pendingInvites.get(playerUuid);
        if (playerMap != null) {
            playerMap.remove(clanId);
            if (playerMap.isEmpty()) {
                pendingInvites.remove(playerUuid);
            }
        }
    }

    public void cleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        pendingInvites.clear();
        disabledInvites.clear();
    }
}
