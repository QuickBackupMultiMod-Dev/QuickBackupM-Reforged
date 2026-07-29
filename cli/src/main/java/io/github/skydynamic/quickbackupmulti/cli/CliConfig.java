package io.github.skydynamic.quickbackupmulti.cli;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads only what the CLI needs ({@code storagePath}) from the mod's {@code QuickBackupMulti.json}.
 * The mod serializes its {@code ConfigStorage} at the top level, so {@code storagePath} is a top-level field.
 */
public class CliConfig {
    private static final Gson GSON = new Gson();

    public String storagePath;

    public static String readStoragePath(Path configFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            CliConfig config = GSON.fromJson(reader, CliConfig.class);
            if (config == null || config.storagePath == null || config.storagePath.isBlank()) {
                throw new IOException("Config file does not contain a 'storagePath': " + configFile);
            }
            return config.storagePath;
        }
    }
}
