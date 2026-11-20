package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

public class RankingMenu extends Menu {

    public enum RankingType {
        POINTS("Pontos Totais", Material.NETHER_STAR),
        DUELS("Vitórias em Duelo", Material.DIAMOND_SWORD),
        WARS("Vitórias em Guerra", Material.FIRE_CHARGE),
        KDR("Taxa de KDR", Material.BOW);

        final String title;
        final Material icon;

        RankingType(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private final VClans plugin;
    private final RankingType type;

    public RankingMenu(Player player, VClans plugin, RankingType type) {
        super(player, "&#FFAA00&lTOP CLÃS &8» &e" + type.title, 45);
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public void init() {
        for (int i = 0; i < 9; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }
        for (int i = 36; i < 45; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Filtros no topo (Slots 1, 3, 5, 7)
        int[] filterSlots = {1, 3, 5, 7};
        RankingType[] types = RankingType.values();
        for (int idx = 0; idx < types.length; idx++) {
            RankingType rType = types[idx];
            boolean isSelected = rType == this.type;
            ItemBuilder b = new ItemBuilder(rType.icon)
                    .name((isSelected ? "&#55FF55&l" : "&#AAAAAA") + rType.title)
                    .lore("&7Clique para visualizar este ranking.");
            if (isSelected) b.glow();
            setItem(filterSlots[idx], b.build(), e -> {
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Exception ignored) {}
                new RankingMenu(player, plugin, rType).open();
            });
        }

        // Buscar lista correspondente
        List<Clan> list = switch (type) {
            case POINTS -> plugin.getClanManager().getTopClansByPoints(10);
            case DUELS -> plugin.getClanManager().getTopClansByDuels(10);
            case WARS -> plugin.getClanManager().getTopClansByWars(10);
            case KDR -> plugin.getClanManager().getTopClansByKDR(10);
        };

        int[] displaySlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};
        for (int i = 0; i < 10; i++) {
            int slot = displaySlots[i];
            int rank = i + 1;

            if (i < list.size()) {
                Clan c = list.get(i);
                Material mat = switch (rank) {
                    case 1 -> Material.GOLD_BLOCK;
                    case 2 -> Material.IRON_BLOCK;
                    case 3 -> Material.COPPER_BLOCK;
                    default -> Material.PAPER;
                };

                String medal = switch (rank) {
                    case 1 -> "&e🥇 #1 ";
                    case 2 -> "&f🥈 #2 ";
                    case 3 -> "&6🥉 #3 ";
                    default -> "&8#" + rank + " ";
                };

                var leader = c.getMember(c.getLeaderUuid());
                String leaderName = leader != null ? leader.getLastKnownName() : "Desconhecido";

                ItemBuilder ib = new ItemBuilder(mat)
                        .name(medal + c.getName() + " &8[" + c.getTagColor() + c.getTag() + "&8]")
                        .lore("&7Líder: &f" + leaderName,
                              "&7Membros: &a" + c.getMembersCount() + "/" + plugin.getConfig().getInt("clan.max-members", 20),
                              "&7Pontos: &#FFAA00" + c.getStats().getPoints(),
                              "&7KDR: &#55FFFF" + c.getStats().getFormattedKdr(),
                              "&7Duelos: &#55FF55" + c.getStats().getDuelWins() + "V &7/ &#FF5555" + c.getStats().getDuelLosses() + "D",
                              "&7Guerras: &#55FF55" + c.getStats().getWarWins() + "V &7/ &#FF5555" + c.getStats().getWarLosses() + "D");

                if (rank <= 3) ib.glow();
                setItem(slot, ib.build());
            } else {
                setItem(slot, new ItemBuilder(Material.GRAY_DYE)
                        .name("&8#" + rank + " &7Vazio")
                        .lore("&7Nenhum clã classificado ainda.")
                        .build());
            }
        }

        // Botão Voltar
        setItem(40, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .lore("&7Retornar ao menu principal.")
                .build(), e -> new MainMenu(player, plugin).open());
    }
}

