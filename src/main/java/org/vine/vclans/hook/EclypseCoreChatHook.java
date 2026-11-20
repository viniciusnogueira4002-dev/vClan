package org.vine.vclans.hook;

import org.bukkit.entity.Player;
import org.vine.vclans.VClans;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Ponte reflexiva opcional para entregar mensagens do chat de Clã diretamente
 * à aba {@code CLÃ} do EclypseCore no Mohist 1.20.1, com fallback para {@link Player#sendMessage(String)}.
 */
public class EclypseCoreChatHook {

    private static final String CLAN_CHANNEL_ID = "clan";

    private final Method sendChannelLegacyByUuidMethod;
    private final boolean available;

    public EclypseCoreChatHook(VClans plugin) {
        Method resolved = null;
        boolean ok = false;
        try {
            Class<?> apiClass = Class.forName("kk.sleexzx.features.chat.server.EclypseChatApi");
            resolved = apiClass.getMethod("sendChannelLegacyByUuid", UUID.class, String.class, String.class);
            ok = true;
            plugin.getLogger().info("Integração de Chat com EclypseCore detectada (aba CLÃ ativa).");
        } catch (Throwable ignored) {
            plugin.getLogger().info("EclypseCoreChatApi não detectada; usando chat padrão do Bukkit para mensagens de clã.");
        }
        this.sendChannelLegacyByUuidMethod = resolved;
        this.available = ok;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public void sendClanMessage(Player target, String legacyColoredMessage) {
        if (target == null || !target.isOnline() || legacyColoredMessage == null || legacyColoredMessage.isEmpty()) {
            return;
        }
        if (this.available && this.sendChannelLegacyByUuidMethod != null) {
            try {
                Object delivered = this.sendChannelLegacyByUuidMethod.invoke(
                        null,
                        target.getUniqueId(),
                        CLAN_CHANNEL_ID,
                        legacyColoredMessage
                );
                if (Boolean.TRUE.equals(delivered)) {
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        target.sendMessage(legacyColoredMessage);
    }
}
