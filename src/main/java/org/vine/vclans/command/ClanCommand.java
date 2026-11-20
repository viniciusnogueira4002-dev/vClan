package org.vine.vclans.command;

import org.vine.vclans.VClans;
import org.vine.vclans.manager.BankUpgradeManager;
import org.vine.vclans.manager.BankUpgradeManager.TransactionResult;
import org.vine.vclans.menu.AdminMenu;
import org.vine.vclans.menu.MainMenu;
import org.vine.vclans.menu.RankingMenu;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.model.ClanUpgrades.UpgradeType;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final VClans plugin;

    public ClanCommand(VClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadAll();
                sender.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                        plugin.getMessages().getString("general.config-reloaded", "")));
                return true;
            }
            sender.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                    plugin.getMessages().getString("general.only-players", "")));
            return true;
        }

        String prefix = plugin.getMessages().getString("prefix", "");

        if (args.length == 0) {
            if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_OVERVIEW)) {
                new MainMenu(player, plugin).open();
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "menu", "gui" -> {
                if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_OVERVIEW)) {
                    new MainMenu(player, plugin).open();
                }
                return true;
            }

            case "criar", "create" -> {
                handleCreate(player, args, prefix);
                return true;
            }

            case "info", "perfil" -> {
                handleInfo(player, args, prefix);
                return true;
            }

            case "convidar", "invite" -> {
                handleInvite(player, args, prefix);
                return true;
            }

            case "aceitar", "accept" -> {
                handleAccept(player, args, prefix);
                return true;
            }

            case "recusar", "decline" -> {
                handleDecline(player, args, prefix);
                return true;
            }

            case "sair", "leave" -> {
                handleLeave(player, prefix);
                return true;
            }

            case "expulsar", "kick" -> {
                handleKick(player, args, prefix);
                return true;
            }

            case "promover", "promote" -> {
                handlePromote(player, args, prefix);
                return true;
            }

            case "rebaixar", "demote" -> {
                handleDemote(player, args, prefix);
                return true;
            }

            case "lider", "leader" -> {
                handleTransferLeader(player, args, prefix);
                return true;
            }

            case "tag" -> {
                handleTag(player, args, prefix);
                return true;
            }

            case "descricao", "desc" -> {
                handleDescription(player, args, prefix);
                return true;
            }

            case "top", "ranking" -> {
                if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_RANKINGS)) {
                    new RankingMenu(player, plugin, RankingMenu.RankingType.POINTS).open();
                }
                return true;
            }

            case "duelo", "duel" -> {
                handleDuel(player, args, prefix);
                return true;
            }

            case "desafiar" -> {
                handleDirectChallenge(player, args, prefix);
                return true;
            }

            case "alianca", "ally" -> {
                handleAlliance(player, args, prefix);
                return true;
            }

            case "guerra", "war" -> {
                handleWar(player, args, prefix);
                return true;
            }

            case "banco", "bank", "upgrade", "upgrades" -> {
                handleBank(player, args, prefix);
                return true;
            }

            case "config", "configuracoes", "ajustes", "settings" -> {
                Clan clan = plugin.getClanManager().getClanByPlayer(player);
                if (clan == null) {
                    player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
                    return true;
                }
                if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_SETTINGS)) {
                    new org.vine.vclans.menu.ClanSettingsMenu(player, plugin, clan).open();
                }
                return true;
            }

            case "convites", "invites" -> {
                boolean active = plugin.getInviteManager().toggleInvites(player.getUniqueId());
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString(
                        active ? "invite.invites-toggled-on" : "invite.invites-toggled-off", "")));
                return true;
            }

            case "ff", "fogoamigo", "friendlyfire" -> {
                handleFriendlyFireToggle(player, prefix);
                return true;
            }

            case "chat", "." -> {
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("clan.chat-usage", "&#FF5555Uso: &e/. <mensagem>")));
                    return true;
                }
                String clanMsg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.getClanManager().sendClanChat(player, clanMsg);
                return true;
            }

            case "reload" -> {
                if (!player.hasPermission("vclans.reload") && !player.hasPermission("eclypseclans.reload")) {
                    player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
                    return true;
                }
                plugin.reloadAll();
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.config-reloaded", "")));
                return true;
            }

            case "evento" -> {
                if (args.length >= 2 && (args[1].equalsIgnoreCase("sair") || args[1].equalsIgnoreCase("leave"))) {
                    plugin.getGlobalEventManager().leaveEvent(player);
                } else {
                    plugin.getGlobalEventManager().joinEvent(player);
                }
                return true;
            }

            case "admin" -> {
                handleAdmin(player, args, prefix);
                return true;
            }

            default -> {
                player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan [criar|info|banco|convidar|aceitar|recusar|sair|top|duelo|alianca|guerra]"));
                return true;
            }
        }
    }

    private void handleCreate(Player player, String[] args, String prefix) {
        if (!player.hasPermission("vclans.create") && !player.hasPermission("eclypseclans.create")) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }

        if (plugin.getClanManager().getClanByPlayer(player) != null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.already-in-clan", "")));
            return;
        }

        if (plugin.getClanManager().hasLeaveCooldown(player.getUniqueId())) {
            long rem = plugin.getClanManager().getRemainingLeaveCooldown(player.getUniqueId());
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.cooldown-active", "")
                    .replace("{time}", TimeUtil.formatDuration(rem))));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso correto: /clan criar <nome> <tag>"));
            return;
        }

        String name = args[1];
        String tag = args[2].toUpperCase();

        int minName = plugin.getConfig().getInt("clan.name.min-length", 3);
        int maxName = plugin.getConfig().getInt("clan.name.max-length", 16);
        String nameRegex = plugin.getConfig().getString("clan.name.regex", "^[a-zA-Z0-9_]+$");

        int minTag = plugin.getConfig().getInt("clan.tag.min-length", 2);
        int maxTag = plugin.getConfig().getInt("clan.tag.max-length", 4);
        String tagRegex = plugin.getConfig().getString("clan.tag.regex", "^[a-zA-Z0-9]+$");

        if (name.length() < minName || name.length() > maxName || !name.matches(nameRegex)) {
            player.sendMessage(ColorUtil.color(prefix + "&cO nome deve ter entre " + minName + " e " + maxName + " caracteres alfanuméricos."));
            return;
        }

        if (tag.length() < minTag || tag.length() > maxTag || !tag.matches(tagRegex)) {
            player.sendMessage(ColorUtil.color(prefix + "&cA tag deve ter entre " + minTag + " e " + maxTag + " caracteres alfanuméricos."));
            return;
        }

        if (plugin.getClanManager().isNameTaken(name)) {
            player.sendMessage(ColorUtil.color(prefix + "&cJá existe um clã cadastrado com este nome!"));
            return;
        }

        if (plugin.getClanManager().isTagTaken(tag)) {
            player.sendMessage(ColorUtil.color(prefix + "&cJá existe um clã cadastrado com esta tag!"));
            return;
        }

        double cost = plugin.getConfig().getDouble("clan.creation-cost", 0.0);
        if (cost > 0 && !plugin.getVaultHook().hasMoney(player, cost)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.not-enough-money", "")
                    .replace("{cost}", String.valueOf(cost))));
            return;
        }

        if (cost > 0) {
            plugin.getVaultHook().withdraw(player, cost);
        }

        Clan clan = plugin.getClanManager().createClan(name, tag, player);

        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.created", "")
                .replace("{name}", clan.getName())
                .replace("{tag}", clan.getTag())
                .replace("{color}", clan.getTagColor())));

        String broadcast = plugin.getMessages().getString("clan.created-broadcast", "")
                .replace("{player}", player.getName())
                .replace("{name}", clan.getName())
                .replace("{tag}", clan.getTag())
                .replace("{color}", clan.getTagColor());
        Bukkit.broadcastMessage(ColorUtil.color(prefix + broadcast));
    }

    private void handleInfo(Player player, String[] args, String prefix) {
        Clan clan;
        if (args.length >= 2) {
            clan = plugin.getClanManager().getClanByTag(args[1]);
            if (clan == null) clan = plugin.getClanManager().getClanByName(args[1]);
        } else {
            clan = plugin.getClanManager().getClanByPlayer(player);
        }

        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.clan-not-found", "")));
            return;
        }

        var leader = clan.getMember(clan.getLeaderUuid());
        String leaderName = leader != null ? leader.getLastKnownName() : "Desconhecido";

        player.sendMessage(ColorUtil.color("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.color(" &#FFAA00&l" + clan.getName() + " &8[" + clan.getTagColor() + clan.getTag() + "&8]"));
        player.sendMessage(ColorUtil.color(" &7Descrição: &f" + clan.getDescription()));
        player.sendMessage(ColorUtil.color(" &7Líder: &f" + leaderName));
        player.sendMessage(ColorUtil.color(" &7Membros: &a" + clan.getMembersCount() + "/" + plugin.getBankUpgradeManager().getMaxMembers(clan)));
        player.sendMessage(ColorUtil.color(" &7Banco: &b" + clan.getBank().getTp() + " TP &8| &a$" + String.format(Locale.US, "%.2f", clan.getBank().getMoney())));
        player.sendMessage(ColorUtil.color(" &7Guerra (Temporada): &#FF5555" + clan.getSeasonWarPoints() + " pts"));
        player.sendMessage(ColorUtil.color(" &7Pontos: &#FFAA00" + clan.getStats().getPoints() + " &8| &7KDR: &#55FFFF" + clan.getStats().getFormattedKdr()));
        player.sendMessage(ColorUtil.color(" &7Duelos: &#55FF55" + clan.getStats().getDuelWins() + "V &7/ &#FF5555" + clan.getStats().getDuelLosses() + "D"));
        player.sendMessage(ColorUtil.color(" &7Guerras: &#55FF55" + clan.getStats().getWarWins() + "V &7/ &#FF5555" + clan.getStats().getWarLosses() + "D"));
        player.sendMessage(ColorUtil.color(" &7Fundado em: &e" + TimeUtil.formatDate(clan.getCreatedAt())));
        player.sendMessage(ColorUtil.color("&8&m----------------------------------------"));
    }

    private void handleInvite(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.INVITE)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan convidar <jogador>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.player-not-found", "")));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtil.color(prefix + "&cVocê não pode convidar a si mesmo."));
            return;
        }

        Clan targetClan = plugin.getClanManager().getClanByPlayer(target);
        if (targetClan != null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.target-has-clan", "")
                    .replace("{player}", target.getName())));
            return;
        }

        if (plugin.getInviteManager().areInvitesDisabled(target.getUniqueId())) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.target-disabled-invites", "")
                    .replace("{player}", target.getName())));
            return;
        }

        if (plugin.getInviteManager().hasInvite(target.getUniqueId(), clan.getId())) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.already-invited", "")));
            return;
        }

        if (clan.getMembersCount() >= plugin.getBankUpgradeManager().getMaxMembers(clan)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.limit-reached", "")
                    .replace("{max}", String.valueOf(plugin.getBankUpgradeManager().getMaxMembers(clan)))));
            return;
        }

        plugin.getInviteManager().sendInvite(clan, target, player);
        int timeout = plugin.getConfig().getInt("clan.invite-timeout-seconds", 60);
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.sent", "")
                .replace("{player}", target.getName())
                .replace("{time}", String.valueOf(timeout))));
    }

    private void handleAccept(Player player, String[] args, String prefix) {
        if (plugin.getClanManager().getClanByPlayer(player) != null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.already-in-clan", "")));
            return;
        }

        Clan clan = null;
        if (args.length >= 2) {
            clan = plugin.getClanManager().getClanByTag(args[1]);
            if (clan == null) clan = plugin.getClanManager().getClanByName(args[1]);
        } else {
            List<org.vine.vclans.model.ClanInvite> pending = plugin.getInviteManager().getInvites(player.getUniqueId());
            if (pending.size() == 1) {
                clan = plugin.getClanManager().getClanById(pending.get(0).getClanId());
            } else if (pending.isEmpty()) {
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.no-pending", "")));
                return;
            } else {
                if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_BANK)) {
                    new org.vine.vclans.menu.PendingInvitesMenu(player, plugin).open();
                }
                return;
            }
        }

        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.clan-not-found", "")));
            return;
        }

        if (!plugin.getInviteManager().hasInvite(player.getUniqueId(), clan.getId())) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.no-pending", "")));
            return;
        }

        if (clan.getMembersCount() >= plugin.getBankUpgradeManager().getMaxMembers(clan)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.limit-reached", "")
                    .replace("{max}", String.valueOf(plugin.getBankUpgradeManager().getMaxMembers(clan)))));
            return;
        }

        plugin.getInviteManager().removeInvite(player.getUniqueId(), clan.getId());
        plugin.getClanManager().addMember(clan, player, ClanRole.MEMBER);

        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.accepted", "")
                .replace("{name}", clan.getName())));

        String joinedMsg = prefix + plugin.getMessages().getString("clan.member-joined", "")
                .replace("{player}", player.getName());
        clan.broadcast(joinedMsg);

        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    private void handleDecline(Player player, String[] args, String prefix) {
        Clan clan = null;
        if (args.length >= 2) {
            clan = plugin.getClanManager().getClanByTag(args[1]);
            if (clan == null) clan = plugin.getClanManager().getClanByName(args[1]);
        } else {
            List<org.vine.vclans.model.ClanInvite> pending = plugin.getInviteManager().getInvites(player.getUniqueId());
            if (pending.size() == 1) {
                clan = plugin.getClanManager().getClanById(pending.get(0).getClanId());
            } else if (pending.isEmpty()) {
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.no-pending", "")));
                return;
            } else {
                if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_BANK)) {
                    new org.vine.vclans.menu.PendingInvitesMenu(player, plugin).open();
                }
                return;
            }
        }

        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.clan-not-found", "")));
            return;
        }

        if (!plugin.getInviteManager().hasInvite(player.getUniqueId(), clan.getId())) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.no-pending", "")));
            return;
        }

        plugin.getInviteManager().removeInvite(player.getUniqueId(), clan.getId());
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("invite.declined", "")
                .replace("{name}", clan.getName())));
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
        } catch (Exception ignored) {}
    }

    private void handleLeave(Player player, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }

        if (clan.isLeader(player.getUniqueId())) {
            player.sendMessage(ColorUtil.color(prefix + "&cO líder não pode sair do clã! Transfira a liderança ou desfaça o clã no menu /clan."));
            return;
        }

        plugin.getClanManager().removeMember(clan, player.getUniqueId(), player.getName(), false);
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.member-left", "")
                .replace("{player}", player.getName())));
    }

    private void handleKick(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.KICK)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan expulsar <jogador>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        ClanMember member = clan.getMember(target.getUniqueId());
        if (member == null) {
            player.sendMessage(ColorUtil.color(prefix + "&cEste jogador não pertence ao seu clã."));
            return;
        }

        ClanRole viewerRole = clan.getRole(player.getUniqueId());
        if (!clan.isLeader(player.getUniqueId()) && (viewerRole == null || !viewerRole.canTarget(member.getRole()))) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.cannot-kick-higher", "")));
            return;
        }

        plugin.getClanManager().removeMember(clan, target.getUniqueId(), player.getName(), true);
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.member-kicked", "")
                .replace("{player}", member.getLastKnownName())
                .replace("{actor}", player.getName())));
    }

    private void handlePromote(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.PROMOTE)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan promover <jogador>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        ClanMember member = clan.getMember(target.getUniqueId());
        if (member == null) {
            player.sendMessage(ColorUtil.color(prefix + "&cEste jogador não pertence ao seu clã."));
            return;
        }

        ClanRole next = member.getRole().getNextHigher();
        if (next == null || next == ClanRole.LEADER) {
            player.sendMessage(ColorUtil.color(prefix + "&cEste membro já está no cargo máximo possível."));
            return;
        }

        ClanRole viewerRole = clan.getRole(player.getUniqueId());
        if (!clan.isLeader(player.getUniqueId()) && (viewerRole == null || viewerRole.getPriority() <= next.getPriority())) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.cannot-promote-higher", "")));
            return;
        }

        plugin.getClanManager().setRole(clan, target.getUniqueId(), next, player.getUniqueId());
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.role-promoted", "")
                .replace("{player}", member.getLastKnownName())
                .replace("{role}", next.getDefaultDisplayName())));
    }

    private void handleDemote(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.DEMOTE)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan rebaixar <jogador>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        ClanMember member = clan.getMember(target.getUniqueId());
        if (member == null) {
            player.sendMessage(ColorUtil.color(prefix + "&cEste jogador não pertence ao seu clã."));
            return;
        }

        ClanRole next = member.getRole().getNextLower();
        if (next == null) {
            player.sendMessage(ColorUtil.color(prefix + "&cEste membro já está no menor cargo do clã."));
            return;
        }

        ClanRole viewerRole = clan.getRole(player.getUniqueId());
        if (!clan.isLeader(player.getUniqueId()) && (viewerRole == null || viewerRole.getPriority() <= member.getRole().getPriority())) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.cannot-kick-higher", "")));
            return;
        }

        plugin.getClanManager().setRole(clan, target.getUniqueId(), next, player.getUniqueId());
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.role-demoted", "")
                .replace("{player}", member.getLastKnownName())
                .replace("{role}", next.getDefaultDisplayName())));
    }

    private void handleTransferLeader(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null || !clan.isLeader(player.getUniqueId())) {
            player.sendMessage(ColorUtil.color(prefix + "&cApenas o Líder supremo pode transferir a liderança."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan lider <jogador>"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        ClanMember member = clan.getMember(target.getUniqueId());
        if (member == null) {
            player.sendMessage(ColorUtil.color(prefix + "&cEste jogador não pertence ao seu clã."));
            return;
        }

        plugin.getClanManager().transferLeadership(clan, target.getUniqueId(), player.getUniqueId());
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.leader-transferred", "")
                .replace("{player}", member.getLastKnownName())));
    }

    private void handleTag(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null || !clan.hasPermission(player.getUniqueId(), ClanPermission.EDIT_INFO)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan tag <nova-tag>"));
            return;
        }

        String newTag = args[1].toUpperCase();
        int minTag = plugin.getConfig().getInt("clan.tag.min-length", 2);
        int maxTag = plugin.getConfig().getInt("clan.tag.max-length", 4);
        String tagRegex = plugin.getConfig().getString("clan.tag.regex", "^[a-zA-Z0-9]+$");

        if (newTag.length() < minTag || newTag.length() > maxTag || !newTag.matches(tagRegex)) {
            player.sendMessage(ColorUtil.color(prefix + "&cA tag deve ter entre " + minTag + " e " + maxTag + " caracteres alfanuméricos."));
            return;
        }

        if (newTag.equalsIgnoreCase(clan.getTag())) {
            player.sendMessage(ColorUtil.color(prefix + "&cO seu clã já está utilizando esta tag."));
            return;
        }

        long remainingCooldown = plugin.getClanManager().getRemainingTagChangeCooldownSeconds(clan);
        if (remainingCooldown > 0L) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.cooldown-active",
                            "&#FF5555Aguarde &e{time} &7para realizar esta ação novamente.")
                    .replace("{time}", TimeUtil.formatDuration(remainingCooldown))));
            return;
        }

        if (plugin.getClanManager().isTagTaken(newTag)) {
            player.sendMessage(ColorUtil.color(prefix + "&cEsta tag já está em uso por outro clã!"));
            return;
        }

        plugin.getClanManager().setTag(clan, newTag);
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.tag-changed", "")
                .replace("{tag}", clan.getTag())
                .replace("{color}", clan.getTagColor())));
    }

    private void handleDescription(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null || !clan.hasPermission(player.getUniqueId(), ClanPermission.EDIT_INFO)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan descricao <texto>"));
            return;
        }

        String desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        int max = plugin.getConfig().getInt("clan.description.max-length", 64);
        if (desc.length() > max) {
            desc = desc.substring(0, max);
        }

        plugin.getClanManager().setDescription(clan, desc);
        player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("clan.description-changed", "")
                .replace("{description}", desc)));
    }

    private void handleDuel(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("aceitar")) {
            Clan opponent = plugin.getClanManager().getClanByTag(args[2]);
            if (opponent == null) opponent = plugin.getClanManager().getClanByName(args[2]);
            if (opponent != null) {
                plugin.getDuelManager().acceptChallenge(clan, opponent, player);
            } else {
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.clan-not-found", "")));
            }
            return;
        }

        if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_COMBAT)) {
            new org.vine.vclans.menu.DuelMenu(player, plugin, clan).open();
        }
    }

    private void handleDirectChallenge(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null || !clan.hasPermission(player.getUniqueId(), ClanPermission.DUEL)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan desafiar <tag> [2|3|4|5] (ex: /clan desafiar {tag} 2 para 2v2)"));
            return;
        }

        Clan enemy = plugin.getClanManager().getClanByTag(args[1]);
        if (enemy == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.clan-not-found", "")));
            return;
        }

        if (enemy.getId().equals(clan.getId())) {
            player.sendMessage(ColorUtil.color(prefix + "&cVocê não pode desafiar seu próprio clã para um duelo!"));
            return;
        }

        int teamSize;
        if (args.length >= 3) {
            try {
                String clean = args[2].toLowerCase().replace("v", "").replace("x", "");
                if (clean.length() > 1 && clean.charAt(0) == clean.charAt(1)) {
                    clean = String.valueOf(clean.charAt(0));
                }
                teamSize = Integer.parseInt(clean);
            } catch (Exception e) {
                player.sendMessage(ColorUtil.color(prefix + "&cTamanho de duelo inválido! Escolha entre 2 e 5 (ex: 2 para 2v2, 3 para 3v3)."));
                return;
            }
            if (teamSize < 2 || teamSize > 5) {
                player.sendMessage(ColorUtil.color(prefix + "&cO duelo deve ser entre 2v2 e 5v5 (mínimo 2, máximo 5 jogadores por time)!"));
                return;
            }
        } else {
            int availA = clan.getOnlinePlayers().size();
            int availB = enemy.getOnlinePlayers().size();
            int maxPoss = Math.min(availA, availB);
            if (maxPoss < 2) {
                player.sendMessage(ColorUtil.color(prefix + "&cSão necessários no mínimo 2 jogadores online de cada clã para iniciar um duelo (2v2)!"));
                return;
            }
            teamSize = Math.min(5, maxPoss);
        }

        var arena = plugin.getArenaManager().getAvailableArena();
        if (arena == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("duel.no-arenas", "&cNenhuma arena disponível no momento.")));
            return;
        }

        plugin.getDuelManager().sendChallenge(clan, enemy, arena.getName(), player, teamSize);
    }

    private void handleAlliance(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.ALLIANCE)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("convidar")) {
            Clan target = plugin.getClanManager().getClanByTag(args[2]);
            if (target == null) {
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.clan-not-found", "")));
                return;
            }
            if (target.getId().equals(clan.getId())) {
                player.sendMessage(ColorUtil.color(prefix + "&cVocê não pode formar aliança consigo mesmo."));
                return;
            }

            int max = plugin.getConfig().getInt("alliances.settings.max-alliances-per-clan", 3);
            if (clan.getAllies().size() >= max) {
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("alliance.max-allies", "")));
                return;
            }

            clan.getAllies().add(target.getId());
            target.getAllies().add(clan.getId());

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getRepository().saveAllies(clan.getId(), clan.getAllies());
                plugin.getRepository().saveAllies(target.getId(), target.getAllies());
            });

            String formed = plugin.getMessages().getString("alliance.formed", "")
                    .replace("{clanA}", clan.getName())
                    .replace("{clanB}", target.getName());
            clan.broadcast(prefix + formed);
            target.broadcast(prefix + formed);
            return;
        }

        if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_COMBAT)) {
            new org.vine.vclans.menu.AlliancesMenu(player, plugin, clan).open();
        }
    }

    private void handleWar(Player player, String[] args, String prefix) {
        if (args.length >= 2 && (args[1].equalsIgnoreCase("top")
                || args[1].equalsIgnoreCase("ranking")
                || args[1].equalsIgnoreCase("pontos"))) {
            var warMgr = plugin.getSeasonWarManager();
            List<Clan> top = warMgr.getTopSeasonClans(10);
            player.sendMessage(ColorUtil.color("&8&m----------------------------------------"));
            player.sendMessage(ColorUtil.color(" &#FF5555&lGUERRA DE CLÃS (TEMPORADA) "
                    + (warMgr.isDoublePointsActive() ? "&#55FF55[2X ATIVO]" : "")));
            if (top.isEmpty()) {
                player.sendMessage(ColorUtil.color(" &7Nenhum clã pontuou nesta temporada."));
            } else {
                for (int i = 0; i < top.size(); i++) {
                    Clan c = top.get(i);
                    player.sendMessage(ColorUtil.color(" &e#" + (i + 1) + " &f" + c.getName()
                            + " &8[" + c.getTagColor() + c.getTag() + "&8] &7- &#FFAA00" + c.getSeasonWarPoints() + " pts"));
                }
            }
            player.sendMessage(ColorUtil.color("&8&m----------------------------------------"));
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.WAR)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("declarar")) {
            Clan target = plugin.getClanManager().getClanByTag(args[2]);
            if (target == null) {
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.clan-not-found", "")));
                return;
            }
            if (target.getId().equals(clan.getId())) {
                player.sendMessage(ColorUtil.color(prefix + "&cVocê não pode guerrear contra seu próprio clã."));
                return;
            }
            if (clan.isAlly(target.getId())) {
                player.sendMessage(ColorUtil.color(prefix + "&cVocê não pode declarar guerra a um clã aliado!"));
                return;
            }

            boolean ok = plugin.getWarManager().declareWar(clan, target);
            if (!ok) {
                player.sendMessage(ColorUtil.color(prefix + "&cUm dos clãs já está envolvido em guerra ativa."));
            }
            return;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("aceitar")) {
            Clan target = plugin.getClanManager().getClanByTag(args[2]);
            if (target != null) {
                plugin.getWarManager().acceptWar(clan, target);
            }
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("render-se")) {
            if (plugin.getWarManager().isAtWar(clan.getId())) {
                plugin.getWarManager().surrender(clan);
            } else {
                player.sendMessage(ColorUtil.color(prefix + "&cSeu clã não está em nenhuma guerra ativa."));
            }
            return;
        }

        player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan guerra [top|declarar|aceitar|render-se] <tag>"));
    }

    private void handleBank(Player player, String[] args, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }

        if (args.length == 1) {
            plugin.getBankUpgradeManager().openClanBankUi(player);
            return;
        }

        BankUpgradeManager mgr = plugin.getBankUpgradeManager();
        String action = args[1].toLowerCase();

        if (action.equals("depositar") || action.equals("deposit")) {
            if (args.length < 4) {
                player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan banco depositar <tp|money> <quantia>"));
                return;
            }
            String type = args[2].toLowerCase();
            if (type.equals("tp")) {
                long amount;
                try {
                    amount = Long.parseLong(args[3]);
                } catch (NumberFormatException ex) {
                    player.sendMessage(ColorUtil.color(prefix + "&cInforme um valor inteiro válido de TP."));
                    return;
                }
                TransactionResult res = mgr.depositTp(player, amount);
                player.sendMessage(mgr.formatFeedback(res));
                return;
            }
            if (type.equals("money") || type.equals("dinheiro")) {
                double amount;
                try {
                    amount = Double.parseDouble(args[3].replace(",", "."));
                } catch (NumberFormatException ex) {
                    player.sendMessage(ColorUtil.color(prefix + "&cInforme um valor numérico válido de Money."));
                    return;
                }
                TransactionResult res = mgr.depositMoney(player, amount);
                player.sendMessage(mgr.formatFeedback(res));
                return;
            }
            player.sendMessage(ColorUtil.color(prefix + "&cTipo inválido! Use &e/clan banco depositar <tp|money> <quantia>"));
            return;
        }

        if (action.equals("upar") || action.equals("upgrade")) {
            if (args.length < 3) {
                player.sendMessage(ColorUtil.color(prefix + "&cUso: /clan banco upar <tp|money|membros|guerra>"));
                return;
            }
            UpgradeType type = UpgradeType.fromKey(args[2]);
            if (type == null) {
                player.sendMessage(ColorUtil.color(prefix + "&cUpgrade inválido! Opções: &etp, money, membros, guerra"));
                return;
            }
            TransactionResult res = mgr.buyUpgrade(player, type);
            player.sendMessage(mgr.formatFeedback(res));
            return;
        }

        plugin.getBankUpgradeManager().openClanBankUi(player);
    }

    private void handleAdmin(Player player, String[] args, String prefix) {
        if (!player.hasPermission("vclans.admin") && !player.hasPermission("eclypseclans.admin")) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("top")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("set")) {
                plugin.getTopDisplayManager().setLocation(player.getLocation());
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("admin.top-set", "")));
                return;
            }
            if (args.length >= 3 && args[2].equalsIgnoreCase("reload")) {
                plugin.getTopDisplayManager().spawnOrUpdateDisplay();
                player.sendMessage(ColorUtil.color(prefix + "&aPainel do Top Clãs recarregado!"));
                return;
            }
            if (args.length >= 3 && (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("clear"))) {
                int purged = plugin.getTopDisplayManager().purgeDisplaysNearby(player.getLocation(), 64);
                plugin.getTopDisplayManager().removeDisplay();
                player.sendMessage(ColorUtil.color(prefix + "&aPainel do Top Clãs e " + purged + " holograma(s) antigos removidos com sucesso!"));
                return;
            }
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("arena")) {
            if (args.length >= 4 && args[2].equalsIgnoreCase("criar")) {
                plugin.getArenaManager().createArena(args[3]);
                player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("admin.arena-created", "").replace("{name}", args[3])));
                return;
            }
            if (args.length >= 4 && args[2].equalsIgnoreCase("setspawn1")) {
                var arena = plugin.getArenaManager().getArena(args[3]);
                if (arena != null) {
                    arena.setSpawn1(player.getLocation());
                    plugin.getArenaManager().saveArenas();
                    player.sendMessage(ColorUtil.color(prefix + "&aSpawn 1 da arena &e" + args[3] + " &adefinido! (Onde TODOS os membros do Clã A nascem no duelo)"));
                } else {
                    player.sendMessage(ColorUtil.color(prefix + "&cArena não encontrada com o nome: " + args[3]));
                }
                return;
            }
            if (args.length >= 4 && args[2].equalsIgnoreCase("setspawn2")) {
                var arena = plugin.getArenaManager().getArena(args[3]);
                if (arena != null) {
                    arena.setSpawn2(player.getLocation());
                    plugin.getArenaManager().saveArenas();
                    player.sendMessage(ColorUtil.color(prefix + "&aSpawn 2 da arena &e" + args[3] + " &adefinido! (Onde TODOS os membros do Clã B nascem no duelo)"));
                } else {
                    player.sendMessage(ColorUtil.color(prefix + "&cArena não encontrada com o nome: " + args[3]));
                }
                return;
            }
            if (args.length >= 4 && args[2].equalsIgnoreCase("setexit")) {
                var arena = plugin.getArenaManager().getArena(args[3]);
                if (arena != null) {
                    arena.setExitLocation(player.getLocation());
                    plugin.getArenaManager().saveArenas();
                    player.sendMessage(ColorUtil.color(prefix + "&aPonto de saída da arena &e" + args[3] + " &adefinido com sucesso!"));
                } else {
                    player.sendMessage(ColorUtil.color(prefix + "&cArena não encontrada com o nome: " + args[3]));
                }
                return;
            }
            if (args.length >= 4 && (args[2].equalsIgnoreCase("deletar") || args[2].equalsIgnoreCase("remover"))) {
                boolean ok = plugin.getArenaManager().deleteArena(args[3]);
                if (ok) {
                    player.sendMessage(ColorUtil.color(prefix + "&aArena &e" + args[3] + " &aremovida com sucesso!"));
                } else {
                    player.sendMessage(ColorUtil.color(prefix + "&cArena não encontrada: " + args[3]));
                }
                return;
            }
            if (args.length >= 3 && args[2].equalsIgnoreCase("listar")) {
                var arenas = plugin.getArenaManager().getArenas();
                player.sendMessage(ColorUtil.color(prefix + "&6&lArenas de Duelo Cadastradas (" + arenas.size() + "):"));
                for (var a : arenas) {
                    String status = a.isConfigured() ? "&a[CONFIGURADA]" : "&c[INCOMPLETA]";
                    player.sendMessage(ColorUtil.color("&8» &e" + a.getName() + " " + status + " &7(Spawn1: " + (a.getSpawn1() != null ? "&a✔" : "&c✘") + " &7| Spawn2: " + (a.getSpawn2() != null ? "&a✔" : "&c✘") + " &7| Saída: " + (a.getExitLocation() != null ? "&a✔" : "&c✘") + "&7)"));
                }
                return;
            }

            player.sendMessage(ColorUtil.color(prefix + "&6Gerenciamento de Arenas de Duelo:"));
            player.sendMessage(ColorUtil.color("&e/clan admin arena criar <nome> &7- Cria uma nova arena"));
            player.sendMessage(ColorUtil.color("&e/clan admin arena setspawn1 <nome> &7- Define o spawn de TODOS do Clã A"));
            player.sendMessage(ColorUtil.color("&e/clan admin arena setspawn2 <nome> &7- Define o spawn de TODOS do Clã B"));
            player.sendMessage(ColorUtil.color("&e/clan admin arena setexit <nome> &7- Define a saída ao morrer/terminar"));
            player.sendMessage(ColorUtil.color("&e/clan admin arena listar &7- Lista todas as arenas e status"));
            player.sendMessage(ColorUtil.color("&e/clan admin arena deletar <nome> &7- Deleta uma arena"));
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("evento")) {
            var mgr = plugin.getGlobalEventManager();
            if (args.length >= 3 && args[2].equalsIgnoreCase("setspawn")) {
                mgr.setSpawnLocation(player.getLocation());
                player.sendMessage(ColorUtil.color(prefix + "&aSpawn do Evento Global de Clãs definido com sucesso na sua posição!"));
                return;
            }
            if (args.length >= 3 && args[2].equalsIgnoreCase("setexit")) {
                mgr.setExitLocation(player.getLocation());
                player.sendMessage(ColorUtil.color(prefix + "&aPonto de saída do Evento Global definido com sucesso na sua posição!"));
                return;
            }
            if (args.length >= 3 && (args[2].equalsIgnoreCase("iniciar") || args[2].equalsIgnoreCase("start"))) {
                boolean ok = mgr.startEvent();
                if (ok) {
                    player.sendMessage(ColorUtil.color(prefix + "&aEvento Global de Clãs iniciado e anunciado com sucesso!"));
                } else {
                    player.sendMessage(ColorUtil.color(prefix + "&cConfigure o spawn antes de iniciar (/clan admin evento setspawn)!"));
                }
                return;
            }
            if (args.length >= 3 && (args[2].equalsIgnoreCase("parar") || args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("finalizar"))) {
                mgr.stopEvent();
                player.sendMessage(ColorUtil.color(prefix + "&cEvento Global de Clãs finalizado com sucesso!"));
                return;
            }
            if (args.length >= 3 && args[2].equalsIgnoreCase("status")) {
                player.sendMessage(ColorUtil.color(prefix + "&6Status do Evento Global: " + (mgr.isActive() ? "&aATIVO" : "&cINATIVO")));
                player.sendMessage(ColorUtil.color("&7Guerreiros vivos: &a" + mgr.getParticipantsCount() + " &8| &7Eliminados: &c" + mgr.getEliminatedCount()));
                return;
            }
            if (args.length >= 3 && args[2].equalsIgnoreCase("reload")) {
                mgr.loadConfig();
                player.sendMessage(ColorUtil.color(prefix + "&aConfigurações de eventoglobal.yml recarregadas com sucesso!"));
                return;
            }

            player.sendMessage(ColorUtil.color(prefix + "&6Gerenciamento do Evento Global de Clãs:"));
            player.sendMessage(ColorUtil.color("&e/clan admin evento setspawn &7- Define o spawn único do evento"));
            player.sendMessage(ColorUtil.color("&e/clan admin evento setexit &7- Define a saída do evento"));
            player.sendMessage(ColorUtil.color("&e/clan admin evento iniciar &7- Inicia o evento e avisa no chat"));
            player.sendMessage(ColorUtil.color("&e/clan admin evento parar &7- Finaliza o evento e retira todos"));
            player.sendMessage(ColorUtil.color("&e/clan admin evento status &7- Ver participantes"));
            player.sendMessage(ColorUtil.color("&e/clan admin evento reload &7- Recarrega eventoglobal.yml"));
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("guerra2x")) {
            boolean active = plugin.getSeasonWarManager().toggleManualDoublePoints();
            player.sendMessage(ColorUtil.color(prefix + "&#FFAA00Bônus 2x da Guerra agora está: " + (active ? "&#55FF55ATIVADO" : "&cDESATIVADO")));
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("encerrartemporada")) {
            plugin.getSeasonWarManager().endSeasonAndReward();
            player.sendMessage(ColorUtil.color(prefix + "&#55FF55Temporada de Guerra encerrada e premiada com sucesso!"));
            return;
        }

        if (!plugin.getBankUpgradeManager().openClanModUi(player, BankUpgradeManager.TAB_ADMIN)) {
            new AdminMenu(player, plugin, 0).open();
        }
    }

    private void handleFriendlyFireToggle(Player player, String prefix) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return;
        }

        boolean allowToggle = plugin.getConfig().getBoolean("friendly-fire.allow-clan-toggle", true);
        if (!allowToggle) {
            player.sendMessage(ColorUtil.color(prefix + "&cO fogo amigo está bloqueado globalmente pelo servidor."));
            return;
        }

        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.SETTINGS)) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.no-permission", "")));
            return;
        }

        boolean newFF = !clan.getSettings().isFriendlyFire();
        clan.getSettings().setFriendlyFire(newFF);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveClan(clan));
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newFF ? 0.8f : 1.5f);
        } catch (Exception ignored) {}

        if (newFF) {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString(
                    "clan.friendly-fire-enabled", "&#FF5555Fogo amigo do clã foi ATIVADO! Membros agora podem se ferir em combate.")));
            clan.broadcast(plugin.getMessages().getString(
                    "clan.friendly-fire-broadcast-enabled", "&#FF5555[Aviso de Clã] O fogo amigo foi ATIVADO por &e{player}&FF5555!")
                    .replace("{player}", player.getName()));
        } else {
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString(
                    "clan.friendly-fire-disabled", "&#55FF55Fogo amigo do clã foi DESATIVADO! Membros agora estão totalmente protegidos.")));
            clan.broadcast(plugin.getMessages().getString(
                    "clan.friendly-fire-broadcast-disabled", "&#55FF55[Aviso de Clã] O fogo amigo foi DESATIVADO por &e{player}&55FF55!")
                    .replace("{player}", player.getName()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = Arrays.asList("criar", "info", "banco", "upgrade", "config", "convidar", "aceitar", "recusar", "sair", "expulsar", "promover", "rebaixar", "lider", "tag", "descricao", "top", "duelo", "desafiar", "alianca", "guerra", "convites", "ff", "fogoamigo", "chat", "evento", "menu");
            return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("convidar")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("aceitar") || sub.equals("recusar") || sub.equals("info") || sub.equals("desafiar")) {
                return plugin.getClanManager().getAllClans().stream().map(Clan::getTag)
                        .filter(t -> t.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("banco") || sub.equals("bank")) {
                return Arrays.asList("depositar", "upar").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("duelo")) {
                return Arrays.asList("aceitar");
            }
            if (sub.equals("evento")) {
                return Arrays.asList("clans", "sair", "status");
            }
            if (sub.equals("alianca")) {
                return Arrays.asList("convidar", "remover");
            }
            if (sub.equals("guerra")) {
                return Arrays.asList("top", "declarar", "aceitar", "render-se");
            }
            if (sub.equals("admin")) {
                return Arrays.asList("top", "arena", "evento", "guerra2x", "encerrartemporada");
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("banco") || sub.equals("bank")) {
                if (args[1].equalsIgnoreCase("depositar") || args[1].equalsIgnoreCase("deposit")) {
                    return Arrays.asList("tp", "money").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                }
                if (args[1].equalsIgnoreCase("upar") || args[1].equalsIgnoreCase("upgrade")) {
                    return Arrays.asList("tp", "money", "membros", "guerra").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                }
            }
            if (sub.equals("desafiar")) {
                return Arrays.asList("2", "3", "4", "5");
            }
            if (sub.equals("admin") && args[1].equalsIgnoreCase("arena")) {
                return Arrays.asList("criar", "setspawn1", "setspawn2", "setexit", "listar", "deletar")
                        .stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("admin") && args[1].equalsIgnoreCase("evento")) {
                return Arrays.asList("setspawn", "setexit", "iniciar", "parar", "status", "reload")
                        .stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}

