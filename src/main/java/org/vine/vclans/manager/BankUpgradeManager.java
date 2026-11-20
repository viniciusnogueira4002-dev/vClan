package org.vine.vclans.manager;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.vine.vclans.VClans;
import org.vine.vclans.menu.BankUpgradeMenu;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.model.ClanUpgrades.UpgradeType;
import org.vine.vclans.util.ColorUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador central do Banco Dual do Clã (TP + Money) e Upgrades.
 * Política mão única (sem saque) e execução atômica na thread principal com rate-limit por UUID.
 */
public class BankUpgradeManager {

    public static final int TAB_OVERVIEW = 0;
    public static final int TAB_BANK = 1;
    public static final int TAB_MEMBERS = 2;
    public static final int TAB_COMBAT = 3;
    public static final int TAB_RANKINGS = 4;
    public static final int TAB_ADMIN = 5;

    public record TransactionResult(boolean success, String message) {}

    private final VClans plugin;
    private final Map<UUID, Long> lastTransactionMs = new ConcurrentHashMap<>();

    public BankUpgradeManager(VClans plugin) {
        this.plugin = plugin;
    }

    public void clearCooldown(UUID uuid) {
        if (uuid != null) {
            lastTransactionMs.remove(uuid);
        }
    }

    public void cleanup() {
        lastTransactionMs.clear();
    }

    private boolean checkAndConsumeCooldown(Player player) {
        long cooldownMs = Math.max(100L, plugin.getConfig().getLong("bank.transaction-cooldown-ms", 500L));
        long now = System.currentTimeMillis();
        Long last = lastTransactionMs.get(player.getUniqueId());
        if (last != null && (now - last) < cooldownMs) {
            return false;
        }
        lastTransactionMs.put(player.getUniqueId(), now);
        return true;
    }

    public int getMaxLevel(UpgradeType type) {
        String path = "upgrades." + type.getConfigKey() + ".max-level";
        int def = (type == UpgradeType.MEMBER_LIMIT) ? 5 : 10;
        return Math.max(1, plugin.getConfig().getInt(path, def));
    }

    public double getBonusValue(UpgradeType type, int level) {
        if (level <= 0) return 0.0;
        String basePath = "upgrades." + type.getConfigKey();
        ConfigurationSection levelSection = plugin.getConfig().getConfigurationSection(basePath + ".levels." + level);
        if (levelSection != null && levelSection.contains("bonus")) {
            return Math.max(0.0, levelSection.getDouble("bonus"));
        }
        double defaultPerLevel = switch (type) {
            case TP_MULTIPLIER, MONEY_MULTIPLIER -> 5.0;
            case MEMBER_LIMIT -> 2.0;
            case WAR_MULTIPLIER -> 3.0;
        };
        double perLevel = Math.max(0.0, plugin.getConfig().getDouble(basePath + ".bonus-per-level", defaultPerLevel));
        return perLevel * level;
    }

    public long getCostTp(UpgradeType type, int targetLevel) {
        if (targetLevel <= 0) return 0L;
        String basePath = "upgrades." + type.getConfigKey();
        ConfigurationSection levelSection = plugin.getConfig().getConfigurationSection(basePath + ".levels." + targetLevel);
        if (levelSection != null && levelSection.contains("tp")) {
            return Math.max(0L, levelSection.getLong("tp"));
        }
        long baseCost = Math.max(0L, plugin.getConfig().getLong(basePath + ".base-cost-tp", 10000L));
        double scale = Math.max(1.0, plugin.getConfig().getDouble(basePath + ".cost-scale-multiplier", 1.75));
        double computed = baseCost * Math.pow(scale, targetLevel - 1);
        if (!Double.isFinite(computed) || computed >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(computed));
    }

    public double getCostMoney(UpgradeType type, int targetLevel) {
        if (targetLevel <= 0) return 0.0;
        String basePath = "upgrades." + type.getConfigKey();
        ConfigurationSection levelSection = plugin.getConfig().getConfigurationSection(basePath + ".levels." + targetLevel);
        if (levelSection != null && levelSection.contains("money")) {
            return Math.max(0.0, levelSection.getDouble("money"));
        }
        double baseCost = Math.max(0.0, plugin.getConfig().getDouble(basePath + ".base-cost-money", 5000.0));
        double scale = Math.max(1.0, plugin.getConfig().getDouble(basePath + ".cost-scale-multiplier", 1.75));
        double computed = baseCost * Math.pow(scale, targetLevel - 1);
        if (!Double.isFinite(computed)) {
            return Double.MAX_VALUE;
        }
        return Math.round(computed * 100.0) / 100.0;
    }

    public int getMaxMembers(Clan clan) {
        int baseMax = plugin.getConfig().getInt("clan.max-members", 20);
        if (clan == null) return baseMax;
        int extra = (int) Math.floor(getBonusValue(UpgradeType.MEMBER_LIMIT, clan.getUpgrades().getMemberLimitLevel()));
        return Math.max(1, baseMax + Math.max(0, extra));
    }

