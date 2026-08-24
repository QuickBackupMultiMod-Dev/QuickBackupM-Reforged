package io.github.skydynamic.quickbackupmulti.utils;

/** Builds unique full-backup names without touching Minecraft or mod statics. */
public final class FullBackupNames {
    private FullBackupNames() {
    }

    public static String of(String levelId, long epochMillis) {
        String level = (levelId == null || levelId.isEmpty()) ? "Server" : levelId;
        return "FullBackup-" + level + "-" + epochMillis;
    }
}
