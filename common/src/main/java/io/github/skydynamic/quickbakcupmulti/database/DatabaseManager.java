package io.github.skydynamic.quickbakcupmulti.database;

import io.github.skydynamic.increment.storage.lib.Interface.IDatabaseManager;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class DatabaseManager implements IDatabaseManager {
    private String fileName = "QuickBakcupMulti";
    private String databasePath = "./QuickBakcupMulti";
    private UUID collectionUuid;

    public DatabaseManager(
        String fileName,
        String dataBasePath,
        UUID collectionUuid
    ) {
        this.fileName = fileName;
        this.databasePath = dataBasePath;
        this.collectionUuid = collectionUuid;
    }

    public DatabaseManager(UUID uuid) {
        this.collectionUuid = uuid;
    }
}
