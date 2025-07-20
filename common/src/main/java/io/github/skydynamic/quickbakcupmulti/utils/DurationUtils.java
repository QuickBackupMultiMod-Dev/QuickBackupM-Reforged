package io.github.skydynamic.quickbakcupmulti.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class DurationUtils {
    public static long parseDurationToSeconds(String input) {
        var pattern = java.util.regex.Pattern.compile("(\\d+)([a-zA-Z]+)");
        var matcher = pattern.matcher(input.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid jitter format: " + input);
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        return switch (unit) {
            case "ms", "milli", "millis" -> value / 1000;
            case "s", "sec", "second", "seconds" -> value;
            case "m", "min", "minute", "minutes" -> value * 60;
            case "h", "hr", "hour", "hours" -> value * 60 * 60;
            case "d", "day", "days" -> value * 24 * 60 * 60;
            case "w", "week", "weeks" -> value * 7 * 24 * 60 * 60;
            case "mo", "mon", "month", "months" -> value * 30 * 24 * 60 * 60;
            case "y", "yr", "year", "years" -> value * 365 * 24 * 60 * 60;
            default -> 0;
        };
    }

    public static String formatByUnit(long timestamp, String unit, ZoneId zoneId) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId);
        return switch (unit) {
            case "hour" -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
            case "day" -> dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "week" -> String.format("%d-%02d", dateTime.getYear(), dateTime.getYear() / 52 + 1);
            case "month" -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "year" -> String.valueOf(dateTime.getYear());
            default -> "";
        };
    }

    public static int getRandomDurationInSeconds(long maxJitterSeconds) {
        if (maxJitterSeconds <= 0) {
            return 0;
        }
        return new Random().nextInt((int) maxJitterSeconds + 1);
    }

    public static int parseAndRandom(String input) {
        long parse = parseDurationToSeconds(input);
        return getRandomDurationInSeconds((int) parse);
    }
}
