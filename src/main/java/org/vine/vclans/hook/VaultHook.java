package org.vine.vclans.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private Economy economy;
    private final boolean enabled;

    public VaultHook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
            }
        }
        this.enabled = this.economy != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasMoney(Player player, double amount) {
        if (!enabled || amount <= 0) return true;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!enabled || amount <= 0) return true;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public void deposit(Player player, double amount) {
        if (!enabled || amount <= 0) return;
        economy.depositPlayer(player, amount);
    }
}

