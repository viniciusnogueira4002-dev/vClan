package org.vine.vclans.menu;

import org.vine.vclans.VClans;
import org.vine.vclans.model.Clan;
import org.vine.vclans.util.ColorUtil;
import org.vine.vclans.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public class TagColorMenu extends Menu {

    private final VClans plugin;
    private final Clan clan;

    private static final Map<String, TagColorEntry> COLORS = new LinkedHashMap<>();

    static {
        COLORS.put("&#FF5555", new TagColorEntry("Vermelho Carmesim", Material.RED_WOOL, "&#FF5555"));
        COLORS.put("&#FF7733", new TagColorEntry("Laranja Chama", Material.ORANGE_WOOL, "&#FF7733"));
        COLORS.put("&#FFAA00", new TagColorEntry("Ouro Radiante", Material.YELLOW_WOOL, "&#FFAA00"));
        COLORS.put("&#55FF55", new TagColorEntry("Verde Esmeralda", Material.LIME_WOOL, "&#55FF55"));
        COLORS.put("&#00AA00", new TagColorEntry("Verde Floresta", Material.GREEN_WOOL, "&#00AA00"));
        COLORS.put("&#55FFFF", new TagColorEntry("Ciano Glacial", Material.LIGHT_BLUE_WOOL, "&#55FFFF"));
        COLORS.put("&#00AAAA", new TagColorEntry("Azul Marinho", Material.CYAN_WOOL, "&#00AAAA"));
        COLORS.put("&#5555FF", new TagColorEntry("Azul Real", Material.BLUE_WOOL, "&#5555FF"));
        COLORS.put("&#FF55FF", new TagColorEntry("Rosa Magenta", Material.MAGENTA_WOOL, "&#FF55FF"));
        COLORS.put("&#AA00AA", new TagColorEntry("Roxo Noturno", Material.PURPLE_WOOL, "&#AA00AA"));
        COLORS.put("&#FFFFFF", new TagColorEntry("Branco Puro", Material.WHITE_WOOL, "&#FFFFFF"));
        COLORS.put("&#AAAAAA", new TagColorEntry("Cinza Platina", Material.LIGHT_GRAY_WOOL, "&#AAAAAA"));
    }

    public TagColorMenu(Player player, VClans plugin, Clan clan) {
        super(player, "&#FFAA00&lCORES DA TAG &8» &e" + clan.getTag(), 36);
        this.plugin = plugin;
        this.clan = clan;
    }

    @Override
    public void init() {
        for (int i = 0; i < size; i++) {
            setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        int slot = 10;
        for (Map.Entry<String, TagColorEntry> entry : COLORS.entrySet()) {
            if (slot == 17) slot = 19;
            if (slot == 26) break;

            String hex = entry.getKey();
            TagColorEntry colorData = entry.getValue();
            boolean isCurrent = clan.getTagColor().equalsIgnoreCase(hex);

            ItemBuilder builder = new ItemBuilder(colorData.material)
                    .name(hex + "&l" + colorData.name)
                    .lore("&7Prévia: &8[" + hex + clan.getTag() + "&8]",
                          "",
                          isCurrent ? "&#55FF55✔ Cor atualmente selecionada" : "&eClique para selecionar esta cor!");

            if (isCurrent) {
                builder.glow();
            }

            setItem(slot++, builder.build(), e -> {
                plugin.getClanManager().setTagColor(clan, hex);
                player.sendMessage(ColorUtil.color(plugin.getMessages().getString("prefix", "") +
                        plugin.getMessages().getString("clan.tag-color-changed", "")));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                } catch (Exception ignored) {}
                new TagColorMenu(player, plugin, clan).open();
            });
        }

        // Botão Voltar
        setItem(31, new ItemBuilder(Material.ARROW)
                .name("&cVoltar")
                .lore("&7Retornar às configurações do clã.")
                .build(), e -> new ClanSettingsMenu(player, plugin, clan).open());
    }

    private record TagColorEntry(String name, Material material, String hex) {}
}

