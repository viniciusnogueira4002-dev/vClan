package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.util.ItemBuilder;
import org.vine.vclans.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MembersMenu extends Menu {

    private final VClans plugin;
    private final Clan clan;
    private final int page;

    public MembersMenu(Player player, VClans plugin, Clan clan, int page) {
        super(player, "&#55FFFF&lMEMBROS DO CLÃ &8» &7Página " + (page + 1), 54);
        this.plugin = plugin;
        this.clan = clan;
        this.page = page;
    }

    @Override
    public void init() {
        for (int i = 45; i < 54; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        List<ClanMember> members = new ArrayList<>(clan.getMembers().values());
        // Ordenar por prioridade de cargo decrescente
        members.sort(Comparator.comparingInt((ClanMember m) -> m.getRole().getPriority()).reversed()
                .thenComparing(ClanMember::getLastKnownName));

        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, members.size());

        boolean canManage = clan.hasPermission(player.getUniqueId(), ClanPermission.KICK) ||
                            clan.hasPermission(player.getUniqueId(), ClanPermission.PROMOTE);
        ClanRole viewerRole = clan.getRole(player.getUniqueId());

        for (int i = startIndex; i < endIndex; i++) {
            ClanMember member = members.get(i);
            boolean isOnline = member.isOnline();
            boolean isSelf = member.getUuid().equals(player.getUniqueId());
            boolean targetable = canManage && !isSelf && viewerRole != null && viewerRole.canTarget(member.getRole());

            ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                    .skullOwner(member.getLastKnownName())
                    .name(member.getRole().getDefaultDisplayName() + " &f" + member.getLastKnownName())
                    .lore("&7Cargo: " + member.getRole().getDefaultDisplayName(),
                          "&7Status: " + (isOnline ? "&#55FF55Online" : "&#AAAAAAOffline"),
                          "&7Entrou em: &e" + TimeUtil.formatDate(member.getJoinedAt()),
                          "&7Último acesso: &e" + TimeUtil.formatDate(member.getLastSeen()),
                          "");

            if (targetable) {
                builder.addLoreLine("&#FFAA00▶ Clique para gerenciar este membro!");
            } else if (isSelf) {
                builder.addLoreLine("&7(Você)");
            }

            int slot = i - startIndex;
            setItem(slot, builder.build(), e -> {
                if (targetable) {
                    new MemberActionMenu(player, plugin, clan, member).open();
                }
            });
        }

        // Navegação
        if (page > 0) {
            setItem(48, new ItemBuilder(Material.ARROW)
                    .name("&ePágina Anterior")
                    .build(), e -> new MembersMenu(player, plugin, clan, page - 1).open());
        }

        setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&cVoltar")
                .lore("&7Retornar ao menu principal.")
                .build(), e -> new MainMenu(player, plugin).open());

        if (endIndex < members.size()) {
            setItem(50, new ItemBuilder(Material.ARROW)
                    .name("&ePróxima Página")
                    .build(), e -> new MembersMenu(player, plugin, clan, page + 1).open());
        }
    }
}

