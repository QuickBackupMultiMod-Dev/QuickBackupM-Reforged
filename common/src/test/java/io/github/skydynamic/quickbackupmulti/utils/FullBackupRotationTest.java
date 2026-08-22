package io.github.skydynamic.quickbackupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullBackupRotationTest {
    private static StorageInfo fullBackup(String name, long timestamp) {
        return new StorageInfo(name, "Full backup", timestamp, false);
    }

    private static List<String> names(List<StorageInfo> backups) {
        return backups.stream().map(StorageInfo::getName).toList();
    }

    @Test
    void keepsEverythingWhenUnderTheLimit() {
        List<StorageInfo> backups = List.of(fullBackup("a", 1), fullBackup("b", 2));

        assertTrue(FullBackupRotation.selectExpired(backups, 4).isEmpty());
        assertTrue(FullBackupRotation.selectExpired(backups, 2).isEmpty());
    }

    @Test
    void dropsTheOldestFirst() {
        List<StorageInfo> backups = List.of(
            fullBackup("newest", 300),
            fullBackup("oldest", 100),
            fullBackup("middle", 200)
        );

        assertEquals(List.of("oldest"), names(FullBackupRotation.selectExpired(backups, 2)));
        assertEquals(List.of("oldest", "middle"), names(FullBackupRotation.selectExpired(backups, 1)));
    }

    @Test
    void dropsEveryBackupOverTheLimitInOneGo() {
        List<StorageInfo> backups = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            backups.add(fullBackup("backup-" + i, i));
        }

        // A single call has to bring an over-full store back to the limit. Removing just one per run
        // is what let a stale entry be re-selected forever (issue #55).
        List<StorageInfo> expired = FullBackupRotation.selectExpired(backups, 4);

        assertEquals(5, expired.size());
        assertEquals(List.of("backup-0", "backup-1", "backup-2", "backup-3", "backup-4"), names(expired));
    }

    @Test
    void rotationConvergesWhenRepeated() {
        List<StorageInfo> store = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            store.add(fullBackup("existing-" + i, i));
        }

        // Simulate makeFullBackup: prune to saveFullBackupCount - 1, then add the new full backup.
        for (int round = 0; round < 10; round++) {
            store.removeAll(FullBackupRotation.selectExpired(store, 4));
            store.add(fullBackup("new-" + round, 100 + round));
            assertEquals(5, store.size(), "full backup count must stay at the limit");
        }

        // Every original entry has been rotated out; nothing is stuck.
        assertEquals(
            List.of("new-5", "new-6", "new-7", "new-8", "new-9"),
            names(store).stream().sorted().toList()
        );
    }

    @Test
    void treatsNegativeKeepCountAsZero() {
        List<StorageInfo> backups = List.of(fullBackup("a", 1), fullBackup("b", 2));

        assertEquals(List.of("a", "b"), names(FullBackupRotation.selectExpired(backups, -1)));
    }

    @Test
    void toleratesEmptyAndNullInput() {
        assertTrue(FullBackupRotation.selectExpired(List.of(), 3).isEmpty());
        assertTrue(FullBackupRotation.selectExpired(null, 3).isEmpty());
    }
}
