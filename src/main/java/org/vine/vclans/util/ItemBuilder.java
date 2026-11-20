package org.vine.vclans.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.setDisplayName(ColorUtil.color(name));
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            meta.setLore(ColorUtil.color(lines));
        }
        return this;
    }

    public ItemBuilder addLoreLine(String line) {
        if (meta != null && line != null) {
            List<String> current = meta.getLore();
            if (current == null) current = new ArrayList<>();
            current.add(ColorUtil.color(line));
            meta.setLore(current);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder skullOwner(UUID uuid) {
        if (meta instanceof SkullMeta skullMeta && uuid != null) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            skullMeta.setOwningPlayer(player);
        }
        return this;
    }

    public ItemBuilder skullOwner(String name) {
        if (meta instanceof SkullMeta skullMeta && name != null) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(name);
            skullMeta.setOwningPlayer(player);
        }
        return this;
    }

    public ItemBuilder glow() {
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder hideAllFlags() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}

