package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.vine.vclans.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

public class MainMenu extends Menu {

    private final VClans plugin;

    public MainMenu(Player player, VClans plugin) {
        super(player, "&#FF5555&lECLYPSE CLANS &8» &7Menu Principal", 45);
        this.plugin = plugin;
    }

    @Override
    public void init() {
        for (int i = 0; i < size; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player);

        if (clan == null) {
            initNoClanMenu();
        } else {
            initHasClanMenu(clan);
        }
    }

    private void initNoClanMenu() {
        // Aviso de Expulsão se o jogador foi expulso recentemente
        var kickNotice = plugin.getClanManager().getKickNotice(player.getUniqueId());
        if (kickNotice != null) {
            setItem(13, new ItemBuilder(Material.BARRIER)
                    .name("&#FF5555&l⚠ VOCÊ FOI EXPULSO DO CLÃ!")
                    .lore("&7Você foi removido das fileiras do clã:",
                          " &8» &#FFAA00" + kickNotice.getClanName() + " &8[" + kickNotice.getTagColor() + kickNotice.getClanTag() + "&8]",
                          "",
                          "&7Expulso por: &e" + kickNotice.getActorName(),
                          "&7Data: &e" + TimeUtil.formatDate(kickNotice.getKickedAt()),
                          "",
                          "&cVocê não pertence mais a este clã.",
                          "&#55FF55▶ Clique aqui para dispensar este aviso.")
                    .glow()
                    .build(), e -> {
                plugin.getClanManager().dismissKickNotice(player.getUniqueId());
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Exception ignored) {}
                open();
            });
        }

        // 1. Criar Clã
        double cost = plugin.getConfig().getDouble("clan.creation-cost", 0.0);
        ItemBuilder createBuilder = new ItemBuilder(Material.ANVIL)
                .name("&#55FF55&lFundar um Novo Clã")
                .lore("&7Crie seu próprio legado na Rede Eclypse,",
                      "&7reúna guerreiros e conquiste o topo!",
                      "",
                      (cost > 0 ? "&7Custo de criação: &#FFAA00" + cost + " moedas" : "&7Custo: &#55FF55Gratuito"),
                      "",
                      "&#55FF55▶ Clique para criar seu clã!");

