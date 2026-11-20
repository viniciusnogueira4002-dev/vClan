package org.vine.vclans.menu;

import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ConfirmationMenu extends Menu {

    private final String actionDescription;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmationMenu(Player player, String actionDescription, Runnable onConfirm, Runnable onCancel) {
        super(player, "&#FF5555&lCONFIRMAÇÃO NECESSÁRIA", 27);
        this.actionDescription = actionDescription;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void init() {
        // Preencher bordas com vidro cinza
        ItemStack border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) {
            setItem(i, border);
        }

        // Informação central
        setItem(13, new ItemBuilder(Material.PAPER)
                .name("&#FFAA00&lAtenção!")
                .lore("&7Você está prestes a:",
                      " &f" + actionDescription,
                      "",
                      "&eDeseja realmente continuar?")
                .build());

        // Botão Confirmar (Verde)
        setItem(11, new ItemBuilder(Material.LIME_CONCRETE)
                .name("&#55FF55&lCONFIRMAR")
                .lore("&7Clique para executar a ação.")
                .build(), e -> {
            player.closeInventory();
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } catch (Exception ignored) {}
            if (onConfirm != null) onConfirm.run();
        });

        // Botão Cancelar (Vermelho)
        setItem(15, new ItemBuilder(Material.RED_CONCRETE)
                .name("&#FF5555&lCANCELAR")
                .lore("&7Clique para voltar sem alterar nada.")
                .build(), e -> {
            player.closeInventory();
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            } catch (Exception ignored) {}
            if (onCancel != null) onCancel.run();
        });
    }
}

