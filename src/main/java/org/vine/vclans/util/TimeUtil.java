package org.vine.vclans.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private TimeUtil() {}

    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        Duration d = Duration.ofSeconds(seconds);
        long days = d.toDays();
        long hours = d.toHours() % 24;
        long minutes = d.toMinutes() % 60;
        long secs = d.getSeconds() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    public static String formatDate(Instant instant) {
        if (instant == null) return "Nunca";
        return DATE_FORMATTER.format(instant);
    }
}

