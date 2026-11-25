package io.github.skydynamic.quickbackupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbackupmulti.config.PbsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Picks the backups a prune run should delete, following Proxmox Backup Server's retention rules.
 *
 * <p>This is a port of {@code mark_selections} from PBS's {@code pbs-datastore/src/prune.rs}: the
 * {@code keep-last/hourly/daily/weekly/monthly/yearly} options are applied in that order, each one
 * marking at most its own quota of <em>new</em> time buckets while scanning the whole list from the
 * newest backup backwards. Buckets that an earlier option already kept do not count against the
 * current option's quota, which is what makes "monthly 6, yearly 2" keep six distinct months plus two
 * distinct years rather than six months only.
 *
 * <p>Deliberately free of Minecraft types and of mod-wide static state so the selection can be
 * exercised directly by tests, and so it behaves identically on every Minecraft branch.
 */
public final class PbsPrunePolicy {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-Prune");

    /** PBS uses a per-bucket string; the local time is formatted into the zone being pruned. */
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH", Locale.ROOT);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM", Locale.ROOT);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);
    /**
     * ISO-8601 week, i.e. the same bucket as PBS's {@code strftime("%G/%V")}.
     *
     * <p>Built from {@link IsoFields} rather than {@code ofPattern("YYYYww")} on purpose: the pattern
     * letters resolve their {@code WeekFields} from the formatter's locale, so the same backup would
     * land in different weeks depending on the server's locale.
     */
    private static final DateTimeFormatter WEEK = new DateTimeFormatterBuilder()
        .appendValue(IsoFields.WEEK_BASED_YEAR, 4)
        .appendLiteral('-')
        .appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
        .toFormatter(Locale.ROOT);

    private PbsPrunePolicy() {
    }

    /**
     * @param expired            the backups to delete, newest first
     * @param droppedByMaxAmount how many backups {@code maxAmount} threw away <em>after</em> the PBS
     *                           options had kept them; non-zero means the configured retention is being
     *                           overruled and is worth warning about
     */
    public record Result(List<StorageInfo> expired, int droppedByMaxAmount) {
    }

    /**
     * Decides what {@code config} would remove from {@code backups}.
     *
     * @param zone      the zone the time buckets are computed in (PBS's {@code timezone} setting here)
     * @param nowMillis the current time; backups stamped in the future are always kept
     */
    public static Result selectExpired(PbsConfig config, List<StorageInfo> backups, ZoneId zone, long nowMillis) {
        if (config == null || !config.isEnabled() || backups == null || backups.isEmpty()) {
            return new Result(List.of(), 0);
        }
        ZoneId zoneId = zone == null ? ZoneId.systemDefault() : zone;

        List<StorageInfo> sorted = new ArrayList<>(backups);
        sorted.sort(Comparator.comparingLong(StorageInfo::getTimestamp).reversed());

        // A clock that jumped backwards must not wipe the backups it just wrote: PBS never prunes a
        // backup dated in the future, and neither do we.
        List<StorageInfo> past = new ArrayList<>();
        Set<String> protectedNames = new HashSet<>();
        for (StorageInfo backup : sorted) {
            if (backup.getTimestamp() > nowMillis) {
                protectedNames.add(backup.getName());
            } else {
                past.add(backup);
            }
        }

        Set<String> kept = markSelections(past, config, zoneId);

        List<StorageInfo> surviving = new ArrayList<>();
        for (StorageInfo backup : past) {
            if (kept.contains(backup.getName())) {
                surviving.add(backup);
            }
        }

        long lifetimeSeconds = maxLifeTimeSeconds(config.getMaxLifeTime());
        if (lifetimeSeconds > 0) {
            long lifetimeMillis = lifetimeSeconds * 1000L;
            surviving.removeIf(backup -> nowMillis - backup.getTimestamp() > lifetimeMillis);
        }

        int droppedByMaxAmount = 0;
        Integer maxAmount = config.getMaxAmount();
        if (maxAmount != null && maxAmount > 0 && surviving.size() > maxAmount) {
            droppedByMaxAmount = surviving.size() - maxAmount;
            surviving = new ArrayList<>(surviving.subList(0, maxAmount));
        }

        Set<String> survivingNames = new HashSet<>(protectedNames);
        for (StorageInfo backup : surviving) {
            survivingNames.add(backup.getName());
        }

        List<StorageInfo> expired = new ArrayList<>();
        for (StorageInfo backup : sorted) {
            if (!survivingNames.contains(backup.getName())) {
                expired.add(backup);
            }
        }
        return new Result(expired, droppedByMaxAmount);
    }

    /** The PBS retention options, in the order PBS applies them. */
    private static Set<String> markSelections(List<StorageInfo> backups, PbsConfig config, ZoneId zone) {
        Set<String> kept = new HashSet<>();
        markSelections(kept, backups, config.getLast(), StorageInfo::getName);
        markSelections(kept, backups, config.getHour(), backup -> format(backup, zone, HOUR));
        markSelections(kept, backups, config.getDay(), backup -> format(backup, zone, DAY));
        markSelections(kept, backups, config.getWeek(), backup -> format(backup, zone, WEEK));
        markSelections(kept, backups, config.getMonth(), backup -> format(backup, zone, MONTH));
        markSelections(kept, backups, config.getYear(), backup -> format(backup, zone, YEAR));
        return kept;
    }

    /**
     * One retention option: keep at most {@code limit} backups, one per distinct bucket, scanning from
     * the newest backup to the oldest.
     *
     * @param limit a negative limit means "no limit", which is how the mod exposes PBS's unlimited
     *              retention; zero keeps nothing
     */
    private static void markSelections(Set<String> kept, List<StorageInfo> backups, int limit,
                                       Function<StorageInfo, String> bucket) {
        if (limit == 0) {
            return;
        }
        long quota = limit < 0 ? Long.MAX_VALUE : limit;

        // Buckets already covered by an earlier option do not use up this option's quota, and their
        // backups are not re-considered: PBS's `already_included`.
        Set<String> alreadyIncluded = new HashSet<>();
        for (StorageInfo backup : backups) {
            if (kept.contains(backup.getName())) {
                alreadyIncluded.add(bucket.apply(backup));
            }
        }

        Set<String> includeHash = new HashSet<>();
        for (StorageInfo backup : backups) {
            if (kept.contains(backup.getName())) {
                continue;
            }
            String id = bucket.apply(backup);
            if (alreadyIncluded.contains(id)) {
                continue;
            }
            if (!includeHash.contains(id)) {
                if (includeHash.size() >= quota) {
                    break;
                }
                includeHash.add(id);
                kept.add(backup.getName());
            }
        }
    }

    private static String format(StorageInfo backup, ZoneId zone, DateTimeFormatter formatter) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(backup.getTimestamp()), zone).format(formatter);
    }

    /**
     * {@code maxLifeTime} in seconds, with {@code <= 0} meaning "no age limit".
     *
     * <p>Any duration that parses to zero counts as no limit: {@code "0s"} always did, but {@code "0m"}
     * used to mean "delete everything older than this very instant".
     */
    private static long maxLifeTimeSeconds(String maxLifeTime) {
        if (maxLifeTime == null || maxLifeTime.isBlank()) {
            return 0;
        }
        try {
            long seconds = DurationUtils.parseDurationToSeconds(maxLifeTime);
            return Math.max(seconds, 0);
        } catch (IllegalArgumentException e) {
            logger.warn("Unparseable maxLifeTime '{}', treating it as unlimited", maxLifeTime);
            return 0;
        }
    }
}
