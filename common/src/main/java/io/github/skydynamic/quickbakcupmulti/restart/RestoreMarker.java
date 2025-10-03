package io.github.skydynamic.quickbakcupmulti.restart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class RestoreMarker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MARKER_FILE_NAME = ".qbm_restore_pending";

    private RestoreMarker() {}

    public static void write(String targetBackup) {
        if (targetBackup == null || targetBackup.isEmpty()) {
            QuickbakcupmultiReforged.logger.warn("Skip writing restore marker, target backup is empty.");
            return;
        }

        Path markerPath = resolveMarkerPath();
        if (markerPath == null) {
            QuickbakcupmultiReforged.logger.error("Cannot resolve restore marker path while writing.");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("targetBackup", targetBackup);

        try {
            Files.createDirectories(markerPath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(
                markerPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException e) {
            QuickbakcupmultiReforged.logger.error("Failed to write restore marker", e);
        }
    }

    public static Optional<String> read() {
        Path markerPath = resolveMarkerPath();
        if (markerPath == null || !Files.exists(markerPath)) {
            return Optional.empty();
        }

        try (BufferedReader reader = Files.newBufferedReader(markerPath, StandardCharsets.UTF_8)) {
            JsonObject object = GSON.fromJson(reader, JsonObject.class);
            if (object != null && object.has("targetBackup")) {
                String target = object.get("targetBackup").getAsString();
                if (target != null && !target.isEmpty()) {
                    return Optional.of(target);
                }
            }
        } catch (IOException e) {
            QuickbakcupmultiReforged.logger.error("Failed to read restore marker", e);
        }
        return Optional.empty();
    }

    public static void delete() {
        Path markerPath = resolveMarkerPath();
        if (markerPath == null) {
            QuickbakcupmultiReforged.logger.error("Cannot resolve restore marker path while deleting.");
            return;
        }

        try {
            Files.deleteIfExists(markerPath);
        } catch (IOException e) {
            QuickbakcupmultiReforged.logger.error("Failed to delete restore marker", e);
        }
    }

    public static boolean exists() {
        Path markerPath = resolveMarkerPath();
        return markerPath != null && Files.exists(markerPath);
    }

    private static Path resolveMarkerPath() {
        if (QuickbakcupmultiReforged.getModContainer() == null) {
            QuickbakcupmultiReforged.logger.error("Mod container not initialized when resolving restore marker path.");
            return null;
        }

        Path currentSavePath = QuickbakcupmultiReforged.getModContainer().getCurrentSavePath();
        if (currentSavePath == null) {
            QuickbakcupmultiReforged.logger.error("Current save path is null when resolving restore marker path.");
            return null;
        }

        Path parent = currentSavePath.getParent();
        if (parent == null) {
            QuickbakcupmultiReforged.logger.error("Current save path parent is null when resolving restore marker path.");
            return null;
        }

        return parent.resolve(MARKER_FILE_NAME);
    }
}
