package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AdminMenu extends Menu {

    private final VClans plugin;
    private final int page;

    public AdminMenu(Player player, VClans plugin, int page) {
        super(player, "&#FF5555&lADMIN &8» &7Gerenciar Clãs (Pág. " + (page + 1) + ")", 54);
        this.plugin = plugin;
        this.page = page;
    }

    @Override
    public void init() {
        for (int i = 45; i < 54; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        List<Clan> allClans = new ArrayList<>(plugin.getClanManager().getAllClans());

        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allClans.size());

        for (int i = startIndex; i < endIndex; i++) {
            Clan c = allClans.get(i);
            var leader = c.getMember(c.getLeaderUuid());
            String leaderName = leader != null ? leader.getLastKnownName() : "Desconhecido";

            ItemBuilder ib = new ItemBuilder(Material.CHEST)
                    .name("&#FFAA00" + c.getName() + " &8[" + c.getTagColor() + c.getTag() + "&8]")
                    .lore("&7ID: &8" + c.getId().toString().substring(0, 8) + "...",
                          "&7Líder: &f" + leaderName,
                          "&7Membros: &a" + c.getMembersCount() + "/" + plugin.getBankUpgradeManager().getMaxMembers(c),
                          "&7Pontos: &#FFAA00" + c.getStats().getPoints(),
                          "",
                          "&#55FF55[BOTÃO ESQUERDO] &7Adicionar 100 pontos",
                          "&#FF5555[BOTÃO DIREITO] &7Remover clã (Forçar Exclusão)");

            int slot = i - startIndex;
            setItem(slot, ib.build(), e -> {
                if (e.isLeftClick()) {
                    plugin.getClanManager().addPoints(c, 100);
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            "&#55FF55+100 pontos concedidos ao clã &e" + c.getName()));
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    } catch (Exception ignored) {}
                    open();
                } else if (e.isRightClick()) {
                    new ConfirmationMenu(player, "EXCLUIR permanentemente o clã " + c.getName(), () -> {
                        plugin.getClanManager().disbandClan(c);
                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                plugin.getMessages().getString("admin.clan-deleted", "").replace("{clan}", c.getName())));
                        open();
                    }, () -> open()).open();
                }
            });
        }

        // Navegação
        if (page > 0) {
            setItem(48, new ItemBuilder(Material.ARROW)
                    .name("&ePágina Anterior")
                    .build(), e -> new AdminMenu(player, plugin, page - 1).open());
        }

        // Configurar Top HD no Local
        setItem(49, new ItemBuilder(Material.ARMOR_STAND)
                .name("&#FFAA00&lDefinir Painel Top HD Aqui")
                .lore("&7Posiciona o painel visual de ranking",
                      "&7em sua localização atual.",
                      "",
                      "&eClique para salvar coordenadas!")
                .build(), e -> {
            plugin.getTopDisplayManager().setLocation(player.getLocation());
            player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                    plugin.getMessages().getString("admin.top-set", "")));
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } catch (Exception ignored) {}
            player.closeInventory();
        });

        if (endIndex < allClans.size()) {
            setItem(50, new ItemBuilder(Material.ARROW)
                    .name("&ePróxima Página")
                    .build(), e -> new AdminMenu(player, plugin, page + 1).open());
        }
    }
}

