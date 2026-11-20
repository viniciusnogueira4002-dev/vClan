package org.vine.vclans.command;

import org.vine.vclans.VClans;
import org.vine.vclans.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EventoCommand implements CommandExecutor, TabCompleter {

    private final VClans plugin;

    public EventoCommand(VClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cEste comando so pode ser executado por jogadores in-game."));
            return true;
        }

        var mgr = plugin.getGlobalEventManager();

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "clans", "clan", "entrar", "join" -> {
                mgr.joinEvent(player);
                return true;
            }

            case "sair", "leave" -> {
                mgr.leaveEvent(player);
                return true;
            }

            case "status", "info" -> {
                player.sendMessage(ColorUtil.color("&6&l⚔ EVENTO GLOBAL DE CLÃS ⚔"));
                player.sendMessage(ColorUtil.color("&7Status: " + (mgr.isActive() ? "&a&lATIVO - ENTRADA LIBERADA" : "&c&lINATIVO")));
                if (mgr.isActive()) {
                    player.sendMessage(ColorUtil.color("&7Guerreiros na batalha: &a" + mgr.getParticipantsCount()));
                    player.sendMessage(ColorUtil.color("&7Guerreiros eliminados: &c" + mgr.getEliminatedCount()));
                    player.sendMessage(ColorUtil.color("&aDigite &e/evento clans &apara entrar!"));
                }
                return true;
            }

            case "admin" -> {
                if (!player.hasPermission("vclans.admin") && !player.isOp()) {
                    player.sendMessage(ColorUtil.color("&cVocê não tem permissão para gerenciar o evento."));
                    return true;
                }
                handleAdmin(player, args);
                return true;
            }

            default -> {
                sendHelp(player);
                return true;
            }
        }
    }

    private void handleAdmin(Player player, String[] args) {
        var mgr = plugin.getGlobalEventManager();

        if (args.length < 2) {
            player.sendMessage(ColorUtil.color("&6&lPainel Administrativo do Evento Global:"));
            player.sendMessage(ColorUtil.color("&e/evento admin setspawn &7- Define onde todos os jogadores vão nascer"));
            player.sendMessage(ColorUtil.color("&e/evento admin setexit &7- Define o ponto de saída"));
            player.sendMessage(ColorUtil.color("&e/evento admin iniciar &7- Inicia e avisa no chat do servidor"));
            player.sendMessage(ColorUtil.color("&e/evento admin parar &7- Finaliza o evento e retira todos"));
            player.sendMessage(ColorUtil.color("&e/evento admin status &7- Ver participantes e eliminados"));
            player.sendMessage(ColorUtil.color("&e/evento admin reload &7- Recarrega eventoglobal.yml"));
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "setspawn" -> {
                mgr.setSpawnLocation(player.getLocation());
                player.sendMessage(ColorUtil.color("&aSpawn do Evento Global de Clãs definido com sucesso na sua posição!"));
            }
            case "setexit" -> {
                mgr.setExitLocation(player.getLocation());
                player.sendMessage(ColorUtil.color("&aPonto de saída do Evento Global definido com sucesso na sua posição!"));
            }
            case "iniciar", "start" -> {
                boolean ok = mgr.startEvent();
                if (ok) {
                    player.sendMessage(ColorUtil.color("&aEvento Global de Clãs iniciado e anunciado com sucesso!"));
                } else {
                    player.sendMessage(ColorUtil.color("&cConfigure o spawn antes de iniciar (/evento admin setspawn)!"));
                }
            }
            case "parar", "stop", "finalizar" -> {
                mgr.stopEvent();
                player.sendMessage(ColorUtil.color("&cEvento Global de Clãs finalizado com sucesso!"));
            }
            case "status" -> {
                player.sendMessage(ColorUtil.color("&6Status: " + (mgr.isActive() ? "&aATIVO" : "&cINATIVO")));
                player.sendMessage(ColorUtil.color("&7Vivos na arena: &a" + mgr.getParticipantsCount() + " &8| &7Eliminados: &c" + mgr.getEliminatedCount()));
            }
            case "reload" -> {
                mgr.loadConfig();
                player.sendMessage(ColorUtil.color("&aConfigurações de eventoglobal.yml recarregadas com sucesso!"));
            }
            default -> player.sendMessage(ColorUtil.color("&cUso: /evento admin [setspawn|setexit|iniciar|parar|status|reload]"));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtil.color("&6&l⚔ SISTEMA DE EVENTO DE CLÃS ⚔"));
        player.sendMessage(ColorUtil.color("&e/evento clans &7- Entrar na batalha global do clã"));
        player.sendMessage(ColorUtil.color("&e/evento sair &7- Desistir e sair da arena"));
        player.sendMessage(ColorUtil.color("&e/evento status &7- Ver informações do evento atual"));
        if (player.hasPermission("vclans.admin") || player.isOp()) {
            player.sendMessage(ColorUtil.color("&c/evento admin &7- Painel de controle do evento"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>(Arrays.asList("clans", "sair", "status", "info"));
            if (sender.hasPermission("vclans.admin") || sender.isOp()) {
                list.add("admin");
            }
            return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return Arrays.asList("setspawn", "setexit", "iniciar", "parar", "status", "reload")
                    .stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
