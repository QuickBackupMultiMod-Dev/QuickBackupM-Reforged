package io.github.skydynamic.quickbackupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;

import java.util.Comparator;
import java.util.List;

/**
 * Decides which full backups a rotation should drop.
 *
 * <p>Deliberately free of Minecraft types and of mod-wide static state so the selection can be
 * exercised directly by tests.
 */
public final class FullBackupRotation {
    private FullBackupRotation() {
    }

    /**
     * The oldest entries of {@code fullBackups} that have to go for at most {@code keepCount} to
     * remain, oldest first. Empty when nothing needs removing.
     */
    public static List<StorageInfo> selectExpired(List<StorageInfo> fullBackups, int keepCount) {
        int keep = Math.max(keepCount, 0);
        if (fullBackups == null || fullBackups.size() <= keep) {
            return List.of();
        }
        return fullBackups.stream()
            .sorted(Comparator.comparingLong(StorageInfo::getTimestamp))
            .limit(fullBackups.size() - (long) keep)
            .toList();
    }
}
