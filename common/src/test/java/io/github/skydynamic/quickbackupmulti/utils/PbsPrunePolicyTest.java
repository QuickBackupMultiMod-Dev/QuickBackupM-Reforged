package io.github.skydynamic.quickbackupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbackupmulti.config.PbsConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the PBS retention port behind issue #60 ("backups a month old were deleted even
 * though monthly/yearly were configured").
 *
 * <p>Every test pins "now" and builds its own timeline, so the expectations below describe the same
 * thing the Proxmox prune simulator shows for those inputs — the timelines are small enough to check by
 * hand (and are meant to be pasted there when the rules are ever questioned again).
 */
class PbsPrunePolicyTest {
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, ZONE);

    @Test
    void monthlyAndYearlyRetentionSurvivesADenseHistory() {
        // Half a year of hourly backups: the shape a real server produces. With the old shared-iterator
        // scan the first tier consumed the whole list, so monthly/yearly could never keep anything.
        List<StorageInfo> backups = hourly(24 * 180, 1);
        PbsConfig config = tiers(10, 24, 7, 4, 6, 2);

        List<String> kept = keptNames(config, backups, NOW);

        assertTrue(kept.containsAll(List.of("b0", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9")),
            "the newest 10 backups are always kept, got " + kept);

        // "monthly 6" over 180 days reaches back into the sixth month, i.e. 150+ days.
        int oldestKeptAgeInHours = kept.stream().mapToInt(PbsPrunePolicyTest::indexOf).max().orElseThrow();
        assertTrue(oldestKeptAgeInHours >= 24 * 150,
            "monthly/yearly must reach back several months, oldest kept was " + oldestKeptAgeInHours + "h old");

        // And the prune still removes the vast majority of an hourly history.
        assertTrue(kept.size() < backups.size() / 10,
            "expected a small kept set out of " + backups.size() + ", got " + kept.size());
    }

    @Test
    void weeklyRetentionCountsDistinctIsoWeeks() {
        // Four Mondays, four different ISO weeks. "weekly 4" keeps all four; the old bucket key
        // (calendar year / 52 + 1) put all of them in one bucket and kept one.
        List<StorageInfo> backups = List.of(
            backup("w13", ZonedDateTime.of(2026, 3, 23, 3, 0, 0, 0, ZONE)),
            backup("w12", ZonedDateTime.of(2026, 3, 16, 3, 0, 0, 0, ZONE)),
            backup("w11", ZonedDateTime.of(2026, 3, 9, 3, 0, 0, 0, ZONE)),
            backup("w10", ZonedDateTime.of(2026, 3, 2, 3, 0, 0, 0, ZONE))
        );

        assertTrue(expiredNames(tiers(0, 0, 0, 4, 0, 0), backups, ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, ZONE))
            .isEmpty(), "four distinct weeks must all be kept by weekly 4");
    }

    @Test
    void weeklyRetentionTreatsTheIsoWeekAcrossNewYearAsOneBucket() {
        // 2026-12-31 (Thu) and 2027-01-02 (Sat) are both ISO week 2026-W53; 2026-12-28 is 2026-W53 too.
        // "weekly 1" therefore keeps exactly one of them, the newest.
        List<StorageInfo> backups = List.of(
            backup("jan2", ZonedDateTime.of(2027, 1, 2, 3, 0, 0, 0, ZONE)),
            backup("dec31", ZonedDateTime.of(2026, 12, 31, 3, 0, 0, 0, ZONE)),
            backup("dec28", ZonedDateTime.of(2026, 12, 28, 3, 0, 0, 0, ZONE))
        );

        List<String> expired = expiredNames(tiers(0, 0, 0, 1, 0, 0), backups,
            ZonedDateTime.of(2027, 1, 10, 0, 0, 0, 0, ZONE));

        assertEquals(List.of("dec31", "dec28"), expired);
    }

    @Test
    void earlierTiersDoNotUseUpTheNextTiersQuota() {
        List<StorageInfo> backups = daily(10);
        // daily 3: the three newest days. Hourly/weekly off.
        assertEquals(7, expiredNames(tiers(0, 0, 3, 0, 0, 0), backups, NOW).size());

        // last 5 covers the newest 5 days; daily 1 then still keeps the newest day it has not already
        // covered, i.e. day 6 — not nothing, which is what a naive shared counter produced.
        List<String> kept = keptNames(tiers(5, 0, 1, 0, 0, 0), backups, NOW);
        assertEquals(6, kept.size(), "expected 5 kept by `last` plus 1 more by `daily`, got " + kept);
        assertTrue(kept.contains("d5"), "the extra daily backup must be the next older one, got " + kept);
    }

    @Test
    void maxAmountTruncatesWhatTheTiersSelected() {
        List<StorageInfo> backups = hourly(100, 1);
        PbsConfig config = tiers(0, 100, 0, 0, 0, 0);
        config.maxAmount = 3;

        PbsPrunePolicy.Result result = PbsPrunePolicy.selectExpired(
            config, backups, ZONE, NOW.toInstant().toEpochMilli());

        assertEquals(3, 100 - result.expired().size(), "maxAmount caps the kept set");
        assertEquals(97, result.droppedByMaxAmount(), "and reports how much it overruled the tiers");
    }

    @Test
    void maxAmountZeroLetsTheTiersDecide() {
        // Ten weeks of dailies span three months, so "monthly 2" keeps two of them.
        List<StorageInfo> backups = daily(70);
        PbsConfig config = tiers(0, 0, 0, 0, 2, 0);
        config.maxAmount = 0;

        PbsPrunePolicy.Result result = PbsPrunePolicy.selectExpired(
            config, backups, ZONE, NOW.toInstant().toEpochMilli());

        assertEquals(0, result.droppedByMaxAmount());
        assertEquals(2, 70 - result.expired().size(), "monthly 2 keeps one backup per month");
    }

    @Test
    void maxLifeTimeDropsBackupsTheTiersKept() {
        List<StorageInfo> backups = daily(30);

        // A zero duration is "no limit" for every spelling; "0m" used to mean "delete everything".
        assertTrue(expiredNames(withLifeTime(tiers(-1, 0, 0, 0, 0, 0), "0s"), backups, NOW).isEmpty());
        assertTrue(expiredNames(withLifeTime(tiers(-1, 0, 0, 0, 0, 0), "0m"), backups, NOW).isEmpty());

        List<String> expired = expiredNames(withLifeTime(tiers(-1, 0, 0, 0, 0, 0), "7d"), backups, NOW);
        assertEquals(22, expired.size(), "everything older than seven days goes, got " + expired);
        assertFalse(expired.contains("d7"), "the seven newest days stay: " + expired);
    }

    @Test
    void unlimitedTierKeepsEverything() {
        for (int unlimited : new int[] {-1, Integer.MIN_VALUE}) {
            List<StorageInfo> backups = daily(40);
            assertTrue(expiredNames(tiers(unlimited, 0, 0, 0, 0, 0), backups, NOW).isEmpty(),
                "last=" + unlimited + " must keep every backup");
        }
    }

    @Test
    void zeroTierKeepsNothing() {
        assertEquals(40, expiredNames(tiers(0, 0, 0, 0, 0, 0), daily(40), NOW).size());
    }

    @Test
    void futureBackupsAreNeverPruned() {
        // A clock that jumped backwards must not delete backups it just wrote.
        List<StorageInfo> backups = new ArrayList<>(daily(3));
        backups.add(backup("fromTheFuture", NOW.plusDays(2)));

        List<String> expired = expiredNames(tiers(1, 0, 0, 0, 0, 0), backups, NOW);

        assertFalse(expired.contains("fromTheFuture"), "future backups are always kept: " + expired);
        assertEquals(List.of("d1", "d2"), expired);
    }

    @Test
    void disabledConfigPrunesNothing() {
        PbsConfig config = tiers(0, 0, 0, 0, 0, 0);
        config.enabled = false;

        assertTrue(expiredNames(config, daily(10), NOW).isEmpty());
    }

    @Test
    void raisingATierNeverShrinksTheKeptSet() {
        List<StorageInfo> backups = hourly(24 * 60, 1);

        List<String> previous = List.of();
        for (int month = 0; month <= 6; month++) {
            List<String> kept = keptNames(tiers(5, 12, 7, 4, month, 0), backups, NOW);
            assertTrue(kept.containsAll(previous),
                "month=" + month + " dropped something an earlier setting kept");
            previous = kept;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static StorageInfo backup(String name, ZonedDateTime at) {
        return new StorageInfo(name, "", at.toInstant().toEpochMilli(), true);
    }

    /** Hourly backups named b0 (newest) .. b{count-1}. */
    private static List<StorageInfo> hourly(int count, int stepHours) {
        List<StorageInfo> backups = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            backups.add(backup("b" + i, NOW.minus(Duration.ofHours((long) stepHours * i))));
        }
        return backups;
    }

    /** Daily backups named d0 (newest) .. d{count-1}, stamped at 12:00. */
    private static List<StorageInfo> daily(int count) {
        List<StorageInfo> backups = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            backups.add(backup("d" + i, NOW.minus(Duration.ofDays(i))));
        }
        return backups;
    }

    private static PbsConfig tiers(int last, int hour, int day, int week, int month, int year) {
        PbsConfig config = new PbsConfig();
        config.enabled = true;
        // 0 = "no cap", so the retention options are the only thing deciding; the cap itself is covered
        // by maxAmountTruncatesWhatTheTiersSelected.
        config.maxAmount = 0;
        config.last = last;
        config.hour = hour;
        config.day = day;
        config.week = week;
        config.month = month;
        config.year = year;
        return config;
    }

    private static PbsConfig withLifeTime(PbsConfig config, String lifeTime) {
        config.maxLifeTime = lifeTime;
        return config;
    }

    private static int indexOf(String name) {
        return Integer.parseInt(name.substring(1));
    }

    private static List<String> expiredNames(PbsConfig config, List<StorageInfo> backups, ZonedDateTime now) {
        return PbsPrunePolicy.selectExpired(config, backups, ZONE, now.toInstant().toEpochMilli())
            .expired()
            .stream()
            .map(StorageInfo::getName)
            .toList();
    }

    private static List<String> keptNames(PbsConfig config, List<StorageInfo> backups, ZonedDateTime now) {
        Set<String> expired = new HashSet<>(expiredNames(config, backups, now));
        return backups.stream().map(StorageInfo::getName).filter(name -> !expired.contains(name)).toList();
    }
}
