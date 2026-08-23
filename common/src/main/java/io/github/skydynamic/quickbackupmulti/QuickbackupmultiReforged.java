package io.github.skydynamic.quickbackupmulti;

import io.github.skydynamic.increment.storage.lib.database.Database;
import io.github.skydynamic.increment.storage.lib.utils.StorageManager;
import io.github.skydynamic.quickbackupmulti.command.ModCommand;
import io.github.skydynamic.quickbackupmulti.config.ModConfig;
import io.github.skydynamic.quickbackupmulti.database.DatabaseManager;
import io.github.skydynamic.quickbackupmulti.harness.HarnessCommandChannel;
import io.github.skydynamic.quickbackupmulti.schedule.quartz.DisableQuartzInfoLogger;
import io.github.skydynamic.quickbackupmulti.translate.Translate;
import io.github.skydynamic.quickbackupmulti.utils.UpdateChecker;
import io.github.skydynamic.quickbackupmulti.utils.permission.PermissionManager;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.UUID;

public final class QuickbackupmultiReforged {
    public static final String MOD_ID = "quickbackupmulti_reforged";
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
        logger.info("QuickBackupMulti {} initializing (env: {})",
            container.getModVersion(), container.getEnvType());

        // Initialize Config
        modConfig = new ModConfig(modContainer.getConfigPath().resolve(MOD_NAME + ".json"));
        modConfig.load();
        modConfig.save();
        modContainer.setPermissionManager(new PermissionManager());

        if (modConfig.isCheckUpdate()) {
            new UpdateChecker().start();
        }

        // Initialize Translate
        Translate.handleResourceReload(modConfig.getLang());

        // Initialize StoragePath
        File storagePath = new File(modConfig.getStoragePath());
        if (!storagePath.exists()) {
            storagePath.mkdirs();
        }

        // Disable Quartz Info Logger
        DisableQuartzInfoLogger.disable();

        // The functional test harness's command channel. Both halves of this condition matter: the
        // property keeps it out of a real game, and the client check keeps HarnessCommandChannel — which
        // references Minecraft — from ever being loaded on a dedicated server.
        if (container.getEnvType() == ModEnvType.CLIENT
            && Boolean.getBoolean(HarnessCommandChannel.ENABLE_PROPERTY)) {
            HarnessCommandChannel.startIfEnabled();
        }

        logger.info("QuickBackupMulti initialization complete");
    }

    public static void registerCommand() {
        if (modContainer.getDispatcher() == null) return;
        ModCommand.register(modContainer.getDispatcher());
    }

    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(timestamp);
    }

    public static void setNewDataBase(String collectionName) {
        QuickbackupmultiReforged.getModContainer().setOriginalStoragePath(QuickbackupmultiReforged.getModConfig().getStoragePath());

        String appendFolder = (QuickbackupmultiReforged.getModContainer().getEnvType() == ModEnvType.CLIENT) ? "/" + collectionName : "";
        DatabaseManager databaseManager = new DatabaseManager(
            "QuickBackupMulti",
            QuickbackupmultiReforged.getModConfig().getStoragePath(),
            UUID.nameUUIDFromBytes(collectionName.getBytes())
        );

        ModConfig modTempConfig = QuickbackupmultiReforged.getModConfig().copy();

        modTempConfig.setStoragePath(QuickbackupmultiReforged.getModConfig().getStoragePath() + appendFolder);
        QuickbackupmultiReforged.setDatabase(new Database(databaseManager));
        QuickbackupmultiReforged.setManager(new StorageManager(QuickbackupmultiReforged.getDatabase(), modTempConfig));
    }
}
