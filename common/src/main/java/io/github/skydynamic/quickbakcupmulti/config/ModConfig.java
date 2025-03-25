package io.github.skydynamic.quickbakcupmulti.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.skydynamic.increment.storage.lib.Interface.IConfig;
import lombok.Getter;
import lombok.Setter;
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
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

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
    }

    public int getMaxScheduleBackup() {
        return config.maxScheduleBackup;
    }

    public AutoRestartMode getAutoRestartMode() {
        return config.autoRestartMode;
    }

    public void setAutoRestartMode(AutoRestartMode autoRestartMode) {
        config.autoRestartMode = autoRestartMode;
    }

    @Override
    public String getStoragePath() {
        return config.storagePath;
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

        private String storagePath = "./QuickBackupMulti";

        @Override
        public String toString() {
            return "ConfigStorage [checkUpdate=" + checkUpdate + ", ignoredFiles=" + ignoredFiles
                + ", ignoredFolders=" + ignoredFolders + ", lang=" + lang
                + ", autoRestartMode=" + autoRestartMode + ", storagePath=" + storagePath + "]";
        }
    }
}
