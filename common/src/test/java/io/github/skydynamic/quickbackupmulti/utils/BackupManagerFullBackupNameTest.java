package io.github.skydynamic.quickbackupmulti.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BackupManagerFullBackupNameTest {
    @Test
    void fullBackupNameIncludesLevelAndTimestamp() {
        assertEquals("FullBackup-Server-1000", FullBackupNames.of("", 1000));
        assertEquals("FullBackup-world-1000", FullBackupNames.of("world", 1000));
        assertNotEquals(
            FullBackupNames.of("world", 1),
            FullBackupNames.of("world", 2));
    }
}
