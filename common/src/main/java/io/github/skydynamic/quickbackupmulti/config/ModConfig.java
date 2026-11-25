package io.github.skydynamic.quickbackupmulti.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import io.github.skydynamic.increment.storage.lib.manager.IConfig;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModConfig implements IConfig {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-Config");

    /** Bounds for {@link ConfigStorage#restoreCountdownSeconds}; the getter clamps to these. */
    public static final int MIN_RESTORE_COUNTDOWN_SECONDS = 0;
    public static final int MAX_RESTORE_COUNTDOWN_SECONDS = 3600;

    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
        // Gson maps an unknown enum name to null, and a literal null to null as well; the config is
        // hand-edited often enough that either one used to reach a `switch` and throw.
        .registerTypeAdapter(AutoRestartMode.class, (JsonDeserializer<AutoRestartMode>) (json, type, context) ->
            AutoRestartMode.fromConfigValue(json == null || json.isJsonNull() ? null : json.getAsString()))
        .create();

    @Setter @Getter
    private ConfigStorage config = new ConfigStorage();

    private final Path path;

    public ModConfig(final Path path) {
        this.path = path;
    }

    public boolean save() {
        if (!Files.exists(path)) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                logger.error("Save {} error: create file failed.", path, e);
                return false;
            }
        }
        try (BufferedWriter bfw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(getConfig(), bfw);
        } catch (IOException e) {
            logger.error("Save {} error: write file failed.", path, e);
            return false;
        }
        return true;
    }

    public boolean load() {
        if (path == null) {
            logger.error("Config Path is null");
            return false;
        }

        if (!Files.exists(path)) {
            return save();
        }

        try (BufferedReader bfr = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ConfigStorage loaded = gson.fromJson(bfr, ConfigStorage.class);
            // An empty file parses to null, which used to leave the mod with no config object at all.
            if (loaded == null) {
                logger.warn("Config {} is empty, keeping the built-in defaults.", path);
                setConfig(new ConfigStorage());
                return save();
            }
            // Gson leaves an explicit `null` in place, so a partially written file (or a user who
            // deleted a section) would NPE on the first access. Fill those back in.
            loaded.fillMissingSections();
            setConfig(loaded);
        } catch (IOException e) {
            logger.error("Load {} error: read file failed.", path, e);
            return false;
        } catch (RuntimeException e) {
            // Malformed JSON must not take the whole server down on boot.
            logger.error("Load {} error: malformed config, keeping the built-in defaults.", path, e);
            setConfig(new ConfigStorage());
            return false;
        }
        return true;
    }

    public ModConfig copy() {
        ModConfig newConfig = new ModConfig(this.path);
        ConfigStorage newStorage = new ConfigStorage();

        newStorage.checkUpdate = this.config.checkUpdate;
        newStorage.lang = this.config.lang;
        newStorage.autoRestartMode = this.config.autoRestartMode;
        newStorage.clientAutoReJoinWorld = this.config.clientAutoReJoinWorld;
        newStorage.storagePath = this.config.storagePath;
        newStorage.cacheDatabase = this.config.cacheDatabase;
        newStorage.fullBackupInterval = this.config.fullBackupInterval;
        newStorage.saveFullBackupCount = this.config.saveFullBackupCount;
        newStorage.relaunchCommand = this.config.relaunchCommand;
        newStorage.restoreCountdownSeconds = this.config.restoreCountdownSeconds;

        newStorage.ignoredFiles = new ArrayList<>(this.config.ignoredFiles);
        newStorage.ignoredFolders = new ArrayList<>(this.config.ignoredFolders);

        newStorage.scheduleBackup = new ScheduleBackupConfig();
        newStorage.scheduleBackup.enabled = this.config.scheduleBackup.enabled;
        newStorage.scheduleBackup.interval = this.config.scheduleBackup.interval;
        newStorage.scheduleBackup.crontab = this.config.scheduleBackup.crontab;
        newStorage.scheduleBackup.resetTimerOnBackup = this.config.scheduleBackup.resetTimerOnBackup;
        newStorage.scheduleBackup.requireOnlinePlayers = this.config.scheduleBackup.requireOnlinePlayers;
        newStorage.scheduleBackup.requireOnlinePlayersIgnoreCarpetFakePlayer = this.config.scheduleBackup.requireOnlinePlayersIgnoreCarpetFakePlayer;
        newStorage.scheduleBackup.requireOnlinePlayersBlacklist = this.config.scheduleBackup.requireOnlinePlayersBlacklist;
        newStorage.scheduleBackup.notifyPlayers = this.config.scheduleBackup.notifyPlayers;

        newStorage.prune = new PruneScheduleConfig();
        newStorage.prune.enabled = this.config.prune.enabled;
        newStorage.prune.interval = this.config.prune.interval;
        newStorage.prune.crontab = this.config.prune.crontab;
        newStorage.prune.timezoneOverride = this.config.prune.timezoneOverride;

        newStorage.prune.regularBackup = new PbsConfig();
        newStorage.prune.regularBackup.enabled = this.config.prune.regularBackup.enabled;
        newStorage.prune.regularBackup.maxAmount = this.config.prune.regularBackup.maxAmount;
        newStorage.prune.regularBackup.maxLifeTime = this.config.prune.regularBackup.maxLifeTime;
        newStorage.prune.regularBackup.last = this.config.prune.regularBackup.last;
        newStorage.prune.regularBackup.hour = this.config.prune.regularBackup.hour;
        newStorage.prune.regularBackup.day = this.config.prune.regularBackup.day;
        newStorage.prune.regularBackup.week = this.config.prune.regularBackup.week;
        newStorage.prune.regularBackup.month = this.config.prune.regularBackup.month;
        newStorage.prune.regularBackup.year = this.config.prune.regularBackup.year;

        newStorage.database = new DatabaseConfig();
        newStorage.database.backup = new ScheduleConfig();
        newStorage.database.backup.enabled = this.config.database.backup.enabled;
        newStorage.database.backup.interval = this.config.database.backup.interval;
        newStorage.database.backup.crontab = this.config.database.backup.crontab;

        newConfig.setConfig(newStorage);
        return newConfig;
    }

    public boolean isCheckUpdate() {
        return config.checkUpdate;
    }

    public ArrayList<String> getIgnoredFiles() {
        ArrayList<String> ignoredFiles = new ArrayList<>(config.ignoredFiles);
        ignoredFiles.add("session.lock");
        return ignoredFiles;
    }


    public ArrayList<String> getIgnoredFolders() {
        return config.ignoredFolders;
    }


    public String getLang() {
        return config.lang;
    }

    public void setLang(String lang) {
        config.lang = lang;
        save();
    }

    public AutoRestartMode getAutoRestartMode() {
        return config.autoRestartMode;
    }

    public void setAutoRestartMode(AutoRestartMode autoRestartMode) {
        config.autoRestartMode = autoRestartMode;
        save();
    }

    /**
     * A command line used verbatim (whitespace split, double quotes honoured) to restart the server,
     * instead of the auto-detected one.
     *
     * <p>The escape hatch for launchers whose command line cannot be recovered from the JVM (a wrapper
     * script, a container entrypoint, a panel that rewrites argv). Blank means "detect it".
     */
    public String getRelaunchCommand() {
        return config.relaunchCommand == null ? "" : config.relaunchCommand.trim();
    }

    public void setRelaunchCommand(String relaunchCommand) {
        config.relaunchCommand = relaunchCommand == null ? "" : relaunchCommand.trim();
        save();
    }

    /** Seconds the pre-restore countdown lasts; clamped to the documented range, 0 meaning "no countdown". */
    public int getRestoreCountdownSeconds() {
        return Math.max(MIN_RESTORE_COUNTDOWN_SECONDS,
            Math.min(MAX_RESTORE_COUNTDOWN_SECONDS, config.restoreCountdownSeconds));
    }

    public void setRestoreCountdownSeconds(int seconds) {
        config.restoreCountdownSeconds = Math.max(MIN_RESTORE_COUNTDOWN_SECONDS,
            Math.min(MAX_RESTORE_COUNTDOWN_SECONDS, seconds));
        save();
    }

    public boolean isClientAutoReJoinWorld() {
        return config.clientAutoReJoinWorld;
    }

    public void setClientAutoReJoinWorld(boolean clientAutoReJoinWorld) {
        config.clientAutoReJoinWorld = clientAutoReJoinWorld;
        save();
    }

    public boolean isCacheDatabase() {
        return config.cacheDatabase;
    }

    public int getFullBackupInterval() {
        return config.fullBackupInterval;
    }

    public int getSaveFullBackupCount() {
        return config.saveFullBackupCount;
    }

    public ScheduleBackupConfig getScheduleBackupConfig() {
        return config.scheduleBackup;
    }

    public PruneScheduleConfig getPruneScheduleConfig() {
        return config.prune;
    }

    public DatabaseConfig getDatabaseConfig() {
        return config.database;
    }

    @Override
    public @NotNull String getStoragePath() {
        return config.storagePath;
    }

    public void setStoragePath(String storagePath) {
        config.storagePath = storagePath;
    }

    /**
     * What happens to the server once a pending restore has been applied at shutdown.
     */
    public enum AutoRestartMode {
        /** Leave the server stopped; the process exits and nothing restarts it. */
        DISABLE,
        /**
         * Start the server again after a restore. Kept as the name every existing config file uses.
         *
         * <p>Its implementation is identical to {@link #SELF_RESTART}: the mod launches a replacement
         * process and exits, because re-running {@code MinecraftServer#runServer} on the instance that
         * already ran it corrupts the world-gen registries (issue #61).
         */
        DEFAULT,
        /**
         * Restart the server without help from anything outside the JVM: launch a new process with the
         * same command line this one was started with, then exit. Needs no service manager or panel.
         */
        SELF_RESTART,
        /** Exit with a non-zero code and let an external panel/daemon start the server again. */
        MCSM;

        /**
         * Resolves a config-file value leniently.
         *
         * <p>Hand-written configs carry aliases (`NONE` is what the issue tracker suggests), and Gson
         * turns anything it does not recognise — including an explicit {@code null} — into {@code null}
         * rather than failing. Neither may reach a {@code switch}.
         */
        public static AutoRestartMode fromConfigValue(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return DEFAULT;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return switch (normalized) {
                case "DISABLE", "DISABLED", "NONE", "OFF", "FALSE" -> DISABLE;
                case "DEFAULT", "AUTO", "AUTOMATIC" -> DEFAULT;
                case "SELF_RESTART", "SELFRESTART", "SELF", "RELAUNCH", "PROCESS", "PROCESS_RESTART" -> SELF_RESTART;
                case "MCSM" -> MCSM;
                default -> {
                    logger.warn("Unknown autoRestartMode '{}' in the config, using DEFAULT.", raw);
                    yield DEFAULT;
                }
            };
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    @Setter
    @Getter
    public static class ConfigStorage {
        private boolean checkUpdate = true;
        private ArrayList<String> ignoredFiles = new ArrayList<>();
        private ArrayList<String> ignoredFolders = new ArrayList<>();
        private String lang = "zh_cn";

        private AutoRestartMode autoRestartMode = AutoRestartMode.DEFAULT;
        private boolean clientAutoReJoinWorld = true;

        private String storagePath = "./QuickBackupMulti";
        private boolean cacheDatabase = false;
        private int fullBackupInterval = 5;
        private int saveFullBackupCount = 5;

        /** Overrides the auto-detected restart command; blank = detect it. */
        private String relaunchCommand = "";

        /** Seconds between {@code /qb confirm} and the rollback; 0 = roll back immediately. */
        private int restoreCountdownSeconds = 10;

        private ScheduleBackupConfig scheduleBackup = new ScheduleBackupConfig();

        private PruneScheduleConfig prune = new PruneScheduleConfig();

        private DatabaseConfig database = new DatabaseConfig();

        /**
         * Restores the defaults for sections that a partially written config left as an explicit
         * {@code null}. Gson does not apply field initialisers to those.
         */
        private void fillMissingSections() {
            if (ignoredFiles == null) ignoredFiles = new ArrayList<>();
            if (ignoredFolders == null) ignoredFolders = new ArrayList<>();
            if (lang == null) lang = "zh_cn";
            if (autoRestartMode == null) autoRestartMode = AutoRestartMode.DEFAULT;
            if (storagePath == null) storagePath = "./QuickBackupMulti";
            if (relaunchCommand == null) relaunchCommand = "";
            if (scheduleBackup == null) scheduleBackup = new ScheduleBackupConfig();
            if (scheduleBackup.interval == null) scheduleBackup.interval = "3h";
            if (scheduleBackup.requireOnlinePlayersBlacklist == null) {
                scheduleBackup.requireOnlinePlayersBlacklist = List.of();
            }
            if (prune == null) prune = new PruneScheduleConfig();
            if (prune.interval == null) prune.interval = "3h";
            if (prune.regularBackup == null) prune.regularBackup = new PbsConfig();
            if (prune.regularBackup.maxLifeTime == null) prune.regularBackup.maxLifeTime = "0s";
            if (prune.temporaryBackup == null) prune.temporaryBackup = new ScheduleConfig();
            if (database == null) database = new DatabaseConfig();
            if (database.backup == null) database.backup = new ScheduleConfig();
            if (database.backup.interval == null) database.backup.interval = "3h";
        }

        @Override
        public String toString() {
            return "ConfigStorage{" +
                    "checkUpdate=" + checkUpdate +
                    ", ignoredFiles=" + ignoredFiles +
                    ", ignoredFolders=" + ignoredFolders +
                    ", lang='" + lang + '\'' +
                    ", autoRestartMode=" + autoRestartMode +
                    ", clientAutoReJoinWorld=" + clientAutoReJoinWorld +
                    ", storagePath='" + storagePath + '\'' +
                    ", cacheDatabase=" + cacheDatabase +
                    ", fullBackupInterval=" + fullBackupInterval +
                    ", saveFullBackupCount=" + saveFullBackupCount +
                    ", relaunchCommand='" + relaunchCommand + '\'' +
                    ", restoreCountdownSeconds=" + restoreCountdownSeconds +
                    ", scheduleBackup=" + scheduleBackup +
                    ", prune=" + prune +
                    ", database=" + database +
                    '}';
        }
    }
}
