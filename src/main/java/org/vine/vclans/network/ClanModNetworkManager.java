package org.vine.vclans.network;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.vine.vclans.VClans;
import org.vine.vclans.manager.BankUpgradeManager.TransactionResult;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanPermission;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.model.ClanUpgrades.UpgradeType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canal de Plugin Messaging blindado ("vclans:ui") para comunicação com o Mod GUI no cliente.
 * O cliente envia apenas intenções simples (bytes/primitivos); toda validação e lógica rodam no servidor.
 */
public class ClanModNetworkManager implements PluginMessageListener {

    public static final String CHANNEL = "vclans:ui";
    public static final byte PROTOCOL_VERSION = 1;
    private static final int MAX_C2S_PACKET_BYTES = 64;

    private static final byte C2S_REQUEST_SYNC = 0x01;
    private static final byte C2S_DEPOSIT_TP = 0x02;
    private static final byte C2S_DEPOSIT_MONEY = 0x03;
    private static final byte C2S_BUY_UPGRADE = 0x04;

    private static final byte S2C_SYNC_SNAPSHOT = 0x01;
    private static final byte S2C_ACTION_FEEDBACK = 0x02;

    private final VClans plugin;
    private final Map<UUID, Long> lastSyncRequestMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWritePacketMs = new ConcurrentHashMap<>();

    public ClanModNetworkManager(VClans plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void clearRateLimit(UUID uuid) {
        if (uuid != null) {
            lastSyncRequestMs.remove(uuid);
            lastWritePacketMs.remove(uuid);
        }
    }

    public void cleanup() {
        lastSyncRequestMs.clear();
        lastWritePacketMs.clear();
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel) || player == null || !player.isOnline()) {
            return;
        }
        if (message.length < 2 || message.length > MAX_C2S_PACKET_BYTES) {
            return;
        }

