package org.vine.vclans.command;

import org.vine.vclans.VClans;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ClanChatCommand implements CommandExecutor {

    private final VClans plugin;

    public ClanChatCommand(VClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cEste comando so pode ser executado por jogadores."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtil.color(plugin.getMessages().getString("clan.chat-usage", "&#FF5555Uso correto: &e/. <mensagem>")));
            return true;
        }

        String message = String.join(" ", args);
        plugin.getClanManager().sendClanChat(player, message);
        return true;
    }
}
