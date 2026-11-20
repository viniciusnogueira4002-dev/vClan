package org.vine.vclans.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.vine.vclans.VClans;
import org.vine.vclans.manager.BankUpgradeManager;
import org.vine.vclans.manager.BankUpgradeManager.TransactionResult;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.model.ClanUpgrades.UpgradeType;
import org.vine.vclans.util.ItemBuilder;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Menu GUI Bukkit de fallback para o Banco Dual (TP + Money) e Upgrades do Clã.
 */
public class BankUpgradeMenu extends Menu {

    private final VClans plugin;
    private final Clan clan;

    public BankUpgradeMenu(Player player, VClans plugin, Clan clan) {
        super(player, "&#FFAA00&lBANCO & UPGRADES &8» &7" + clan.getTag(), 54);
        this.plugin = plugin;
        this.clan = clan;
    }

    @Override
    public void init() {
        for (int i = 0; i < 9; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }
        for (int i = 45; i < 54; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        BankUpgradeManager mgr = plugin.getBankUpgradeManager();
        var dmz = plugin.getDragonMineZHook();
        var vault = plugin.getVaultHook();

        long playerTp = (dmz != null && dmz.isEnabled()) ? dmz.getPlayerTp(player) : 0L;
        double playerMoney = (vault != null && vault.isEnabled()) ? vault.getBalance(player) : 0.0;
        int warRank = plugin.getSeasonWarManager().getClanRank(clan.getId());
        boolean doubleWar = plugin.getSeasonWarManager().isDoublePointsActive();

        // Slot 4: Resumo do Tesouro do Clã
        setItem(4, new ItemBuilder(Material.ENDER_CHEST)
                .name("&#FFAA00&lTesouraria do Clã &8[" + clan.getTagColor() + clan.getTag() + "&8]")
                .lore("&7Banco do Clã (Sem Saque):",
                      " &8» &bTP no Banco: &f" + formatInt(clan.getBank().getTp()),
                      " &8» &aMoney no Banco: &f$" + formatMoney(clan.getBank().getMoney()),
                      "",
                      "&7Seu Saldo Pessoal:",
                      " &8» &bSeu TP: &f" + formatInt(playerTp),
                      " &8» &aSeu Money: &f$" + formatMoney(playerMoney),
                      "",
                      "&7Guerra da Temporada:",
                      " &8» &#FF5555Pontos: &e" + formatInt(clan.getSeasonWarPoints())
                              + (warRank > 0 ? " &7(#" + warRank + ")" : ""),
                      " &8» &7Horário 2x: " + (doubleWar ? "&#55FF55ATIVO!" : "&7Desativado"))
                .glow()
                .build());

        // Botões rápidos de Depósito de TP (Slots 19, 20, 21)
        long[] tpAmounts = {1_000L, 10_000L, 100_000L};
        int[] tpSlots = {19, 20, 21};
        for (int i = 0; i < tpAmounts.length; i++) {
            long amt = tpAmounts[i];
            setItem(tpSlots[i], new ItemBuilder(Material.LAPIS_LAZULI)
                    .name("&#55FFFF&lDepositar +" + formatInt(amt) + " TP")
                    .lore("&7Transfere TP da sua ficha DMZ",
                          "&7para o banco do clã.",
                          "",
                          "&e▶ Clique para depositar!",
                          "&8Ou use: /clan banco depositar tp <valor>")
                    .build(), e -> {
                TransactionResult res = mgr.depositTp(player, amt);
                player.sendMessage(mgr.formatFeedback(res));
                open();
            });
        }

        // Botões rápidos de Depósito de Money (Slots 23, 24, 25)
        double[] moneyAmounts = {1_000.0, 10_000.0, 100_000.0};
        int[] moneySlots = {23, 24, 25};
        for (int i = 0; i < moneyAmounts.length; i++) {
            double amt = moneyAmounts[i];
            setItem(moneySlots[i], new ItemBuilder(Material.EMERALD)
                    .name("&#55FF55&lDepositar +$" + formatMoney(amt))
                    .lore("&7Transfere dinheiro (Vault) da sua conta",
                          "&7para o banco do clã.",
                          "",
                          "&e▶ Clique para depositar!",
                          "&8Ou use: /clan banco depositar money <valor>")
                    .build(), e -> {
                TransactionResult res = mgr.depositMoney(player, amt);
                player.sendMessage(mgr.formatFeedback(res));
                open();
            });
        }

        // Slot 31: Top Doadores do Clã
        List<ClanMember> donors = clan.getMembers().values().stream()
                .sorted(Comparator.comparingLong(ClanMember::getDonatedTp)
                        .thenComparingDouble(ClanMember::getDonatedMoney).reversed())
                .limit(5)
                .toList();
        List<String> donorLore = new ArrayList<>();
        donorLore.add("&7Maiores contribuidores do clã:");
        donorLore.add("");
        int pos = 1;
        for (ClanMember m : donors) {
            donorLore.add("&e#" + pos++ + " &f" + m.getLastKnownName()
                    + " &8- &b" + formatInt(m.getDonatedTp()) + " TP &8| &a$" + formatMoney(m.getDonatedMoney()));
        }
        donorLore.add("");
        donorLore.add("&7Para valor personalizado use:");
        donorLore.add("&e/clan banco depositar <tp|money> <quantia>");
        setItem(31, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&#FFAA00&lTop Doadores do Clã")
                .lore(donorLore)
                .build());

        // Upgrades (Slots 37, 39, 41, 43)
        boolean canUpgrade = clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_UPGRADE)
                || clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_WITHDRAW);

