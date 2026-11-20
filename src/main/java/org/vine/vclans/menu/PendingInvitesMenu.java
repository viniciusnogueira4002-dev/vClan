package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanInvite;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

public class PendingInvitesMenu extends Menu {

    private final VClans plugin;

    public PendingInvitesMenu(Player player, VClans plugin) {
        super(player, "&#FFAA00&lCONVITES RECEBIDOS", 36);
        this.plugin = plugin;
    }

    @Override
    public void init() {
        for (int i = 27; i < 36; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        List<ClanInvite> invites = plugin.getInviteManager().getInvites(player.getUniqueId());

        if (invites.isEmpty()) {
            setItem(13, new ItemBuilder(Material.PAPER)
                    .name("&#FF5555Nenhum convite pendente")
                    .lore("&7Você não possui convites no momento.",
                          "&7Quando um clã o convidar, ele",
                          "&7aparecerá listado aqui.")
                    .build());
        } else {
            int slot = 10;
            for (ClanInvite invite : invites) {
                if (slot == 17) break;
                Clan clan = plugin.getClanManager().getClanById(invite.getClanId());
                if (clan == null) continue;

                long remaining = invite.getRemainingSeconds();

                setItem(slot++, new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("&#FFAA00" + clan.getName() + " &8[" + clan.getTagColor() + clan.getTag() + "&8]")
                        .lore("&7Membros: &a" + clan.getMembersCount() + "/" + plugin.getBankUpgradeManager().getMaxMembers(clan),
                              "&7Tempo restante: &e" + remaining + "s",
                              "",
                              "&#55FF55[BOTÃO ESQUERDO] &7Aceitar convite",
                              "&#FF5555[BOTÃO DIREITO] &7Recusar convite")
                        .build(), e -> {
                    if (e.isLeftClick()) {
                        // Aceitar
                        if (plugin.getClanManager().getClanByPlayer(player) != null) {
                            player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                    plugin.getMessages().getString("general.already-in-clan", "")));
                            return;
                        }
                        if (clan.getMembersCount() >= plugin.getBankUpgradeManager().getMaxMembers(clan)) {
                            player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                    plugin.getMessages().getString("clan.limit-reached", "")
                                            .replace("{max}", String.valueOf(plugin.getBankUpgradeManager().getMaxMembers(clan)))));
                            return;
                        }

                        plugin.getInviteManager().removeInvite(player.getUniqueId(), clan.getId());
                        plugin.getClanManager().addMember(clan, player, ClanRole.MEMBER);

                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                plugin.getMessages().getString("invite.accepted", "")
                                        .replace("{name}", clan.getName())));
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        } catch (Exception ignored) {}
                        player.closeInventory();
                    } else if (e.isRightClick()) {
                        // Recusar
                        plugin.getInviteManager().removeInvite(player.getUniqueId(), clan.getId());
                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                plugin.getMessages().getString("invite.declined", "")
                                        .replace("{name}", clan.getName())));
                        try {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                        } catch (Exception ignored) {}
                        open();
                    }
                });
            }
        }

        // Botão Alternar Convites (Ativar/Desativar)
        boolean disabled = plugin.getInviteManager().areInvitesDisabled(player.getUniqueId());
        setItem(30, new ItemBuilder(disabled ? Material.REDSTONE_TORCH : Material.TORCH)
                .name(disabled ? "&#FF5555Convites Desativados" : "&#55FF55Convites Ativados")
                .lore("&7Clique para " + (disabled ? "reativar" : "bloquear") + " o recebimento",
                      "&7de novos convites de clãs.")
                .build(), e -> {
            boolean active = plugin.getInviteManager().toggleInvites(player.getUniqueId());
            player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                    plugin.getMessages().getString(active ? "invite.invites-toggled-on" : "invite.invites-toggled-off", "")));
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            } catch (Exception ignored) {}
            open();
        });

        // Botão Voltar
        setItem(31, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .lore("&7Retornar ao menu anterior.")
                .build(), e -> new MainMenu(player, plugin).open());
    }
}

