package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes {@code config/QuickBackupMulti.json} before the game first starts.
 *
 * <p>Pre-writing it is what makes the run deterministic. Left alone the mod would default to
 * {@code lang=zh_cn} (so every user-facing string an assertion might match changes),
 * {@code checkUpdate=true} (a network call to Modrinth on every boot, and a source of unrelated
 * failures when it is unreachable) and {@code autoRestartMode=DEFAULT} (which on NeoForge spawns a
 * detached JVM the harness cannot observe).
 *
 * <p>The field names here mirror {@code ModConfig.ConfigStorage} exactly. Gson leaves absent fields at
 * their defaults, so only what the harness actually cares about is written — but a rename in the mod
 * would silently stop taking effect, which is why {@code SmokeScenario} asserts on the effects of
 * these values rather than trusting them.
 */
public final class ModConfigFile {
    private final Map<String, Object> root = new LinkedHashMap<>();
    private final Map<String, Object> scheduleBackup = new LinkedHashMap<>();
    private final Map<String, Object> prune = new LinkedHashMap<>();
    private final Map<String, Object> pruneRegular = new LinkedHashMap<>();
    private final Map<String, Object> pruneTemporary = new LinkedHashMap<>();
    private final Map<String, Object> database = new LinkedHashMap<>();
    private final Map<String, Object> databaseBackup = new LinkedHashMap<>();

    private ModConfigFile() {
    }

    /**
     * The baseline every scenario starts from: English strings, no update check, no schedules, and no
     * automatic restart.
     */
    public static ModConfigFile deterministic() {
        ModConfigFile c = new ModConfigFile();
        // Assertions must not depend on the machine's locale or on translation churn.
        c.root.put("lang", "en_us");
        // A boot must not depend on Modrinth being reachable.
        c.root.put("checkUpdate", false);
        // DEFAULT restarts in-process on Fabric but launches a *detached* JVM on NeoForge and exits, so
        // the harness would lose track of the server. Scenarios that test restart opt in explicitly.
        c.root.put("autoRestartMode", "DISABLE");
        c.root.put("clientAutoReJoinWorld", false);
        c.root.put("storagePath", "./QuickBackupMulti");
        c.root.put("cacheDatabase", false);
        c.root.put("fullBackupInterval", 5);
        c.root.put("saveFullBackupCount", 5);
        c.root.put("ignoredFiles", new String[0]);
        c.root.put("ignoredFolders", new String[0]);

        // Nothing may fire on a timer while a scenario is asserting on backup counts.
        c.scheduleBackup.put("enabled", false);
        c.scheduleBackup.put("interval", "3h");
        c.scheduleBackup.put("crontab", null);
        c.scheduleBackup.put("resetTimerOnBackup", true);
        c.scheduleBackup.put("requireOnlinePlayers", false);

        c.prune.put("enabled", false);
        c.prune.put("interval", "3h");
        c.prune.put("crontab", null);
        c.pruneRegular.put("enabled", false);
        c.pruneTemporary.put("enabled", false);

        c.databaseBackup.put("enabled", false);
        return c;
    }

    /** Sets a top-level field, for scenarios that need to diverge from the baseline. */
    public ModConfigFile with(String key, Object value) {
        root.put(key, value);
        return this;
    }

    /** Enables the periodic backup schedule at {@code interval} (a duration string such as "5s"). */
    public ModConfigFile withScheduleBackup(String interval, boolean resetTimerOnBackup) {
        scheduleBackup.put("enabled", true);
        scheduleBackup.put("interval", interval);
        scheduleBackup.put("crontab", null);
        scheduleBackup.put("resetTimerOnBackup", resetTimerOnBackup);
        return this;
    }

    /** Enables the database-backup schedule, used to prove one schedule does not disturb another. */
    public ModConfigFile withDatabaseSchedule(String interval) {
        databaseBackup.put("enabled", true);
        databaseBackup.put("interval", interval);
        databaseBackup.put("crontab", null);
        return this;
    }

    /** How many incremental backups trigger a new full backup, and how many fulls to keep. */
    public ModConfigFile withFullBackups(int interval, int keep) {
        root.put("fullBackupInterval", interval);
        root.put("saveFullBackupCount", keep);
        return this;
    }

    /** Writes the file into a run directory's {@code config/}, which both loaders resolve to. */
    public void writeTo(Path runDir) throws IOException {
        Path configDir = Files.createDirectories(runDir.resolve("config"));
        Files.writeString(configDir.resolve("QuickBackupMulti.json"), toJson());
    }

    String toJson() {
        Map<String, Object> out = new LinkedHashMap<>(root);
        Map<String, Object> pruneOut = new LinkedHashMap<>(prune);
        pruneOut.put("regularBackup", pruneRegular);
        pruneOut.put("temporaryBackup", pruneTemporary);
        Map<String, Object> databaseOut = new LinkedHashMap<>(database);
        databaseOut.put("backup", databaseBackup);
        out.put("scheduleBackup", scheduleBackup);
        out.put("prune", pruneOut);
        out.put("database", databaseOut);
        return write(out);
    }

    @SuppressWarnings("unchecked")
    private static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<String, Object>) map).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(quote(String.valueOf(e.getKey()))).append(':').append(write(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Object[] array) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(write(array[i]));
            }
            return sb.append(']').toString();
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
