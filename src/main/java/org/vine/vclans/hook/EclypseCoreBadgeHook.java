package org.vine.vclans.hook;

import org.vine.vclans.VClans;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/**
 * Ponte reflexiva opcional para sincronizar placas customizadas de clã com o módulo
 * {@code ClanBadgeSyncServer} do EclypseCore no Mohist 1.20.1, além de codificar o marcador de chat.
 */
public class EclypseCoreBadgeHook {

    public static final char WIRE_START = '\uE601';
    public static final char WIRE_END = '\uE602';
    private static final int WIRE_PAYLOAD_BASE = 0xE700;

    private final Method notifyPlayerMethod;
    private final Method notifyClanMethod;
    private final boolean available;

    public EclypseCoreBadgeHook(VClans plugin) {
        Method playerMethod = null;
        Method clanMethod = null;
        boolean ok = false;
        try {
            Class<?> syncClass = Class.forName("kk.sleexzx.features.clan.server.ClanBadgeSyncServer");
            playerMethod = syncClass.getMethod("notifyPlayerBadge", UUID.class, String.class, String.class);
            clanMethod = syncClass.getMethod("notifyClanUpdated", Collection.class, String.class, String.class);
            ok = true;
            plugin.getLogger().info("Integração de Placas de Clã com EclypseCore detectada.");
        } catch (Throwable ignored) {
            plugin.getLogger().info("EclypseCore ClanBadgeSyncServer não detectado; placas customizadas usarão apenas texto padrão.");
        }
        this.notifyPlayerMethod = playerMethod;
        this.notifyClanMethod = clanMethod;
        this.available = ok;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public void notifyPlayerBadge(UUID playerUuid, String tag, String tagStyle) {
        if (!this.available || this.notifyPlayerMethod == null || playerUuid == null) {
            return;
        }
        try {
            this.notifyPlayerMethod.invoke(null, playerUuid, tag != null ? tag : "", tagStyle != null ? tagStyle : "");
        } catch (Throwable ignored) {
        }
    }

    public void notifyClanUpdated(Collection<UUID> memberUuids, String tag, String tagStyle) {
        if (!this.available || this.notifyClanMethod == null || memberUuids == null || memberUuids.isEmpty()) {
            return;
        }
        try {
            this.notifyClanMethod.invoke(null, memberUuids, tag != null ? tag : "", tagStyle != null ? tagStyle : "");
        } catch (Throwable ignored) {
        }
    }

    /**
     * Codifica {@code TAG|STYLE} em Unicodes PUA invisíveis (\uE700..+\uE77F) delimitados por \uE601...\uE602.
     */
    public static String encodeChatBadge(String tag, String tagStyle) {
        if (tag == null || tag.isBlank() || tagStyle == null || tagStyle.isBlank()) {
            return "";
        }
        String raw = tag.trim() + "|" + tagStyle.trim();
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append(WIRE_START);
        for (int i = 0; i < raw.length(); i++) {
            int code = raw.charAt(i) & 0x7F;
            if (code >= 0x20 && code <= 0x7E) {
                sb.append((char) (WIRE_PAYLOAD_BASE + code));
            }
        }
        sb.append(WIRE_END);
        return sb.toString();
    }
}
