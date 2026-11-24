package org.vine.vclans.hook;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.model.ClanMember;
import org.vine.vclans.model.ClanRole;
import org.vine.vclans.model.ClanUpgrades.UpgradeType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class PlaceholderHook extends PlaceholderExpansion {

    private final VClans plugin;
    private final String identifier;

    public PlaceholderHook(VClans plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    public PlaceholderHook(VClans plugin) {
        this(plugin, "vclans");
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rede Eclypse";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("top_war_")) {
            String[] parts = params.split("_");
            if (parts.length >= 4) {
                try {
                    int rank = Integer.parseInt(parts[2]);
                    String type = parts[3].toLowerCase();
                    List<Clan> top = plugin.getSeasonWarManager().getTopSeasonClans(10);
                    if (rank > 0 && rank <= top.size()) {
                        Clan c = top.get(rank - 1);
                        return switch (type) {
                            case "name" -> c.getName();
                            case "tag" -> c.getTag();
                            case "coloredtag" -> c.getColoredTag();
                            case "points" -> String.valueOf(c.getSeasonWarPoints());
                            default -> "";
                        };
                    } else {
                        return "---";
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (params.startsWith("top_")) {
            // Suporte para %vclans_top_1_name%, etc.
            String[] parts = params.split("_");
            if (parts.length >= 3) {
                try {
                    int rank = Integer.parseInt(parts[1]);
                    String type = parts[2].toLowerCase();
                    List<Clan> top = plugin.getClanManager().getTopClansByPoints(10);
                    if (rank > 0 && rank <= top.size()) {
                        Clan c = top.get(rank - 1);
                        return switch (type) {
                            case "name" -> c.getName();
                            case "tag" -> c.getTag();
                            case "coloredtag" -> c.getColoredTag();
                            case "points" -> String.valueOf(c.getStats().getPoints());
                            default -> "";
                        };
                    } else {
                        return "---";
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (player == null) return "";

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());

        if (clan == null) {
            return switch (params.toLowerCase()) {
                case "has_clan" -> "false";
                case "prefix" -> plugin.getConfig().getString("chat.no-clan-tag", "");
                case "tag", "tag_colored", "tag_formatted", "chat_prefix", "nametag", "head", "tag_head", "abovename", "sigla", "sigla_colorida", "name", "role", "role_prefix", "leader", "badge", "badge_rank" -> "";
                case "points", "kills", "deaths", "members_count", "duel_wins", "duel_losses", "war_wins", "war_losses",
                        "bank_tp", "season_war_points", "badge_tier" -> "0";
                case "bank_money" -> "0.00";
                case "mult_tp", "mult_money", "mult_war" -> "0%";
                case "kdr" -> "0.00";
                default -> "";
            };
        }

        ClanMember member = clan.getMember(player.getUniqueId());
        ClanRole role = member != null ? member.getRole() : ClanRole.MEMBER;
        ClanMember leader = clan.getMember(clan.getLeaderUuid());
        String leaderName = leader != null ? leader.getLastKnownName() : "Desconhecido";

        String encodedBadge = !clan.getTagStyle().isEmpty()
                ? EclypseCoreBadgeHook.encodeChatBadge(clan.getTag(), clan.getTagStyle())
                : "";

        String tagPrefix = !encodedBadge.isEmpty()
                ? (encodedBadge + " ")
                : org.vine.vclans.util.ColorUtil.color(
                plugin.getConfig().getString("chat.clan-tag-format", "&8[{color}{tag}&8] ")
                        .replace("{color}", clan.getTagColor())
                        .replace("{tag}", clan.getTag())
        );

        String nametagPrefix = org.vine.vclans.util.ColorUtil.color(
                plugin.getConfig().getString("nametag.prefix-format", "&8[{color}{tag}&8] &r")
                        .replace("{color}", clan.getTagColor())
                        .replace("{tag}", clan.getTag())
        );

        return switch (params.toLowerCase()) {
            case "has_clan" -> "true";
            case "clan_id" -> clan.getId().toString();
            case "name" -> clan.getName();
            case "tag", "sigla" -> clan.getTag();
            case "tag_colored", "sigla_colorida" -> clan.getColoredTag();
            case "tag_formatted" -> clan.getFormattedTag();
            case "badge" -> encodedBadge;
            case "badge_tier" -> String.valueOf(clan.getBadgeTier());
            case "badge_rank" -> clan.getBadgeRankName();
            case "prefix" -> tagPrefix;
            case "nametag", "head", "tag_head", "abovename" -> nametagPrefix;
            case "chat_prefix" -> (role.getDefaultPrefix().isEmpty() ? "" : role.getDefaultPrefix() + " ") + tagPrefix;
            case "role" -> role.getDefaultDisplayName();
            case "role_prefix" -> role.getDefaultPrefix();
            case "leader" -> leaderName;
            case "points" -> String.valueOf(clan.getStats().getPoints());
            case "kdr" -> clan.getStats().getFormattedKdr();
            case "kills" -> String.valueOf(clan.getStats().getKills());
            case "deaths" -> String.valueOf(clan.getStats().getDeaths());
            case "members_count" -> String.valueOf(clan.getMembersCount());
            case "members_max" -> String.valueOf(plugin.getBankUpgradeManager().getMaxMembers(clan));
            case "bank_tp" -> String.valueOf(clan.getBank().getTp());
            case "bank_money" -> String.format(Locale.US, "%.2f", clan.getBank().getMoney());
            case "mult_tp" -> Math.round(plugin.getBankUpgradeManager().getBonusValue(UpgradeType.TP_MULTIPLIER, clan.getUpgrades().getTpMultiplierLevel())) + "%";
            case "mult_money" -> Math.round(plugin.getBankUpgradeManager().getBonusValue(UpgradeType.MONEY_MULTIPLIER, clan.getUpgrades().getMoneyMultiplierLevel())) + "%";
            case "mult_war" -> Math.round(plugin.getBankUpgradeManager().getBonusValue(UpgradeType.WAR_MULTIPLIER, clan.getUpgrades().getWarMultiplierLevel())) + "%";
            case "season_war_points" -> String.valueOf(clan.getSeasonWarPoints());
            case "duel_wins" -> String.valueOf(clan.getStats().getDuelWins());
            case "duel_losses" -> String.valueOf(clan.getStats().getDuelLosses());
            case "war_wins" -> String.valueOf(clan.getStats().getWarWins());
            case "war_losses" -> String.valueOf(clan.getStats().getWarLosses());
            default -> null;
        };
    }
}
