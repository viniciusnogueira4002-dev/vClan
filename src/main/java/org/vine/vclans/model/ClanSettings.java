package org.vine.vclans.model;

public class ClanSettings {

    private boolean friendlyFire;
    private boolean openJoin;
    private boolean notifications;

    public ClanSettings() {
        this.friendlyFire = false;
        this.openJoin = false;
        this.notifications = true;
    }

    public ClanSettings(boolean friendlyFire, boolean openJoin, boolean notifications) {
        this.friendlyFire = friendlyFire;
        this.openJoin = openJoin;
        this.notifications = notifications;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public boolean isOpenJoin() {
        return openJoin;
    }

    public void setOpenJoin(boolean openJoin) {
        this.openJoin = openJoin;
    }

    public boolean isNotifications() {
        return notifications;
    }

    public void setNotifications(boolean notifications) {
        this.notifications = notifications;
    }
}

