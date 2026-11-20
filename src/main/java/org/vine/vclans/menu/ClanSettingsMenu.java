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

public class ClanSettingsMenu extends Menu {

    private final VClans plugin;
    private final Clan clan;

    public ClanSettingsMenu(Player player, VClans plugin, Clan clan) {
        super(player, "&#55FFFF&lCONFIGURAÇÕES &8» &e" + clan.getName(), 36);
        this.plugin = plugin;
        this.clan = clan;
    }

    @Override
    public void init() {
        for (int i = 0; i < size; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        boolean canManage = clan.hasPermission(player.getUniqueId(), ClanPermission.SETTINGS);

        // Alterar Cor da Tag
        setItem(10, new ItemBuilder(Material.NAME_TAG)
                .name("&#FFAA00&lCor da Tag")
                .lore("&7Personalize a tonalidade da tag",
                      "&7do seu clã no chat e scoreboard.",
                      "",
                      "&7Cor atual: " + clan.getTagColor() + "■ &8[" + clan.getTagColor() + clan.getTag() + "&8]",
                      "",
                      canManage ? "&eClique para abrir a paleta de cores!" : "&cVocê não tem permissão para alterar.")
                .build(), e -> {
            if (canManage) {
                new TagColorMenu(player, plugin, clan).open();
            }
        });

        // Modo de Entrada (Aberto ou Convite)
        boolean isOpen = clan.getSettings().isOpenJoin();
        setItem(12, new ItemBuilder(isOpen ? Material.OAK_DOOR : Material.IRON_DOOR)
                .name("&#55FF55&lModo de Entrada")
                .lore("&7Define se novos guerreiros precisam",
                      "&7de convite para ingressar no clã.",
                      "",
                      "&7Status: " + (isOpen ? "&#55FF55Aberto ao Público" : "&#FFAA00Apenas por Convite"),
                      "",
                      canManage ? "&eClique para alternar o modo!" : "&cVocê não tem permissão.")
                .build(), e -> {
            if (canManage) {
                clan.getSettings().setOpenJoin(!isOpen);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveClan(clan));
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Exception ignored) {}
                open();
            }
        });

        // Fogo Amigo Interno
        boolean allowToggle = plugin.getConfig().getBoolean("friendly-fire.allow-clan-toggle", true);
        boolean ff = clan.getSettings().isFriendlyFire();

        ItemBuilder ffItem;
        if (ff) {
            ffItem = new ItemBuilder(Material.NETHERITE_SWORD)
                    .name("&#FF5555&lFogo Amigo: &c&lATIVADO")
                    .lore("&7Permite que guerreiros do mesmo",
                          "&7clã causem dano uns aos outros.",
                          "",
                          "&7Status: &#FF5555&lAtivado (Podem se ferir)",
                          "",
                          (allowToggle && canManage) ? "&e▶ Clique para DESATIVAR (Proteger membros)!" :
                                  (!allowToggle ? "&cBloqueado globalmente pelo servidor." : "&cVocê não tem permissão para alterar."))
                    .glow();
        } else {
            ffItem = new ItemBuilder(Material.SHIELD)
                    .name("&#55FF55&lFogo Amigo: &a&lDESATIVADO")
                    .lore("&7Guerreiros do mesmo clã estão",
                          "&7protegidos contra qualquer dano aliado.",
                          "",
                          "&7Status: &#55FF55&lDesativado (Proteção Total)",
                          "",
                          (allowToggle && canManage) ? "&e▶ Clique para ATIVAR (Permitir dano)!" :
                                  (!allowToggle ? "&cBloqueado globalmente pelo servidor." : "&cVocê não tem permissão para alterar."));
        }

        setItem(14, ffItem.build(), e -> {
            if (allowToggle && canManage) {
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

        // Notificações de Atividade
        boolean notif = clan.getSettings().isNotifications();
        setItem(16, new ItemBuilder(Material.BELL)
                .name("&#FFAA00&lNotificações do Clã")
                .lore("&7Avisos de entrada, saída e duelos",
                      "&7enviados para os membros online.",
                      "",
                      "&7Status: " + (notif ? "&#55FF55Ativadas" : "&#FF5555Desativadas"),
                      "",
                      canManage ? "&eClique para alternar!" : "&cVocê não tem permissão.")
                .build(), e -> {
            if (canManage) {
                clan.getSettings().setNotifications(!notif);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveClan(clan));
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Exception ignored) {}
                open();
            }
        });

        // Botão Voltar
        setItem(31, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .lore("&7Retornar ao menu principal.")
                .build(), e -> new MainMenu(player, plugin).open());
    }
}

