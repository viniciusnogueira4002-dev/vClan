package org.vine.vclans.util;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN_1 = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_PATTERN_2 = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private ColorUtil() {}

    public static String color(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        // Caminho rápido para mensagens comuns: sem alocação de Matcher ou StringBuilder
        if (!message.contains("&#") && !message.contains("<#")) {
            return ChatColor.translateAlternateColorCodes('&', message);
        }

        Matcher matcher = HEX_PATTERN_1.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 32);
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(buffer);

        matcher = HEX_PATTERN_2.matcher(buffer.toString());
        StringBuilder buffer2 = new StringBuilder(buffer.length() + 16);
        while (matcher.find()) {
            String hexCode = matcher.group(1);
            matcher.appendReplacement(buffer2, ChatColor.of("#" + hexCode).toString());
        }
        matcher.appendTail(buffer2);

        return ChatColor.translateAlternateColorCodes('&', buffer2.toString());
    }

    public static List<String> color(List<String> lines) {
        if (lines == null) return new ArrayList<>();
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(color(line));
        }
        return result;
    }

    public static String strip(String message) {
        if (message == null) return "";
        return ChatColor.stripColor(color(message));
    }
}

