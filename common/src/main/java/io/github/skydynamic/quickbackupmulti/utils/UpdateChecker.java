package io.github.skydynamic.quickbackupmulti.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.skydynamic.quickbackupmulti.ModVersion;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class UpdateChecker extends Thread {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String RELEASE_API_URL = "https://api.github.com/repos/QuickBackupMultiMod-Dev/QuickBackupM-Reforged/releases";

    public UpdateChecker() {
        super("QuickBackupM-Reforged-Update-Checker");
    }

    @Override
    public void run() {
        ModVersion currentVersion = QuickbackupmultiReforged.getModContainer().getModVersion();

        try {
            HttpResponse<String> response = CLIENT.send(
                HttpRequest.newBuilder().uri(new URI(RELEASE_API_URL)).build(),
                HttpResponse.BodyHandlers.ofString()
            );

            JsonElement jsonElement = JsonParser.parseString(response.body());
            JsonObject release = null;
            if (jsonElement.isJsonArray()) {
                JsonArray releases = jsonElement.getAsJsonArray();
                release = releases.get(0).isJsonObject() ? releases.get(0).getAsJsonObject() : null;
            }

            if (release != null) {
                ModVersion releaseVersion = new ModVersion(release.get("tag_name").getAsString());
                if (releaseVersion.isNewerThan(currentVersion)) {
                    QuickbackupmultiReforged.logger.info("New version available: {}", releaseVersion);
                    QuickbackupmultiReforged.logger.info("Download link: {}", release.get("html_url").getAsString());
                }
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            QuickbackupmultiReforged.logger.error("Failed to check update", e);
        }
    }
}
