package org.vine.vclans.listener;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanUpgrades.UpgradeType;
import org.vine.vclans.util.ColorUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Listener responsável pelos multiplicadores passivos de Farm/Combate (TP + Money)
 * e pela pontuação PvE / Mineração da Guerra de Clãs por Temporada.
 */
public class ClanFarmWarListener implements Listener {

    private final VClans plugin;

    // Trava anti-macro: armazena até 50.000 blocos colocados recentemente por jogadores
    private final Map<String, Boolean> playerPlacedBlocks = Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 50_000;
                }
            }
    );

    public ClanFarmWarListener(VClans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (plugin.getSeasonWarManager().isTrackedWarBlock(block.getType())) {
            playerPlacedBlocks.put(blockKey(block), Boolean.TRUE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlock();
        // Bloco colocado por jogador nunca gera pontos de guerra
        if (playerPlacedBlocks.remove(blockKey(block)) != null) {
            return;
        }

        int basePoints = plugin.getSeasonWarManager().getBlockWarPoints(block.getType());
        if (basePoints <= 0) {
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player);
        if (clan == null) {
            return;
        }

        int gained = plugin.getSeasonWarManager().addWarPoints(clan, player, basePoints, "Bloco: " + block.getType().name());
        if (gained > 0 && plugin.getConfig().getBoolean("farm-rewards.actionbar-enabled", true)) {
            sendActionBar(player, "&#FFAA00+" + gained + " Pontos de Guerra &8(&7Mineração&8)");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null || !killer.isOnline()) {
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(killer);
        if (clan == null) {
            return;
        }

        long gainedTp = 0L;
        double gainedMoney = 0.0;
        double tpBonusPct = plugin.getBankUpgradeManager().getBonusValue(UpgradeType.TP_MULTIPLIER, clan.getUpgrades().getTpMultiplierLevel());
        double moneyBonusPct = plugin.getBankUpgradeManager().getBonusValue(UpgradeType.MONEY_MULTIPLIER, clan.getUpgrades().getMoneyMultiplierLevel());

        if (plugin.getConfig().getBoolean("farm-rewards.enabled", true)) {
            RewardConfig baseReward = resolveEntityReward(entity);
            if (baseReward != null) {
                boolean bonusOnly = plugin.getConfig().getBoolean("farm-rewards.bonus-only-mode", false);

                if (baseReward.tp > 0L && plugin.getDragonMineZHook().isEnabled()) {
                    double mult = bonusOnly ? (tpBonusPct / 100.0) : (1.0 + tpBonusPct / 100.0);
                    gainedTp = Math.round(baseReward.tp * mult);
                    if (gainedTp > 0L) {
                        plugin.getDragonMineZHook().addPlayerTp(killer, gainedTp);
                    }
                }

                if (baseReward.money > 0.0 && plugin.getVaultHook().isEnabled()) {
                    double mult = bonusOnly ? (moneyBonusPct / 100.0) : (1.0 + moneyBonusPct / 100.0);
                    gainedMoney = Math.round(baseReward.money * mult * 100.0) / 100.0;
                    if (gainedMoney > 0.0) {
                        plugin.getVaultHook().deposit(killer, gainedMoney);
                    }
                }
            }
        }

        int warBasePts = plugin.getSeasonWarManager().getEntityWarPoints(entity);
        int gainedWarPts = 0;
        if (warBasePts > 0) {
            gainedWarPts = plugin.getSeasonWarManager().addWarPoints(clan, killer, warBasePts, "Abate PvE");
        }

        if ((gainedTp > 0L || gainedMoney > 0.0 || gainedWarPts > 0)
                && plugin.getConfig().getBoolean("farm-rewards.actionbar-enabled", true)) {
            StringBuilder bar = new StringBuilder();
            if (gainedTp > 0L) {
                bar.append("&#55FF55+").append(gainedTp).append(" TP");
                if (tpBonusPct > 0.0) {
                    bar.append(" &8(&#FFAA00+").append((int) Math.round(tpBonusPct)).append("% Clã&8)");
                }
            }
            if (gainedMoney > 0.0) {
                if (bar.length() > 0) bar.append(" &7| ");
                bar.append("&#55FF55+$").append(String.format(Locale.US, "%.2f", gainedMoney));
                if (moneyBonusPct > 0.0) {
                    bar.append(" &8(&#FFAA00+").append((int) Math.round(moneyBonusPct)).append("% Clã&8)");
                }
            }
            if (gainedWarPts > 0) {
                if (bar.length() > 0) bar.append(" &7| ");
                bar.append("&#FFAA00+").append(gainedWarPts).append(" Guerra");
            }
            sendActionBar(killer, bar.toString());
        }
    }

    private RewardConfig resolveEntityReward(LivingEntity entity) {
        String customName = entity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            String stripped = ColorUtil.strip(customName).trim().toLowerCase(Locale.ROOT);
            ConfigurationSection byName = plugin.getConfig().getConfigurationSection("farm-rewards.entities-by-name");
            if (byName != null) {
                for (String key : byName.getKeys(false)) {
                    String cleanKey = ColorUtil.strip(key).trim().toLowerCase(Locale.ROOT);
                    if (stripped.equals(cleanKey) || stripped.contains(cleanKey)) {
                        return readReward(byName.getConfigurationSection(key));
                    }
                }
            }
        }

        String typeName = entity.getType().name();
        ConfigurationSection byType = plugin.getConfig().getConfigurationSection("farm-rewards.entities-by-type." + typeName);
        if (byType != null) {
            return readReward(byType);
        }

        if (entity instanceof Monster) {
            ConfigurationSection defSec = plugin.getConfig().getConfigurationSection("farm-rewards.default-monster");
            if (defSec != null) {
                return readReward(defSec);
            }
        }
        return null;
    }

    private RewardConfig readReward(ConfigurationSection sec) {
        if (sec == null) return null;
        long tp = Math.max(0L, sec.getLong("tp", 0L));
        double money = Math.max(0.0, sec.getDouble("money", 0.0));
        if (tp <= 0L && money <= 0.0) return null;
        return new RewardConfig(tp, money);
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ColorUtil.color(message)));
        } catch (Exception ignored) {}
    }

    private static String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private record RewardConfig(long tp, double money) {}
}