        // Clonar bytes e garantir processamento estrito na thread principal do servidor
        byte[] payload = message.clone();
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    handlePacketOnMainThread(player, payload);
                }
            });
            return;
        }
        handlePacketOnMainThread(player, payload);
    }

    private void handlePacketOnMainThread(Player player, byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            byte version = in.readByte();
            if (version != PROTOCOL_VERSION) {
                return;
            }
            byte action = in.readByte();

            switch (action) {
                case C2S_REQUEST_SYNC -> {
                    if (!allowRateLimit(lastSyncRequestMs, player.getUniqueId(), 250L)) {
                        return;
                    }
                    sendSyncSnapshot(player);
                }
                case C2S_DEPOSIT_TP -> {
                    if (!allowRateLimit(lastWritePacketMs, player.getUniqueId(), 400L)) {
                        return;
                    }
                    long amount = in.readLong();
                    if (amount <= 0L) {
                        sendActionFeedback(player, false, "Valor de depósito inválido.");
                        return;
                    }
                    TransactionResult result = plugin.getBankUpgradeManager().depositTp(player, amount);
                    sendActionFeedback(player, result.success(), result.message());
                }
                case C2S_DEPOSIT_MONEY -> {
                    if (!allowRateLimit(lastWritePacketMs, player.getUniqueId(), 400L)) {
                        return;
                    }
                    double amount = in.readDouble();
                    if (!Double.isFinite(amount) || amount <= 0.0) {
                        sendActionFeedback(player, false, "Valor de depósito inválido.");
                        return;
                    }
                    TransactionResult result = plugin.getBankUpgradeManager().depositMoney(player, amount);
                    sendActionFeedback(player, result.success(), result.message());
                }
                case C2S_BUY_UPGRADE -> {
                    if (!allowRateLimit(lastWritePacketMs, player.getUniqueId(), 400L)) {
                        return;
                    }
                    byte upgradeId = in.readByte();
                    UpgradeType type = UpgradeType.fromId(upgradeId);
                    if (type == null) {
                        sendActionFeedback(player, false, "Upgrade desconhecido.");
                        return;
                    }
                    TransactionResult result = plugin.getBankUpgradeManager().buyUpgrade(player, type);
                    sendActionFeedback(player, result.success(), result.message());
                }
                default -> {
                    // Ação desconhecida: falha fechado ignorando
                }
            }
        } catch (Exception ignored) {
            // Pacote malformado/truncado: ignora silenciosamente sem derrubar servidor
        }
    }

    private boolean allowRateLimit(Map<UUID, Long> map, UUID uuid, long minIntervalMs) {
        long now = System.currentTimeMillis();
        Long last = map.get(uuid);
        if (last != null && (now - last) < minIntervalMs) {
            return false;
        }
        map.put(uuid, now);
        return true;
    }

    public void sendActionFeedback(Player player, boolean success, String message) {
        if (player == null || !player.isOnline()) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(PROTOCOL_VERSION);
            out.writeByte(S2C_ACTION_FEEDBACK);
            out.writeBoolean(success);
            out.writeUTF(message != null ? message : "");
            out.flush();
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
        } catch (IOException ignored) {}
    }

    public void sendSyncSnapshot(Player player) {
        if (player == null || !player.isOnline()) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeByte(PROTOCOL_VERSION);
            out.writeByte(S2C_SYNC_SNAPSHOT);

            Clan clan = plugin.getClanManager().getClanByPlayer(player);
            out.writeBoolean(clan != null);

            var dmzHook = plugin.getDragonMineZHook();
            var vaultHook = plugin.getVaultHook();
            long playerTp = (dmzHook != null && dmzHook.isEnabled()) ? dmzHook.getPlayerTp(player) : 0L;
            double playerMoney = (vaultHook != null && vaultHook.isEnabled()) ? getVaultBalanceSafe(player) : 0.0;
            out.writeLong(playerTp);
            out.writeDouble(playerMoney);

            var bankMgr = plugin.getBankUpgradeManager();
            var warMgr = plugin.getSeasonWarManager();
            boolean doubleWarActive = warMgr != null && warMgr.isDoublePointsActive();
            out.writeBoolean(doubleWarActive);

            if (clan != null) {
                ClanMember member = clan.getMember(player.getUniqueId());
                ClanRole role = member != null ? member.getRole() : ClanRole.MEMBER;
                boolean canDeposit = clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_DEPOSIT);
                boolean canUpgrade = clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_UPGRADE)
                        || clan.hasPermission(player.getUniqueId(), ClanPermission.BANK_WITHDRAW);

                out.writeUTF(clan.getId().toString());
                out.writeUTF(clan.getName());
                out.writeUTF(clan.getTag());
                out.writeUTF(clan.getTagColor());
                out.writeUTF(role.name());
                out.writeInt(clan.getMembersCount());
                out.writeInt(bankMgr.getMaxMembers(clan));
                out.writeBoolean(canDeposit);
                out.writeBoolean(canUpgrade);

                out.writeLong(clan.getBank().getTp());
                out.writeDouble(clan.getBank().getMoney());

                out.writeInt(clan.getSeasonWarPoints());
                out.writeInt(warMgr != null ? warMgr.getClanRank(clan.getId()) : -1);

                UpgradeType[] types = UpgradeType.values();
                out.writeByte(types.length);
                for (UpgradeType type : types) {
                    int lvl = clan.getUpgrades().getLevel(type);
                    int maxLvl = bankMgr.getMaxLevel(type);
                    double bonus = bankMgr.getBonusValue(type, lvl);
                    double nextBonus = lvl < maxLvl ? bankMgr.getBonusValue(type, lvl + 1) : bonus;
                    long nextTpCost = lvl < maxLvl ? bankMgr.getCostTp(type, lvl + 1) : 0L;
                    double nextMoneyCost = lvl < maxLvl ? bankMgr.getCostMoney(type, lvl + 1) : 0.0;

                    out.writeByte(type.getId());
                    out.writeInt(lvl);
                    out.writeInt(maxLvl);
                    out.writeDouble(bonus);
                    out.writeDouble(nextBonus);
                    out.writeLong(nextTpCost);
                    out.writeDouble(nextMoneyCost);
                }

                // Top 5 doadores internos do clã
                List<ClanMember> topDonors = clan.getMembers().values().stream()
                        .sorted(Comparator.comparingLong(ClanMember::getDonatedTp)
                                .thenComparingDouble(ClanMember::getDonatedMoney).reversed())
                        .limit(5)
                        .toList();
                out.writeByte(topDonors.size());
                for (ClanMember m : topDonors) {
                    out.writeUTF(m.getLastKnownName());
                    out.writeLong(m.getDonatedTp());
                    out.writeDouble(m.getDonatedMoney());
                    out.writeInt(m.getWarPointsContributed());
                }
            }

            // Top 10 da Guerra de Temporada
            List<Clan> topWar = warMgr != null ? warMgr.getTopSeasonClans(10) : List.of();
            out.writeByte(topWar.size());
            for (Clan c : topWar) {
                out.writeUTF(c.getName());
                out.writeUTF(c.getTag());
                out.writeUTF(c.getTagColor());
                out.writeInt(c.getSeasonWarPoints());
            }

            out.flush();
            player.sendPluginMessage(plugin, CHANNEL, baos.toByteArray());
        } catch (IOException ignored) {}
    }

    private double getVaultBalanceSafe(Player player) {
        try {
            return plugin.getVaultHook().getBalance(player);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }
}