        setItem(20, createBuilder.build(), e -> {
            player.closeInventory();
            player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                    "&ePara fundar um novo clã, digite no chat: &f/clan criar <nome> <tag>"));
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            } catch (Exception ignored) {}
        });

        // 2. Convites Recebidos
        int pendingCount = plugin.getInviteManager().getInvites(player.getUniqueId()).size();
        ItemBuilder invitesBuilder = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&#FFAA00&lConvites Recebidos")
                .lore("&7Veja os convites de clãs que",
                      "&7desejam recrutar você para a batalha.",
                      "",
                      "&7Convites pendentes: &e" + pendingCount,
                      "",
                      "&e▶ Clique para gerenciar seus convites!");

        if (pendingCount > 0) invitesBuilder.glow();

        setItem(22, invitesBuilder.build(), e -> new PendingInvitesMenu(player, plugin).open());

        // 3. Ver Rankings
        setItem(24, new ItemBuilder(Material.NETHER_STAR)
                .name("&#55FFFF&lRanking dos Clãs")
                .lore("&7Consulte os clãs mais temidos e",
                      "&7poderosos de toda a Rede Eclypse.",
                      "",
                      "&e▶ Clique para abrir o Hall da Fama!")
                .build(), e -> new RankingMenu(player, plugin, RankingMenu.RankingType.POINTS).open());

        // 4. Painel Admin (se tiver permissão)
        if (player.hasPermission("vclans.admin") || player.hasPermission("eclypseclans.admin")) {
            setItem(44, new ItemBuilder(Material.COMMAND_BLOCK)
                    .name("&#FF5555&lPainel Administrativo")
                    .lore("&7Acesso restrito para a Staff.")
                    .build(), e -> new AdminMenu(player, plugin, 0).open());
        }
    }

    private void initHasClanMenu(Clan clan) {
        var member = clan.getMember(player.getUniqueId());
        ClanRole role = member != null ? member.getRole() : ClanRole.MEMBER;
        boolean isLeader = clan.isLeader(player.getUniqueId());
        var leaderMember = clan.getMember(clan.getLeaderUuid());
        String leaderName = leaderMember != null ? leaderMember.getLastKnownName() : "Desconhecido";

        // Slot 13: Informações Centrais do Clã
        setItem(13, new ItemBuilder(Material.NETHERITE_CHESTPLATE)
                .name("&#FFAA00&l" + clan.getName() + " &8[" + clan.getTagColor() + clan.getTag() + "&8]")
                .lore("&7Descrição: &f" + clan.getDescription(),
                      "",
                      "&7Líder: &f" + leaderName,
                      "&7Seu Cargo: " + role.getDefaultDisplayName(),
                      "&7Membros: &a" + clan.getMembersCount() + "/" + plugin.getConfig().getInt("clan.max-members", 20),
                      "&7Pontos: &#FFAA00" + clan.getStats().getPoints(),
                      "&7KDR: &#55FFFF" + clan.getStats().getFormattedKdr(),
                      "&7Fundado em: &e" + TimeUtil.formatDate(clan.getCreatedAt()))
                .glow()
                .build());

        // Slot 19: Lista de Membros
        setItem(19, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&#55FFFF&lLista de Membros")
                .lore("&7Visualize todos os membros,",
                      "&7seus cargos e status online.",
                      "",
                      "&e▶ Clique para visualizar!")
                .build(), e -> new MembersMenu(player, plugin, clan, 0).open());

        // Slot 20: Convidar Guerreiro (se tiver permissão)
        boolean canInvite = clan.hasPermission(player.getUniqueId(), ClanPermission.INVITE);
        setItem(20, new ItemBuilder(Material.EMERALD)
                .name("&#55FF55&lConvidar Jogador")
                .lore("&7Envie convites para jogadores online",
                      "&7sem clã entrarem nas suas fileiras.",
                      "",
                      canInvite ? "&e▶ Clique para convidar!" : "&cVocê não tem permissão para convidar.")
                .build(), e -> {
            if (canInvite) {
                new InvitePlayerMenu(player, plugin, clan, 0).open();
            }
        });

        // Slot 21: Alianças
        setItem(21, new ItemBuilder(Material.SHIELD)
                .name("&#55FF55&lAlianças de Clã")
                .lore("&7Gerencie clãs aliados e pactos",
                      "&7de não-agressão.",
                      "",
                      "&7Alianças ativas: &a" + clan.getAllies().size(),
                      "",
                      "&e▶ Clique para gerenciar!")
                .build(), e -> new AlliancesMenu(player, plugin, clan).open());

        // Slot 22: Duelos e Guerras
        setItem(22, new ItemBuilder(Material.DIAMOND_SWORD)
                .name("&#FF5555&lDuelos de Clã")
                .lore("&7Desafie clãs rivais para batalhas",
                      "&7épicas em arenas configuradas.",
                      "",
                      "&e▶ Clique para abrir a lista de oponentes!")
                .build(), e -> new DuelMenu(player, plugin, clan).open());

        // Slot 23: Configurações do Clã
        boolean canSettings = clan.hasPermission(player.getUniqueId(), ClanPermission.SETTINGS);
        setItem(23, new ItemBuilder(Material.COMPARATOR)
                .name("&#FFAA00&lConfigurações do Clã")
                .lore("&7Altere a cor da tag, modo de entrada,",
                      "&7fogo amigo e notificações.",
                      "",
                      canSettings ? "&e▶ Clique para configurar!" : "&cVocê não tem permissão.")
                .build(), e -> {
            if (canSettings) {
                new ClanSettingsMenu(player, plugin, clan).open();
            }
        });

        // Slot 24: Fogo Amigo (Atalho Rápido)
        boolean allowToggle = plugin.getConfig().getBoolean("friendly-fire.allow-clan-toggle", true);
        boolean ff = clan.getSettings().isFriendlyFire();
        ItemBuilder ffQuickItem;
        if (ff) {
            ffQuickItem = new ItemBuilder(Material.NETHERITE_SWORD)
                    .name("&#FF5555&lFogo Amigo: &c&lATIVADO")
                    .lore("&7Permite combate entre membros do clã.",
                          "",
                          "&7Status: &#FF5555&lAtivado (Podem se ferir)",
                          "",
                          (allowToggle && canSettings) ? "&e▶ Clique para DESATIVAR (Proteger aliados)!" :
                                  (!allowToggle ? "&cBloqueado globalmente pelo servidor." : "&cVocê não tem permissão para alterar."))
                    .glow();
        } else {
            ffQuickItem = new ItemBuilder(Material.SHIELD)
                    .name("&#55FF55&lFogo Amigo: &a&lDESATIVADO")
                    .lore("&7Guerreiros do clã estão protegidos",
                          "&7contra qualquer tipo de dano aliado.",
                          "",
                          "&7Status: &#55FF55&lDesativado (Protegidos)",
                          "",
                          (allowToggle && canSettings) ? "&e▶ Clique para ATIVAR (Permitir dano)!" :
                                  (!allowToggle ? "&cBloqueado globalmente pelo servidor." : "&cVocê não tem permissão para alterar."));
        }

        setItem(24, ffQuickItem.build(), e -> {
            if (allowToggle && canSettings) {
                boolean newFF = !ff;
                clan.getSettings().setFriendlyFire(newFF);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveClan(clan));
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newFF ? 0.8f : 1.5f);
                } catch (Exception ignored) {}

                String prefix = plugin.getMessages().getString("prefix", "");
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

                open();
            }
        });

        // Slot 25: Ranking Geral
        setItem(25, new ItemBuilder(Material.NETHER_STAR)
                .name("&#55FFFF&lRanking dos Clãs")
                .lore("&7Consulte o Hall da Fama da Rede Eclypse.",
                      "",
                      "&e▶ Clique para visualizar!")
                .build(), e -> new RankingMenu(player, plugin, RankingMenu.RankingType.POINTS).open());

        // Slot 38: Sair do Clã
        if (!isLeader) {
            setItem(38, new ItemBuilder(Material.RED_BED)
                    .name("&#FF5555&lSair do Clã")
                    .lore("&7Abandone suas obrigações no clã.",
                          "",
                          "&cATENÇÃO: Você terá cooldown para entrar em outro!",
                          "&e▶ Clique para sair.")
                    .build(), e -> {
                new ConfirmationMenu(player, "SAIR do clã " + clan.getName(), () -> {
                    plugin.getClanManager().removeMember(clan, player.getUniqueId(), player.getName(), false);
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            plugin.getMessages().getString("clan.member-left", "").replace("{player}", player.getName())));
                    player.closeInventory();
                }, () -> new MainMenu(player, plugin).open()).open();
            });
        }

        // Slot 42: Desfazer Clã (Líder apenas)
        if (isLeader) {
            setItem(42, new ItemBuilder(Material.TNT)
                    .name("&#FF5555&lDesfazer o Clã")
                    .lore("&7Encerra permanentemente a existência",
                          "&7deste clã e remove todos os membros.",
                          "",
                          "&cEsta ação é IRREVERSÍVEL!",
                          "&e▶ Clique para iniciar confirmação.")
                    .build(), e -> {
                new ConfirmationMenu(player, "DESFAZER PERMANENTEMENTE o clã " + clan.getName(), () -> {
                    String name = clan.getName();
                    String tag = clan.getTag();
                    String color = clan.getTagColor();
                    plugin.getClanManager().disbandClan(clan);
                    player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                            plugin.getMessages().getString("clan.disbanded", "")
                                    .replace("{name}", name)
                                    .replace("{tag}", tag)
                                    .replace("{color}", color)));
                    player.closeInventory();
                }, () -> new MainMenu(player, plugin).open()).open();
            });
        }

        // Slot 44: Painel Admin
        if (player.hasPermission("vclans.admin") || player.hasPermission("eclypseclans.admin")) {
            setItem(44, new ItemBuilder(Material.COMMAND_BLOCK)
                    .name("&#FF5555&lPainel Administrativo")
                    .lore("&7Acesso restrito para a Staff.")
                    .build(), e -> new AdminMenu(player, plugin, 0).open());
        }
    }
}

