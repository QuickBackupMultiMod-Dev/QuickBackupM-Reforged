package io.github.skydynamic.quickbakcupmulti.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

public class ModConfig implements IConfig {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-Config");
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .serializeNulls()
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
            setConfig(gson.fromJson(bfr, ConfigStorage.class));
        } catch (IOException e) {
            logger.error("Load {} error: read file failed.", path, e);
            return false;
        }
        return true;
    }

    public ModConfig copy() {
        ModConfig newConfig = new ModConfig(this.path);
        ConfigStorage newStorage = new ConfigStorage();

        newStorage.checkUpdate = this.config.checkUpdate;
        newStorage.lang = this.config.lang;
        newStorage.maxScheduleBackup = this.config.maxScheduleBackup;
        newStorage.autoRestartMode = this.config.autoRestartMode;
        newStorage.clientAutoReJoinWorld = this.config.clientAutoReJoinWorld;
        newStorage.storagePath = this.config.storagePath;
        newStorage.cacheDatabase = this.config.cacheDatabase;

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

    public int getMaxScheduleBackup() {
        return config.maxScheduleBackup;
    }

    public AutoRestartMode getAutoRestartMode() {
        return config.autoRestartMode;
    }

    public void setAutoRestartMode(AutoRestartMode autoRestartMode) {
        config.autoRestartMode = autoRestartMode;
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

    public enum AutoRestartMode {
        DISABLE,
        DEFAULT,
        MCSM
    }

    @SuppressWarnings("FieldMayBeFinal")
    @Setter
    @Getter
    public static class ConfigStorage {
        private boolean checkUpdate = true;
        private ArrayList<String> ignoredFiles = new ArrayList<>();
        private ArrayList<String> ignoredFolders = new ArrayList<>();
        private String lang = "zh_cn";
        private int maxScheduleBackup = 10;

        private AutoRestartMode autoRestartMode = AutoRestartMode.DEFAULT;
        private boolean clientAutoReJoinWorld = true;

        private String storagePath = "./QuickBackupMulti";
        private boolean cacheDatabase = false;

        private ScheduleBackupConfig scheduleBackup = new ScheduleBackupConfig();

        private PruneScheduleConfig prune = new PruneScheduleConfig();

        private DatabaseConfig database = new DatabaseConfig();

        @Override
        public String toString() {
            return "ConfigStorage{" +
                    "checkUpdate=" + checkUpdate +
                    ", ignoredFiles=" + ignoredFiles +
                    ", ignoredFolders=" + ignoredFolders +
                    ", lang='" + lang + '\'' +
                    ", maxScheduleBackup=" + maxScheduleBackup +
                    ", autoRestartMode=" + autoRestartMode +
                    ", clientAutoReJoinWorld=" + clientAutoReJoinWorld +
                    ", storagePath='" + storagePath + '\'' +
                    ", cacheDatabase=" + cacheDatabase +
                    ", scheduleBackup=" + scheduleBackup +
                    ", prune=" + prune +
                    ", database=" + database +
                    '}';
        }
    }
}
