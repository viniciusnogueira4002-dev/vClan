package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Arena;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DuelMenu extends Menu {

    private final VClans plugin;
    private final Clan viewerClan;

    public DuelMenu(Player player, VClans plugin, Clan viewerClan) {
        super(player, "&#FF5555&lDUELO DE CLÃS &8» &7Escolha um Clã", 45);
        this.plugin = plugin;
        this.viewerClan = viewerClan;
    }

    @Override
    public void init() {
        for (int i = 36; i < 45; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        boolean canDuel = viewerClan.hasPermission(player.getUniqueId(), ClanPermission.DUEL);
        Arena availableArena = plugin.getArenaManager().getAvailableArena();

        List<Clan> enemies = new ArrayList<>();
        for (Clan other : plugin.getClanManager().getAllClans()) {
            if (other.getId().equals(viewerClan.getId())) continue;
            if (viewerClan.isAlly(other.getId())) continue;
            if (!other.getOnlinePlayers().isEmpty()) {
                enemies.add(other);
            }
        }

        if (enemies.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&#FF5555Nenhum clã oponente disponível")
                    .lore("&7Não há outros clãs com membros",
                          "&7online no momento para desafiar.")
                    .build());
        } else {
            int slot = 10;
            for (Clan enemy : enemies) {
                if (slot == 17) slot = 19;
                if (slot >= 35) break;

                boolean inDuel = plugin.getDuelManager().isDueling(enemy.getId());

                ItemBuilder ib = new ItemBuilder(Material.NETHERITE_SWORD)
                        .name("&#FF5555" + enemy.getName() + " &8[" + enemy.getTagColor() + enemy.getTag() + "&8]")
                        .lore("&7Membros online: &a" + enemy.getOnlinePlayers().size(),
                              "&7Pontos: &#FFAA00" + enemy.getStats().getPoints(),
                              "&7Vitórias em Duelo: &#55FF55" + enemy.getStats().getDuelWins(),
                              "");

                if (inDuel) {
                    ib.addLoreLine("&#FF5555⚔ Já está em um duelo ativo!");
                } else if (!canDuel) {
                    ib.addLoreLine("&cVocê não tem permissão para desafiar.");
                } else if (availableArena == null) {
                    ib.addLoreLine("&#FFAA00⌛ Nenhuma arena configurada/livre.");
                } else {
                    ib.addLoreLine("&#55FF55▶ Clique para enviar DESAFIO DE DUELO!");
                }

                setItem(slot++, ib.build(), e -> {
                    if (inDuel) {
                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                plugin.getMessages().getString("duel.already-dueling", "")));
                        return;
                    }
                    if (!canDuel) {
                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                plugin.getMessages().getString("general.no-permission", "")));
                        return;
                    }
                    if (availableArena == null) {
                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                                plugin.getMessages().getString("duel.no-arenas", "")));
                        return;
                    }

                    int availA = viewerClan.getOnlinePlayers().size();
                    int availB = enemy.getOnlinePlayers().size();
                    if (availA < 2) {
                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") + "&cSeu clã precisa de pelo menos 2 jogadores online para iniciar um duelo (mínimo 2v2)!"));
                        return;
                    }
                    if (availB < 2) {
                        player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") + "&cO clã " + enemy.getName() + " precisa de pelo menos 2 jogadores online para um duelo (mínimo 2v2)!"));
                        return;
                    }

                    int size = Math.min(5, Math.min(availA, availB));
                    boolean sent = plugin.getDuelManager().sendChallenge(viewerClan, enemy, availableArena.getName(), player, size);
                    if (sent) {
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.2f);
                        } catch (Exception ignored) {}
                        player.closeInventory();
                    }
                });
            }
        }

        // Botão Voltar
        setItem(40, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .lore("&7Retornar ao menu principal.")
                .build(), e -> new MainMenu(player, plugin).open());
    }
}

