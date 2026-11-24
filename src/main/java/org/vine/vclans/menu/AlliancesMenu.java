package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AlliancesMenu extends Menu {

    private final VClans plugin;
    private final Clan clan;

    public AlliancesMenu(Player player, VClans plugin, Clan clan) {
        super(player, "&#55FF55&lALIANÇAS &8» &e" + clan.getName(), 36);
        this.plugin = plugin;
        this.clan = clan;
    }

    @Override
    public void init() {
        for (int i = 0; i < size; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        boolean canManage = clan.hasPermission(player.getUniqueId(), ClanPermission.ALLIANCE);
        int maxAllies = plugin.getConfig().getInt("alliances.settings.max-alliances-per-clan", 3);

        int slot = 10;
        for (UUID allyId : clan.getAllies()) {
            if (slot > 14) break;
            Clan allyClan = plugin.getClanManager().getClanById(allyId);
            if (allyClan == null) continue;

            ItemBuilder builder = new ItemBuilder(Material.SHIELD)
                    .name("&#55FF55" + allyClan.getName() + " &8[" + allyClan.getTagColor() + allyClan.getTag() + "&8]")
                    .lore("&7Membros: &a" + allyClan.getMembersCount(),
                          "&7Pontos: &#FFAA00" + allyClan.getStats().getPoints(),
                          "",
                          canManage ? "&#FF5555[CLIQUE] &7Desfazer esta aliança" : "&7Aliança ativa e protegida.");

            setItem(slot++, builder.build(), e -> {
                if (canManage) {
                    new ConfirmationMenu(player, "Desfazer aliança com " + allyClan.getName(), () -> {
                        clan.getAllies().remove(allyClan.getId());
                        allyClan.getAllies().remove(clan.getId());
                        java.util.Set<UUID> alliesSnapshotA = new java.util.HashSet<>(clan.getAllies());
                        java.util.Set<UUID> alliesSnapshotB = new java.util.HashSet<>(allyClan.getAllies());
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            plugin.getRepository().saveAllies(clan.getId(), alliesSnapshotA);
                            plugin.getRepository().saveAllies(allyClan.getId(), alliesSnapshotB);
                        });

                        String msg = plugin.getMessages().getString("prefix", "") +
                                plugin.getMessages().getString("alliance.broken", "")
                                        .replace("{clanA}", clan.getName())
                                        .replace("{clanB}", allyClan.getName());
                        clan.broadcast(msg);
                        allyClan.broadcast(msg);

                        new AlliancesMenu(player, plugin, clan).open();
                    }, () -> new AlliancesMenu(player, plugin, clan).open()).open();
                }
            });
        }

        // Se houver vagas para alianças
        if (clan.getAllies().size() < maxAllies && canManage) {
            setItem(16, new ItemBuilder(Material.WRITABLE_BOOK)
                    .name("&#55FF55&lPropor Nova Aliança")
                    .lore("&7Vagas de aliança: &a" + clan.getAllies().size() + "/" + maxAllies,
                          "",
                          "&eClique para enviar uma proposta para outro clã!")
                    .build(), e -> {
                player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                        "&ePara propor uma aliança, use: &f/clan alianca convidar <tag>"));
                player.closeInventory();
            });
        }

        // Botão Voltar
        setItem(31, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .lore("&7Retornar ao menu principal.")
                .build(), e -> new MainMenu(player, plugin).open());
    }
}

