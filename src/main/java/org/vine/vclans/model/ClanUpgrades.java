package org.vine.vclans.model;

/**
 * Níveis dos upgrades comprados com recursos do Banco do Clã.
 */
public class ClanUpgrades {

    public enum UpgradeType {
        TP_MULTIPLIER(1, "tp-multiplier", "Multiplicador de TP"),
        MONEY_MULTIPLIER(2, "money-multiplier", "Multiplicador de Money"),
        MEMBER_LIMIT(3, "member-limit", "Limite de Membros"),
        WAR_MULTIPLIER(4, "war-multiplier", "Amplificador de Guerra");

        private final int id;
        private final String configKey;
        private final String displayName;

        UpgradeType(int id, String configKey, String displayName) {
            this.id = id;
            this.configKey = configKey;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static UpgradeType fromId(int id) {
            for (UpgradeType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }

        public static UpgradeType fromKey(String key) {
            if (key == null) return null;
            String normalized = key.trim().toLowerCase();
            return switch (normalized) {
                case "tp", "tp-multiplier", "tpmult", "1" -> TP_MULTIPLIER;
                case "money", "dinheiro", "money-multiplier", "moneymult", "2" -> MONEY_MULTIPLIER;
                case "membros", "membro", "member", "members", "member-limit", "vagas", "3" -> MEMBER_LIMIT;
                case "guerra", "war", "war-multiplier", "warmult", "4" -> WAR_MULTIPLIER;
                default -> null;
            };
        }
    }

    private int tpMultiplierLevel;
    private int moneyMultiplierLevel;
    private int memberLimitLevel;
    private int warMultiplierLevel;

    public ClanUpgrades() {
        this(0, 0, 0, 0);
    }

    public ClanUpgrades(int tpMultiplierLevel, int moneyMultiplierLevel, int memberLimitLevel, int warMultiplierLevel) {
        this.tpMultiplierLevel = Math.max(0, tpMultiplierLevel);
        this.moneyMultiplierLevel = Math.max(0, moneyMultiplierLevel);
        this.memberLimitLevel = Math.max(0, memberLimitLevel);
        this.warMultiplierLevel = Math.max(0, warMultiplierLevel);
    }

    public int getTpMultiplierLevel() {
        return tpMultiplierLevel;
    }

    public void setTpMultiplierLevel(int tpMultiplierLevel) {
        this.tpMultiplierLevel = Math.max(0, tpMultiplierLevel);
    }

    public int getMoneyMultiplierLevel() {
        return moneyMultiplierLevel;
    }

    public void setMoneyMultiplierLevel(int moneyMultiplierLevel) {
        this.moneyMultiplierLevel = Math.max(0, moneyMultiplierLevel);
    }

    public int getMemberLimitLevel() {
        return memberLimitLevel;
    }

    public void setMemberLimitLevel(int memberLimitLevel) {
        this.memberLimitLevel = Math.max(0, memberLimitLevel);
    }

    public int getWarMultiplierLevel() {
        return warMultiplierLevel;
    }

    public void setWarMultiplierLevel(int warMultiplierLevel) {
        this.warMultiplierLevel = Math.max(0, warMultiplierLevel);
    }

    public int getLevel(UpgradeType type) {
        if (type == null) return 0;
        return switch (type) {
            case TP_MULTIPLIER -> tpMultiplierLevel;
            case MONEY_MULTIPLIER -> moneyMultiplierLevel;
            case MEMBER_LIMIT -> memberLimitLevel;
            case WAR_MULTIPLIER -> warMultiplierLevel;
        };
    }

    public void setLevel(UpgradeType type, int level) {
        if (type == null) return;
        int safeLevel = Math.max(0, level);
        switch (type) {
            case TP_MULTIPLIER -> this.tpMultiplierLevel = safeLevel;
            case MONEY_MULTIPLIER -> this.moneyMultiplierLevel = safeLevel;
            case MEMBER_LIMIT -> this.memberLimitLevel = safeLevel;
            case WAR_MULTIPLIER -> this.warMultiplierLevel = safeLevel;
        }
    }
}
