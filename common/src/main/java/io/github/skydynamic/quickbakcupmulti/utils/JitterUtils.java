package io.github.skydynamic.quickbakcupmulti.utils;

import java.util.Random;

public class JitterUtils {
    public static long parseJitterToSeconds(String input) {
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
            case "h", "hr", "hour", "hours" -> value * 3600;
            case "d", "day", "days" -> value * 86400;
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
        };
    }

    public static int getRandomJitterInSeconds(long maxJitterSeconds) {
        if (maxJitterSeconds <= 0) {
            return 0;
        }
        return new Random().nextInt((int) maxJitterSeconds + 1);
    }

    public static int parseAndRandom(String input) {
        long parse = parseJitterToSeconds(input);
        return getRandomJitterInSeconds((int) parse);
    }
}
