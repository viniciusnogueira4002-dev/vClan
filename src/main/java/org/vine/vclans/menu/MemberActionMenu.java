package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class MemberActionMenu extends Menu {

    private final VClans plugin;
    private final Clan clan;
    private final ClanMember target;

    public MemberActionMenu(Player player, VClans plugin, Clan clan, ClanMember target) {
        super(player, "&#FFAA00&lGERENCIAR &8» &e" + target.getLastKnownName(), 36);
        this.plugin = plugin;
        this.clan = clan;
        this.target = target;
    }

    @Override
    public void init() {
        for (int i = 0; i < size; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        ClanRole viewerRole = clan.getRole(player.getUniqueId());
        boolean isLeader = clan.isLeader(player.getUniqueId());
        boolean canPromote = clan.hasPermission(player.getUniqueId(), ClanPermission.PROMOTE);
        boolean canDemote = clan.hasPermission(player.getUniqueId(), ClanPermission.DEMOTE);
        boolean canKick = clan.hasPermission(player.getUniqueId(), ClanPermission.KICK);

        ClanRole targetRole = target.getRole();
        ClanRole higherRole = targetRole.getNextHigher();
        ClanRole lowerRole = targetRole.getNextLower();

        // 1. Promover
        boolean eligibleForPromotion = higherRole != null && higherRole != ClanRole.LEADER &&
                (isLeader || (viewerRole != null && viewerRole.getPriority() > higherRole.getPriority()));

        setItem(10, new ItemBuilder(Material.EMERALD)
                .name("&#55FF55&lPromover Membro")
                .lore("&7Cargo Atual: " + targetRole.getDefaultDisplayName(),
                      "&7Novo Cargo: " + (higherRole != null ? higherRole.getDefaultDisplayName() : "&cInatingível"),
                      "",
                      (canPromote && eligibleForPromotion) ? "&eClique para promover este guerreiro!" : "&cVocê não pode promover para este cargo.")
                .build(), e -> {
            if (canPromote && eligibleForPromotion) {
                plugin.getClanManager().setRole(clan, target.getUuid(), higherRole, player.getUniqueId());
                player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                        plugin.getMessages().getString("clan.role-promoted", "")
                                .replace("{player}", target.getLastKnownName())
                                .replace("{role}", higherRole.getDefaultDisplayName())));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } catch (Exception ignored) {}
                new MemberActionMenu(player, plugin, clan, target).open();
            }
        });

        // 2. Rebaixar
        boolean eligibleForDemotion = lowerRole != null &&
                (isLeader || (viewerRole != null && viewerRole.getPriority() > targetRole.getPriority()));

        setItem(12, new ItemBuilder(Material.REDSTONE)
                .name("&#FFAA00&lRebaixar Membro")
                .lore("&7Cargo Atual: " + targetRole.getDefaultDisplayName(),
                      "&7Novo Cargo: " + (lowerRole != null ? lowerRole.getDefaultDisplayName() : "&cMenor cargo possível"),
                      "",
                      (canDemote && eligibleForDemotion) ? "&eClique para rebaixar este membro!" : "&cVocê não pode rebaixar este membro.")
                .build(), e -> {
            if (canDemote && eligibleForDemotion) {
                plugin.getClanManager().setRole(clan, target.getUuid(), lowerRole, player.getUniqueId());
                player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                        plugin.getMessages().getString("clan.role-demoted", "")
                                .replace("{player}", target.getLastKnownName())
                                .replace("{role}", lowerRole.getDefaultDisplayName())));
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                } catch (Exception ignored) {}
                new MemberActionMenu(player, plugin, clan, target).open();
            }
        });

        // 3. Expulsar
        boolean eligibleForKick = isLeader || (viewerRole != null && viewerRole.getPriority() > targetRole.getPriority());

        setItem(14, new ItemBuilder(Material.BARRIER)
                .name("&#FF5555&lExpulsar do Clã")
                .lore("&7Remove permanentemente este jogador",
                      "&7das fileiras do seu clã.",
                      "",
                      (canKick && eligibleForKick) ? "&cClique para iniciar a expulsão!" : "&cVocê não pode expulsar este membro.")
                .build(), e -> {
            if (canKick && eligibleForKick) {
                new ConfirmationMenu(player, "Expulsar " + target.getLastKnownName() + " do clã", () -> {
                    plugin.getClanManager().removeMember(clan, target.getUuid(), player.getName(), true);
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            plugin.getMessages().getString("clan.member-kicked", "")
                                    .replace("{player}", target.getLastKnownName())
                                    .replace("{actor}", player.getName())));
                    new MembersMenu(player, plugin, clan, 0).open();
                }, () -> new MemberActionMenu(player, plugin, clan, target).open()).open();
            }
        });

        // 4. Transferir Liderança (somente o líder do clã)
        if (isLeader) {
            setItem(16, new ItemBuilder(Material.NETHER_STAR)
                    .name("&#FFAA00&lTransferir Liderança")
                    .lore("&7Passa a liderança máxima do clã",
                          "&7para este guerreiro.",
                          "",
                          "&cATENÇÃO: Você se tornará Sub-Líder!",
                          "&eClique para prosseguir com segurança.")
                    .build(), e -> {
                new ConfirmationMenu(player, "Transferir a LIDERANÇA para " + target.getLastKnownName(), () -> {
                    plugin.getClanManager().transferLeadership(clan, target.getUuid(), player.getUniqueId());
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            plugin.getMessages().getString("clan.leader-transferred", "")
                                    .replace("{player}", target.getLastKnownName())));
                    new MainMenu(player, plugin).open();
                }, () -> new MemberActionMenu(player, plugin, clan, target).open()).open();
            });
        }

        // Botão Voltar
        setItem(31, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .lore("&7Retornar à lista de membros.")
                .build(), e -> new MembersMenu(player, plugin, clan, 0).open());
    }
}

