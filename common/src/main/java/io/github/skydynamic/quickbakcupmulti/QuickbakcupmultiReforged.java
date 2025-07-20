package io.github.skydynamic.quickbakcupmulti;

import io.github.skydynamic.increment.storage.lib.database.Database;
import io.github.skydynamic.increment.storage.lib.utils.StorageManager;
import io.github.skydynamic.quickbakcupmulti.command.ModCommand;
import io.github.skydynamic.quickbakcupmulti.config.ModConfig;
import io.github.skydynamic.quickbakcupmulti.schedule.quartz.DisableQuartzInfoLogger;
import io.github.skydynamic.quickbakcupmulti.translate.Translate;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;

public final class QuickbakcupmultiReforged {
    public static final String MOD_ID = "quickbakcupmulti_reforged";
    public static final String MOD_NAME = "QuickBackupMulti";
    public static final Logger logger = LoggerFactory.getLogger(MOD_NAME);
    @Getter @Setter
    private static Database database;
    @Getter @Setter
    private static StorageManager manager;
    @Getter @Setter
    private static ServerManager serverManager;
    @Getter @Setter
    private static ModContainer modContainer;
    @Getter @Setter
    private static ModConfig modConfig;

    public static void init(ModContainer container) {
        modContainer = container;

        // Initialize Config
        modConfig = new ModConfig(modContainer.getConfigPath().resolve(MOD_NAME + ".json"));
        modConfig.load();
        modConfig.save();
        modContainer.setPermissionManager(new PermissionManager());

        // Initialize Translate
        Translate.handleResourceReload(modConfig.getLang());

        // Initialize StoragePath
        File storagePath = new File(modConfig.getStoragePath());
        if (!storagePath.exists()) {
            storagePath.mkdirs();
        }

        // Disable Quartz Info Logger
        DisableQuartzInfoLogger.disable();
    }

    public static void registerCommand() {
        if (modContainer.getDispatcher() == null) return;
        ModCommand.register(modContainer.getDispatcher());
    }

    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(timestamp);
    }
}
