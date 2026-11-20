package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class InvitePlayerMenu extends Menu {

    private final VClans plugin;
    private final Clan clan;
    private final int page;

    public InvitePlayerMenu(Player player, VClans plugin, Clan clan, int page) {
        super(player, "&#55FF55&lCONVIDAR GUERREIRO &8» &7Página " + (page + 1), 54);
        this.plugin = plugin;
        this.clan = clan;
        this.page = page;
    }

    @Override
    public void init() {
        for (int i = 45; i < 54; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Filtrar jogadores online elegíveis (não pertencem ao clã e não desativaram convites)
        List<Player> eligible = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(player.getUniqueId())) continue;
            Clan targetClan = plugin.getClanManager().getClanByPlayer(target);
            if (targetClan != null && targetClan.getId().equals(clan.getId())) continue;
            eligible.add(target);
        }

        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, eligible.size());

        for (int i = startIndex; i < endIndex; i++) {
            Player target = eligible.get(i);
            Clan targetClan = plugin.getClanManager().getClanByPlayer(target);
            boolean alreadyInvited = plugin.getInviteManager().hasInvite(target.getUniqueId(), clan.getId());
            boolean disabled = plugin.getInviteManager().areInvitesDisabled(target.getUniqueId());

            ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(target.getName())
                    .name("&#55FFFF" + target.getName())
                    .lore("&7Status: &#55FF55Online",
                          "&7Clã Atual: " + (targetClan != null ? targetClan.getFormattedTag() : "&7Nenhum"),
                          "");

            if (disabled) {
                builder.addLoreLine("&#FF5555✖ Jogador desativou convites");
            } else if (alreadyInvited) {
                builder.addLoreLine("&#FFAA00⌛ Convite pendente já enviado");
            } else {
                builder.addLoreLine("&#55FF55▶ Clique para enviar um convite!");
            }

            int slot = i - startIndex;
            setItem(slot, builder.build(), e -> {
                if (disabled) {
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            plugin.getMessages().getString("invite.target-disabled-invites", "")
                                    .replace("{player}", target.getName())));
                    return;
                }
                if (alreadyInvited) {
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            plugin.getMessages().getString("invite.already-invited", "")));
                    return;
                }
                if (clan.getMembersCount() >= plugin.getConfig().getInt("clan.max-members", 20)) {
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            plugin.getMessages().getString("clan.limit-reached", "")));
                    return;
                }

                plugin.getInviteManager().sendInvite(clan, target, player);
                int timeout = plugin.getConfig().getInt("clan.invite-timeout-seconds", 60);
                player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                        plugin.getMessages().getString("invite.sent", "")
                                .replace("{player}", target.getName())
                                .replace("{time}", String.valueOf(timeout))));

                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                } catch (Exception ignored) {}

                open(); // Atualizar estado
            });
        }

        // Navegação
        if (page > 0) {
            setItem(48, new ItemBuilder(Material.ARROW)
                    .name("&ePágina Anterior")
                    .build(), e -> new InvitePlayerMenu(player, plugin, clan, page - 1).open());
        }

        setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&cVoltar")
                .lore("&7Retornar ao menu principal.")
                .build(), e -> new MainMenu(player, plugin).open());

        if (endIndex < eligible.size()) {
            setItem(50, new ItemBuilder(Material.ARROW)
                    .name("&ePróxima Página")
                    .build(), e -> new InvitePlayerMenu(player, plugin, clan, page + 1).open());
        }
    }
}

