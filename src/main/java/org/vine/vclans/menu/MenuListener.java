package org.vine.vclans.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()) {
                menu.handleClick(event);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}

