package io.github.skydynamic.quickbakcupmulti;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class DatabaseCache {
    @Getter
    private static List<StorageInfo> storageInfoCaches = new ArrayList<>();

    public static void updateStorageInfoCaches() {
        QuickbakcupmultiReforged.logger.info("Update storage info caches");
        storageInfoCaches = QuickbakcupmultiReforged.getDatabase().getAllStorageInfo();
    }
}
