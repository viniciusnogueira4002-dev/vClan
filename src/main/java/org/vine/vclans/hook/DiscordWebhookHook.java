package org.vine.vclans.hook;

import org.vine.vclans.VClans;
import org.vine.vclans.util.ColorUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Envia logs do Chat de Clã e do Banco/Upgrades para Webhooks do Discord
 * de forma totalmente assíncrona, ordenada e sem bloqueio da thread principal.
 */
public class DiscordWebhookHook {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "discord.com",
            "discordapp.com",
            "canary.discord.com",
            "ptb.discord.com"
    );
    private static final int COLOR_CHAT_RGB = 0x8B5CF6;
    private static final int COLOR_DEPOSIT_RGB = 0x10B981;
    private static final int COLOR_UPGRADE_RGB = 0xF59E0B;
    private static final long WARN_COOLDOWN_MS = 60_000L;

    private final VClans plugin;
    private final HttpClient httpClient;
    private final ThreadPoolExecutor executor;

    private volatile WebhookTarget chatTarget;
    private volatile WebhookTarget bankTarget;
    private volatile long lastWarnAtMs;

    public DiscordWebhookHook(VClans plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(128),
                r -> {
                    Thread t = new Thread(r, "vClans-DiscordWebhook");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        reloadConfig();
    }

    public void reloadConfig() {
        this.chatTarget = loadTarget("clan-chat.discord-webhook.", "vClans • Chat de Clã");
        this.bankTarget = loadTarget("bank.discord-webhook.", "vClans • Banco & Upgrades");
    }

    private WebhookTarget loadTarget(String basePath, String defaultUsername) {
        boolean cfgEnabled = plugin.getConfig().getBoolean(basePath + "enabled", false);
        String rawUrl = plugin.getConfig().getString(basePath + "url", "");
        URI parsed = parseWebhookUri(rawUrl);

        if (cfgEnabled && parsed == null && rawUrl != null && !rawUrl.isBlank()) {
            plugin.getLogger().warning("[DiscordWebhook] URL inválida em " + basePath + "url (use https://discord.com/api/webhooks/...).");
        }

        String rawName = plugin.getConfig().getString(basePath + "username", defaultUsername);
        String cleanName = ColorUtil.strip(rawName).trim();
        if (cleanName.isEmpty()) {
            cleanName = defaultUsername;
        }
        String username = capLength(cleanName, 80);

        String rawAvatar = plugin.getConfig().getString(basePath + "avatar-url", "");
        String avatarUrl = isValidHttpsUrl(rawAvatar) ? rawAvatar.trim() : "";
        boolean useEmbed = plugin.getConfig().getBoolean(basePath + "use-embed", true);

        return new WebhookTarget(cfgEnabled && parsed != null, parsed, username, avatarUrl, useEmbed);
    }

    public void logClanChat(String clanName, String clanTag, String roleName, String playerName, UUID playerUuid, String rawMessage) {
        WebhookTarget target = this.chatTarget;
        if (target == null || !target.enabled() || target.uri() == null || executor.isShutdown()) {
            return;
        }
        String cleanMsg = capLength(ColorUtil.strip(rawMessage).trim(), 1800);
        if (cleanMsg.isEmpty()) {
            return;
        }

        String cleanClan = capLength(ColorUtil.strip(clanName), 64);
        String cleanTag = capLength(ColorUtil.strip(clanTag), 16);
        String cleanRole = capLength(ColorUtil.strip(roleName), 32);
        String cleanPlayer = capLength(playerName == null ? "Desconhecido" : playerName, 32);
        String uuidStr = playerUuid != null ? playerUuid.toString() : "";

        String title = "Chat de Clã • [" + cleanTag + "] " + cleanClan;
        String description = "**[" + sanitizeMarkdown(cleanRole) + "] " + sanitizeMarkdown(cleanPlayer) + ":** " + sanitizeMarkdown(cleanMsg);
        String plainContent = "**[" + cleanTag + " | " + sanitizeMarkdown(cleanClan) + "] (" + sanitizeMarkdown(cleanRole) + ") "
                + sanitizeMarkdown(cleanPlayer) + ":** " + sanitizeMarkdown(cleanMsg);

        dispatchWebhook(target, title, description, plainContent, COLOR_CHAT_RGB, uuidStr);
    }

    public void logBankDeposit(
            String clanName,
            String clanTag,
            String playerName,
            UUID playerUuid,
            String currencyLabel,
            String amountFormatted,
            String newBalanceFormatted
    ) {
        WebhookTarget target = this.bankTarget;
        if (target == null || !target.enabled() || target.uri() == null || executor.isShutdown()) {
            return;
        }

        String cleanClan = capLength(ColorUtil.strip(clanName), 64);
        String cleanTag = capLength(ColorUtil.strip(clanTag), 16);
        String cleanPlayer = capLength(playerName == null ? "Desconhecido" : playerName, 32);
        String cleanCurrency = capLength(ColorUtil.strip(currencyLabel), 24);
        String cleanAmount = capLength(ColorUtil.strip(amountFormatted), 48);
        String cleanBalance = capLength(ColorUtil.strip(newBalanceFormatted), 64);
        String uuidStr = playerUuid != null ? playerUuid.toString() : "";

        String title = "Banco do Clã • Depósito de " + cleanCurrency + " • [" + cleanTag + "] " + cleanClan;
        String description = "**Jogador:** " + sanitizeMarkdown(cleanPlayer)
                + "\n**Valor Depositado:** `" + sanitizeMarkdown(cleanAmount) + "`"
                + "\n**Novo Saldo no Banco:** `" + sanitizeMarkdown(cleanBalance) + "`";
        String plainContent = "**[Banco • " + cleanTag + " | " + sanitizeMarkdown(cleanClan) + "]** "
                + sanitizeMarkdown(cleanPlayer) + " depositou **" + sanitizeMarkdown(cleanAmount)
                + "** (Saldo: `" + sanitizeMarkdown(cleanBalance) + "`).";

        dispatchWebhook(target, title, description, plainContent, COLOR_DEPOSIT_RGB, uuidStr);
    }

    public void logBankUpgrade(
            String clanName,
            String clanTag,
            String playerName,
            UUID playerUuid,
            String upgradeName,
            int oldLevel,
            int newLevel,
            String bonusSummary,
            String costFormatted,
            String remainingBalanceFormatted
    ) {
        WebhookTarget target = this.bankTarget;
        if (target == null || !target.enabled() || target.uri() == null || executor.isShutdown()) {
            return;
        }

        String cleanClan = capLength(ColorUtil.strip(clanName), 64);
        String cleanTag = capLength(ColorUtil.strip(clanTag), 16);
        String cleanPlayer = capLength(playerName == null ? "Desconhecido" : playerName, 32);
        String cleanUpgrade = capLength(ColorUtil.strip(upgradeName), 48);
        String cleanBonus = capLength(ColorUtil.strip(bonusSummary), 48);
        String cleanCost = capLength(ColorUtil.strip(costFormatted), 64);
        String cleanRemaining = capLength(ColorUtil.strip(remainingBalanceFormatted), 80);
        String uuidStr = playerUuid != null ? playerUuid.toString() : "";

        String title = "Upgrade do Clã • [" + cleanTag + "] " + cleanClan;
        String description = "**Jogador:** " + sanitizeMarkdown(cleanPlayer)
                + "\n**Bônus Evoluído:** **" + sanitizeMarkdown(cleanUpgrade) + "** (`Nv." + oldLevel + " ➔ Nv." + newLevel + "` • `" + sanitizeMarkdown(cleanBonus) + "`)"
                + "\n**Custo Pago:** `" + sanitizeMarkdown(cleanCost) + "`"
                + "\n**Saldo Restante no Banco:** `" + sanitizeMarkdown(cleanRemaining) + "`";
        String plainContent = "**[Upgrade • " + cleanTag + " | " + sanitizeMarkdown(cleanClan) + "]** "
                + sanitizeMarkdown(cleanPlayer) + " evoluiu **" + sanitizeMarkdown(cleanUpgrade)
                + "** para **Nv." + newLevel + "** (`" + sanitizeMarkdown(cleanBonus) + "`) por `" + sanitizeMarkdown(cleanCost) + "`.";

        dispatchWebhook(target, title, description, plainContent, COLOR_UPGRADE_RGB, uuidStr);
    }

    private void dispatchWebhook(
            WebhookTarget target,
            String embedTitle,
            String embedDescription,
            String plainContent,
            int colorRgb,
            String uuidStr
    ) {
        String timestampIso = Instant.now().toString();
        String payload = buildPayloadJson(target, embedTitle, embedDescription, plainContent, colorRgb, uuidStr, timestampIso);
        URI uri = target.uri();
        executor.execute(() -> sendPayload(uri, payload));
    }

    public void shutdown() {
        this.chatTarget = null;
        this.bankTarget = null;
        executor.shutdownNow();
    }

    private void sendPayload(URI uri, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("User-Agent", "vClans-DiscordWebhook/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 429) {
                long retryMs = parseRetryAfterMs(response);
                if (retryMs > 0 && retryMs <= 5_000L) {
                    Thread.sleep(retryMs);
                    HttpResponse<Void> retryResp = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    status = retryResp.statusCode();
                }
            }
            if (status < 200 || status >= 300) {
                warnThrottled("Discord Webhook retornou HTTP " + status + ".");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            warnThrottled("Falha ao enviar log para Discord Webhook: " + e.getMessage());
        }
    }

    private static long parseRetryAfterMs(HttpResponse<?> response) {
        String retryHeader = response.headers().firstValue("Retry-After").orElse("");
        if (retryHeader.isEmpty()) {
            return 0L;
        }
        try {
            double sec = Double.parseDouble(retryHeader.trim());
            return Math.max(0L, Math.round(sec * 1000.0));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarnAtMs >= WARN_COOLDOWN_MS) {
            lastWarnAtMs = now;
            plugin.getLogger().warning("[DiscordWebhook] " + message);
        }
    }

    private static String buildPayloadJson(
            WebhookTarget target,
            String embedTitle,
            String embedDescription,
            String plainContent,
            int colorRgb,
            String uuidStr,
            String timestampIso
    ) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        sb.append("\"username\":\"").append(escapeJson(target.username())).append('"');
        if (target.avatarUrl() != null && !target.avatarUrl().isEmpty()) {
            sb.append(",\"avatar_url\":\"").append(escapeJson(target.avatarUrl())).append('"');
        }
        sb.append(",\"allowed_mentions\":{\"parse\":[]}");

        if (target.useEmbed()) {
            sb.append(",\"embeds\":[{");
            sb.append("\"title\":\"").append(escapeJson(embedTitle)).append('"');
            sb.append(",\"description\":\"").append(escapeJson(embedDescription)).append('"');
            sb.append(",\"color\":").append(colorRgb);
            sb.append(",\"timestamp\":\"").append(escapeJson(timestampIso)).append('"');
            if (uuidStr != null && !uuidStr.isEmpty()) {
                sb.append(",\"footer\":{\"text\":\"UUID: ").append(escapeJson(uuidStr)).append("\"}");
            }
            sb.append("}]");
        } else {
            sb.append(",\"content\":\"").append(escapeJson(capLength(plainContent, 1950))).append('"');
        }

        sb.append('}');
        return sb.toString();
    }

    private static URI parseWebhookUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            String host = uri.getHost();
            if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                return null;
            }
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/api/webhooks/")) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isValidHttpsUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String sanitizeMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("@", "@\u200B").replace("`", "'");
    }

    private static String capLength(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private static String escapeJson(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private record WebhookTarget(
            boolean enabled,
            URI uri,
            String username,
            String avatarUrl,
            boolean useEmbed
    ) {
    }
}