    public double getTpMultiplierFactor(Clan clan) {
        if (clan == null) return 1.0;
        return 1.0 + (getBonusValue(UpgradeType.TP_MULTIPLIER, clan.getUpgrades().getTpMultiplierLevel()) / 100.0);
    }

    public double getMoneyMultiplierFactor(Clan clan) {
        if (clan == null) return 1.0;
        return 1.0 + (getBonusValue(UpgradeType.MONEY_MULTIPLIER, clan.getUpgrades().getMoneyMultiplierLevel()) / 100.0);
    }

    public double getWarMultiplierFactor(Clan clan) {
        if (clan == null) return 1.0;
        return 1.0 + (getBonusValue(UpgradeType.WAR_MULTIPLIER, clan.getUpgrades().getWarMultiplierLevel()) / 100.0);
    }

    public TransactionResult depositTp(Player player, long amount) {
        if (!Bukkit.isPrimaryThread() || player == null || !player.isOnline()) {
            return new TransactionResult(false, "Transação inválida.");
        }
        if (!checkAndConsumeCooldown(player)) {
            return new TransactionResult(false, "Aguarde um instante antes de realizar outra operação.");
        }

        long maxTx = Math.max(1L, plugin.getConfig().getLong("bank.max-deposit-tp-per-tx", 1_000_000_000L));
        if (amount <= 0L || amount > maxTx) {
            return new TransactionResult(false, "Valor de TP inválido para depósito.");
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            return new TransactionResult(false, "Você não pertence a nenhum clã.");
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_DEPOSIT)) {
            return new TransactionResult(false, "Você não tem permissão para depositar no banco do clã.");
        }

        var dmzHook = plugin.getDragonMineZHook();
        if (dmzHook == null || !dmzHook.isEnabled()) {
            return new TransactionResult(false, "Integração de TP (DragonMineZ) indisponível no servidor.");
        }

        long currentTp = dmzHook.getPlayerTp(player);
        if (currentTp < amount) {
            return new TransactionResult(false, "TP insuficiente! Você possui " + currentTp + " TP.");
        }

        // Retirada atômica no servidor + crédito no banco do clã
        if (!dmzHook.withdrawPlayerTp(player, amount)) {
            return new TransactionResult(false, "Não foi possível descontar seu TP no momento.");
        }