        renderUpgradeSlot(37, UpgradeType.TP_MULTIPLIER, Material.EXPERIENCE_BOTTLE, "%", canUpgrade);
        renderUpgradeSlot(39, UpgradeType.MONEY_MULTIPLIER, Material.GOLD_INGOT, "%", canUpgrade);
        renderUpgradeSlot(41, UpgradeType.MEMBER_LIMIT, Material.PLAYER_HEAD, " vagas", canUpgrade);
        renderUpgradeSlot(43, UpgradeType.WAR_MULTIPLIER, Material.NETHERITE_SWORD, "%", canUpgrade);

        // Slot 49: Voltar
        setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&cVoltar")
                .lore("&7Retornar ao menu principal.")
                .build(), e -> new MainMenu(player, plugin).open());
    }

    private void renderUpgradeSlot(int slot, UpgradeType type, Material icon, String unitSuffix, boolean canUpgrade) {
        BankUpgradeManager mgr = plugin.getBankUpgradeManager();
        int level = clan.getUpgrades().getLevel(type);
        int maxLevel = mgr.getMaxLevel(type);
        double curBonus = mgr.getBonusValue(type, level);
        boolean isMax = level >= maxLevel;

        List<String> lore = new ArrayList<>();
        lore.add("&7Nível Atual: &e" + level + "&7/&e" + maxLevel);
        lore.add("&7Bônus Atual: &#55FF55+" + formatBonus(curBonus) + unitSuffix);

        if (isMax) {
            lore.add("");
            lore.add("&#55FF55✔ Upgrade no nível MÁXIMO!");
        } else {
            int nextLevel = level + 1;
            double nextBonus = mgr.getBonusValue(type, nextLevel);
            long costTp = mgr.getCostTp(type, nextLevel);
            double costMoney = mgr.getCostMoney(type, nextLevel);
            boolean enough = clan.getBank().hasEnough(costTp, costMoney);

            lore.add("&7Próximo Nível: &#FFAA00+" + formatBonus(nextBonus) + unitSuffix);
            lore.add("");
            lore.add("&7Custo no Banco do Clã:");
            lore.add(" &8» &b" + formatInt(costTp) + " TP");
            lore.add(" &8» &a$" + formatMoney(costMoney));
            lore.add("");
            if (!canUpgrade) {
                lore.add("&cApenas Líder ou Sub-Líder pode evoluir.");
            } else if (!enough) {
                lore.add("&cSaldo insuficiente no Banco do Clã.");
            } else {
                lore.add("&#55FF55▶ Clique para evoluir para Nv." + nextLevel + "!");
            }
        }

        ItemBuilder builder = new ItemBuilder(icon)
                .name("&#FFAA00&l" + type.getDisplayName() + " &8[Nv." + level + "]")
                .lore(lore);
        if (level > 0) builder.glow();

        setItem(slot, builder.build(), e -> {
            if (!isMax) {
                TransactionResult res = mgr.buyUpgrade(player, type);
                player.sendMessage(mgr.formatFeedback(res));
                open();
            }
        });
    }

    private static String formatInt(long val) {
        return NumberFormat.getIntegerInstance(Locale.US).format(val);
    }

    private static String formatMoney(double val) {
        return String.format(Locale.US, "%,.2f", val);
    }

    private static String formatBonus(double val) {
        if (Math.abs(val - Math.rint(val)) < 1e-6) {
            return Long.toString(Math.round(val));
        }
        return String.format(Locale.US, "%.1f", val);
    }
}
