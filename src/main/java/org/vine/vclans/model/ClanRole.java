package org.vine.vclans.model;

import java.util.EnumSet;
import java.util.Set;

public enum ClanRole {
    LEADER(4, "&#FF5555Líder", "&8[&#FF5555Líder&8] ", EnumSet.allOf(ClanPermission.class)),
    CO_LEADER(3, "&#FFAA00Sub-Líder", "&8[&#FFAA00Sub-Líder&8] ", EnumSet.of(
            ClanPermission.INVITE, ClanPermission.KICK, ClanPermission.PROMOTE,
            ClanPermission.DEMOTE, ClanPermission.EDIT_INFO, ClanPermission.TAG_COLOR,
            ClanPermission.ALLIANCE, ClanPermission.DUEL, ClanPermission.WAR,
            ClanPermission.BANK_DEPOSIT, ClanPermission.BANK_WITHDRAW, ClanPermission.SETTINGS
    )),
    CAPTAIN(2, "&#55FFFFCapitão", "&8[&#55FFFFCapitão&8] ", EnumSet.of(
            ClanPermission.INVITE, ClanPermission.KICK, ClanPermission.DUEL,
            ClanPermission.BANK_DEPOSIT
    )),
    MEMBER(1, "&#AAAAAAMembro", "&8[&#AAAAAAMembro&8] ", EnumSet.of(
            ClanPermission.BANK_DEPOSIT
    ));

    private final int priority;
    private final String defaultDisplayName;
    private final String defaultPrefix;
    private final Set<ClanPermission> defaultPermissions;

    ClanRole(int priority, String defaultDisplayName, String defaultPrefix, Set<ClanPermission> defaultPermissions) {
        this.priority = priority;
        this.defaultDisplayName = defaultDisplayName;
        this.defaultPrefix = defaultPrefix;
        this.defaultPermissions = defaultPermissions;
    }

    public int getPriority() {
        return priority;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public String getDefaultPrefix() {
        return defaultPrefix;
    }

    public Set<ClanPermission> getDefaultPermissions() {
        return defaultPermissions;
    }

    public boolean isHigherThan(ClanRole other) {
        return this.priority > other.priority;
    }

    public boolean canTarget(ClanRole target) {
        return this.priority > target.priority && target != LEADER;
    }

    public ClanRole getNextHigher() {
        return switch (this) {
            case MEMBER -> CAPTAIN;
            case CAPTAIN -> CO_LEADER;
            default -> null;
        };
    }

    public ClanRole getNextLower() {
        return switch (this) {
            case CO_LEADER -> CAPTAIN;
            case CAPTAIN -> MEMBER;
            default -> null;
        };
    }
}