        clan.getBank().addTp(amount);
        ClanMember member = clan.getMember(player.getUniqueId());
        if (member != null) {
            member.addDonatedTp(amount);
        }
        clan.addLog(player.getUniqueId(), player.getName() + " depositou " + amount + " TP no banco");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRepository().saveEconomy(clan);
            if (member != null) {
                plugin.getRepository().saveMember(clan.getId(), member);
            }
        });

        playFeedbackSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
        syncModUiForClan(clan);
        return new TransactionResult(true, "Depositados " + amount + " TP no banco do clã!");
    }

    public TransactionResult depositMoney(Player player, double rawAmount) {
        if (!Bukkit.isPrimaryThread() || player == null || !player.isOnline()) {
            return new TransactionResult(false, "Transação inválida.");
        }
        if (!checkAndConsumeCooldown(player)) {
            return new TransactionResult(false, "Aguarde um instante antes de realizar outra operação.");
        }

        if (!Double.isFinite(rawAmount)) {
            return new TransactionResult(false, "Valor inválido.");
        }
        double amount = Math.round(rawAmount * 100.0) / 100.0;
        double maxTx = Math.max(1.0, plugin.getConfig().getDouble("bank.max-deposit-money-per-tx", 1_000_000_000.0));
        if (amount < 1.0 || amount > maxTx) {
            return new TransactionResult(false, "Valor de dinheiro inválido (mínimo 1.0).");
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            return new TransactionResult(false, "Você não pertence a nenhum clã.");
        }
        if (!clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_DEPOSIT)) {
            return new TransactionResult(false, "Você não tem permissão para depositar no banco do clã.");
        }

        var vaultHook = plugin.getVaultHook();
        if (vaultHook == null || !vaultHook.isEnabled()) {
            return new TransactionResult(false, "Economia via Vault não encontrada no servidor.");
        }
        if (!vaultHook.hasMoney(player, amount)) {
            return new TransactionResult(false, "Dinheiro insuficiente na sua conta.");
        }
        if (!vaultHook.withdraw(player, amount)) {
            return new TransactionResult(false, "Falha ao descontar dinheiro via Vault.");
        }

        clan.getBank().addMoney(amount);
        ClanMember member = clan.getMember(player.getUniqueId());
        if (member != null) {
            member.addDonatedMoney(amount);
        }
        clan.addLog(player.getUniqueId(), player.getName() + " depositou $" + String.format(java.util.Locale.US, "%.2f", amount) + " no banco");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRepository().saveEconomy(clan);
            if (member != null) {
                plugin.getRepository().saveMember(clan.getId(), member);
            }
        });

        playFeedbackSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
        syncModUiForClan(clan);
        return new TransactionResult(true, "Depositados $" + String.format(java.util.Locale.US, "%.2f", amount) + " no banco do clã!");
    }

    public TransactionResult buyUpgrade(Player player, UpgradeType type) {
        if (!Bukkit.isPrimaryThread() || player == null || !player.isOnline() || type == null) {
            return new TransactionResult(false, "Operação inválida.");
        }
        if (!checkAndConsumeCooldown(player)) {
            return new TransactionResult(false, "Aguarde um instante antes de comprar outro upgrade.");
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            return new TransactionResult(false, "Você não pertence a nenhum clã.");
        }

        boolean canUpgrade = clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_UPGRADE)
                || clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_WITHDRAW);
        if (!canUpgrade) {
            return new TransactionResult(false, "Apenas Líder ou Sub-Líder pode evoluir upgrades do clã.");
        }

        int currentLevel = clan.getUpgrades().getLevel(type);
        int maxLevel = getMaxLevel(type);
        if (currentLevel >= maxLevel) {
            return new TransactionResult(false, "O upgrade " + type.getDisplayName() + " já está no nível máximo (" + maxLevel + ")!");
        }

        int nextLevel = currentLevel + 1;
        long costTp = getCostTp(type, nextLevel);
        double costMoney = getCostMoney(type, nextLevel);

        if (!clan.getBank().trySpend(costTp, costMoney)) {
            return new TransactionResult(false, "Saldo insuficiente no Banco do Clã! Necessário: "
                    + costTp + " TP e $" + String.format(java.util.Locale.US, "%.2f", costMoney) + ".");
        }

        clan.getUpgrades().setLevel(type, nextLevel);
        clan.addLog(player.getUniqueId(), player.getName() + " evoluiu " + type.getDisplayName() + " para Nv." + nextLevel);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getRepository().saveEconomy(clan));

        String prefix = plugin.getMessages().getString("prefix", "");
        clan.broadcast(prefix + "&#55FF55O membro &e" + player.getName()
                + " &#55FF55evoluiu &#FFAA00" + type.getDisplayName()
                + " &#55FF55para o nível &e" + nextLevel + "&#55FF55!");

        playFeedbackSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.3f);
        syncModUiForClan(clan);
        return new TransactionResult(true, type.getDisplayName() + " evoluído para Nv." + nextLevel + "!");
    }

    private void syncModUiForClan(Clan clan) {
        var modNet = plugin.getModNetworkManager();
        if (modNet == null || clan == null) return;
        for (Player online : clan.getOnlinePlayers()) {
            modNet.sendSyncSnapshot(online);
        }
    }

    private void playFeedbackSound(Player player, Sound sound, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, 1.0f, pitch);
        } catch (Exception ignored) {}
    }

    public String formatFeedback(TransactionResult result) {
        String prefix = plugin.getMessages().getString("prefix", "");
        String color = result.success() ? "&#55FF55" : "&#FF5555";
        return ColorUtil.color(prefix + color + result.message());
    }

    /**
     * Tenta abrir a GUI do EclypseCore (Astral Aberto) via ponte reflexiva no Mohist na aba solicitada.
     */
    public boolean openClanModUi(Player player, int initialTab) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        try {
            Class<?> bridgeClass = Class.forName("kk.sleexzx.features.clan.server.VClansBridge");
            boolean available = (boolean) bridgeClass.getMethod("isAvailable").invoke(null);
            if (available) {
                if (player.getOpenInventory() != null
                        && player.getOpenInventory().getTopInventory().getHolder() instanceof org.vine.vclans.menu.Menu) {
                    player.closeInventory();
                }
                try {
                    Object result = bridgeClass.getMethod("openClanMenu", UUID.class, int.class)
                            .invoke(null, player.getUniqueId(), initialTab);
                    if (Boolean.TRUE.equals(result)) {
                        return true;
                    }
                } catch (NoSuchMethodException fallbackOld) {
                    Object result = bridgeClass.getMethod("openClanMenu", UUID.class)
                            .invoke(null, player.getUniqueId());
                    if (Boolean.TRUE.equals(result)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // EclypseCore ausente ou desativado: cai no menu Bukkit fallback
        }
        return false;
    }

    /**
     * Tenta abrir a GUI do EclypseCore (Astral Aberto) via ponte reflexiva no Mohist;
     * se o mod não estiver disponível, abre o menu fallback em inventário Bukkit.
     */
    public boolean openClanBankUi(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            String prefix = plugin.getMessages().getString("prefix", "");
            player.sendMessage(ColorUtil.color(prefix + plugin.getMessages().getString("general.not-in-clan", "")));
            return false;
        }
        if (openClanModUi(player, TAB_BANK)) {
            return true;
        }
        new BankUpgradeMenu(player, plugin, clan).open();
        return false;
    }
}
