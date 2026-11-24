package org.vine.vclans.listener;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class ChatListener implements Listener {

    private final VClans plugin;

    public ChatListener(VClans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void sanitizeBadgeWireCharacters(AsyncPlayerChatEvent event) {
        String raw = event.getMessage();
        String sanitized = stripBadgeWirePua(raw);
        if (!sanitized.equals(raw)) {
            event.setMessage(sanitized);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("chat.override-chat-format", false)) {
            // Outro plugin (LPC, EssentialsChat) gerencia o chat via PlaceholderAPI
            return;
        }

        Player player = event.getPlayer();
        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        String rolePrefix = "";
        String clanTag = "";

        if (clan != null) {
            ClanMember member = clan.getMember(player.getUniqueId());
            ClanRole role = member != null ? member.getRole() : ClanRole.MEMBER;
            rolePrefix = role.getDefaultPrefix();
            if (!clan.getTagStyle().isEmpty()) {
                clanTag = org.vine.vclans.hook.EclypseCoreBadgeHook.encodeChatBadge(clan.getTag(), clan.getTagStyle()) + " ";
            } else {
                clanTag = plugin.getConfig().getString("chat.clan-tag-format", "&8[{color}{tag}&8] ")
                        .replace("{color}", clan.getTagColor())
                        .replace("{tag}", clan.getTag());
            }
        }

        String format = plugin.getConfig().getString("chat.format", "{role_prefix}{clan_tag}&f%1$s: &7%2$s")
                .replace("{role_prefix}", rolePrefix)
                .replace("{clan_tag}", clanTag)
                .replace("{player}", "%1$s")
                .replace("{message}", "%2$s");

        event.setFormat(ColorUtil.color(format));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("clan-chat.enabled", true)) {
            return;
        }

        String msg = event.getMessage();
        if (msg == null) return;

        // Captura /. ou /. <mensagem> ou /.mensagem
        if (msg.equalsIgnoreCase("/.") || msg.startsWith("/. ") || msg.startsWith("/.\t") || (msg.startsWith("/.") && msg.length() > 2)) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            String clanMsg;
            if (msg.equalsIgnoreCase("/.")) {
                clanMsg = "";
            } else if (msg.startsWith("/. ") || msg.startsWith("/.\t")) {
                clanMsg = msg.substring(3).trim();
            } else {
                clanMsg = msg.substring(2).trim();
            }

            plugin.getClanManager().sendClanChat(player, stripBadgeWirePua(clanMsg));
        }
    }

    private static String stripBadgeWirePua(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder sb = null;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '\uE600' && c <= '\uE77F') {
                if (sb == null) {
                    sb = new StringBuilder(input.length());
                    sb.append(input, 0, i);
                }
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb != null ? sb.toString() : input;
    }
}

