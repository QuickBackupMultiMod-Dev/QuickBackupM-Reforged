package io.github.skydynamic.quickbackupmulti;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class DatabaseCache {
    @Getter
    private static List<StorageInfo> storageInfoCaches = new ArrayList<>();

    public static void updateStorageInfoCaches() {
        QuickbackupmultiReforged.logger.info("Update storage info caches");
        storageInfoCaches = QuickbackupmultiReforged.getDatabase().getAllStorageInfo();
    }
}
