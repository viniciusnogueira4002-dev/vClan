package org.vine.vclans.menu;

import org.vine.vclans.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Menu implements InventoryHolder {

    protected final Player player;
    protected final String title;
    protected final int size;
    protected final Inventory inventory;
    protected final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Menu(Player player, String title, int size) {
        this.player = player;
        this.title = title;
        this.size = size;
        this.inventory = Bukkit.createInventory(this, size, ColorUtil.color(title));
    }

    public abstract void init();

    public void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    public void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    public void open() {
        init();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}

