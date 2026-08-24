package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads (and caches) everything a headless run needs for one Minecraft version: the loader's
 * server launcher and the mod's runtime dependencies.
 *
 * <p>Every artifact is cached under a shared directory keyed by its coordinates, so re-running the
 * matrix locally or restoring a CI cache costs no downloads. Downloads land in a sibling
 * {@code .part} file and are moved into place only once complete, so an interrupted run cannot leave
 * a truncated jar that later looks like a valid cache hit.
 */
public final class Provisioner {
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String NEOFORGE_MAVEN =
        "https://maven.neoforged.net/releases/net/neoforged/neoforge";
    private static final String MOJANG_MANIFEST =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String MODRINTH = "https://api.modrinth.com/v2/project";
    /** Where the client's content-addressed asset objects live. */
    private static final String RESOURCES = "https://resources.download.minecraft.net/";

    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    private final Path cache;

    public Provisioner(Path cache) throws IOException {
        this.cache = cache;
        Files.createDirectories(cache);
    }

    /**
     * The Mojang version manifest. Cached like any other artifact so a matrix run makes one request
     * instead of one per version, and so an offline run can reuse the previous copy.
     */
    public String versionManifest() throws IOException, InterruptedException {
        Path p = cache.resolve("version_manifest_v2.json");
        if (!Files.exists(p) || Files.size(p) == 0) {
            download(MOJANG_MANIFEST, p);
        }
        return Files.readString(p);
    }

    /**
     * Prepares a Fabric server directory: the launcher jar plus Fabric API and
     * fabric-language-kotlin, both hard runtime requirements of the mod that its jar does not bundle.
     */
    public Path fabricServer(Path runDir, String mcVersion) throws IOException, InterruptedException {
        String loader = fabricLoaderVersion(mcVersion);
        String installer = firstMatch(
            get(FABRIC_META + "/versions/installer"),
            "\"version\"\\s*:\\s*\"([^\"]+)\"",
            "no Fabric installer version");

        Path launcher = cached("fabric-server-" + mcVersion + "-" + loader + "-" + installer + ".jar",
            FABRIC_META + "/versions/loader/" + mcVersion + "/" + loader + "/" + installer + "/server/jar");
        Path target = runDir.resolve("server.jar");
        Files.copy(launcher, target, StandardCopyOption.REPLACE_EXISTING);

        Path mods = Files.createDirectories(runDir.resolve("mods"));
        // The mod's nested fabric-command-api-v2 / fabric-lifecycle-events-v1 both depend on
        // fabric-api-base, which the jar does not bundle, so the full Fabric API has to be present or
        // the 'main' entrypoint dies with NoClassDefFoundError. fabric-language-kotlin is a declared
        // hard dependency that is likewise not bundled.
        copyModrinth("fabric-api", mcVersion, "fabric", mods.resolve("fabric-api.jar"));
        copyModrinth("fabric-language-kotlin", mcVersion, "fabric", mods.resolve("fabric-language-kotlin.jar"));
        return target;
    }

    /**
     * Prepares a NeoForge server directory by running the official installer, which writes the
     * {@code libraries/} tree and the run scripts the server needs.
     *
     * @param javaExecutable the {@code java} to run the installer with — has to be new enough for
     *                       {@code mcVersion}, same as the server itself, or the installer can fail (or
     *                       silently produce a run script pinned to the wrong JDK) on a branch that
     *                       needs a newer major than the runner's own JVM
     * @return the NeoForge version that was installed
     */
    public String neoForgeServer(Path runDir, String mcVersion, String javaExecutable)
        throws IOException, InterruptedException {
        String version = latestNeoForge(mcVersion);
        Path installer = cached("neoforge-" + version + "-installer.jar",
            NEOFORGE_MAVEN + "/" + version + "/neoforge-" + version + "-installer.jar");

        Files.createDirectories(runDir);
        Process p = new ProcessBuilder(javaExecutable, "-jar", installer.toAbsolutePath().toString(),
            "--install-server", runDir.toAbsolutePath().toString())
            .directory(runDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(runDir.resolve("neoforge-installer.log").toFile())
            .start();
        if (!p.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) {
            p.destroyForcibly();
            throw new IOException("NeoForge installer timed out for " + mcVersion);
        }
        if (p.exitValue() != 0) {
            throw new IOException("NeoForge installer failed for " + mcVersion + " (exit "
                + p.exitValue() + "); see " + runDir.resolve("neoforge-installer.log"));
        }
        Files.createDirectories(runDir.resolve("mods"));
        return version;
    }

    /**
     * The newest NeoForge build for a Minecraft version.
     *
     * <p>Beta builds are accepted: several Minecraft <em>releases</em> only ever got beta NeoForge
     * builds (1.20.3, 1.20.5, 1.21.6, 1.21.11 among them) and the release branches already pin those,
     * so excluding them would leave real supported versions untestable. The "release only" rule in the
     * matrix applies to the Minecraft version, not to the loader's build channel.
     */
    public String latestNeoForge(String mcVersion) throws IOException, InterruptedException {
        String prefix = McVersions.neoForgePrefix(mcVersion) + ".";
        Matcher m = Pattern.compile("<version>([^<]+)</version>")
            .matcher(get(NEOFORGE_MAVEN + "/maven-metadata.xml"));
        String best = null;
        while (m.find()) {
            String v = m.group(1);
            if (v.startsWith(prefix)) best = v;
        }
        if (best == null) {
            throw new IOException("No NeoForge build for Minecraft " + mcVersion
                + " (looked for " + prefix + "*)");
        }
        return best;
    }

    /** Copies the newest Modrinth file for a project/version/loader into {@code target}. */
    public void copyModrinth(String project, String mcVersion, String loader, Path target)
        throws IOException, InterruptedException {
        String url = MODRINTH + "/" + project + "/version?game_versions=%5B%22" + mcVersion
            + "%22%5D&loaders=%5B%22" + loader + "%22%5D";
        String body = get(url);
        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+\\.jar)\"")
            .matcher(body);
        if (!m.find()) {
            throw new IOException("No " + project + " build for " + mcVersion + " / " + loader);
        }
        String fileUrl = m.group(1);
        String name = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
        Files.copy(cached(project + "-" + mcVersion + "-" + name, fileUrl), target,
            StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------------
    // Client provisioning
    // ------------------------------------------------------------------------

    /** The per-version metadata document that lists the client jar, libraries and asset index. */
    public String mojangVersionJson(String mcVersion) throws IOException, InterruptedException {
        Path p = cache.resolve("version-" + mcVersion + ".json");
        if (!Files.exists(p) || Files.size(p) == 0) {
            // The manifest maps a version id to its own metadata URL; the URLs are content-addressed, so
            // a cached copy never goes stale.
            Matcher m = Pattern
                .compile("\\{[^{}]*?\"id\"\\s*:\\s*\"" + Pattern.quote(mcVersion)
                    + "\"[^{}]*?\"url\"\\s*:\\s*\"([^\"]+)\"[^{}]*?}")
                .matcher(versionManifest());
            if (!m.find()) {
                throw new IOException("Minecraft " + mcVersion + " is not in the version manifest");
            }
            download(m.group(1), p);
        }
        return Files.readString(p);
    }

    /** The vanilla client jar for a version. */
    public Path clientJar(String mcVersion, String versionJson) throws IOException, InterruptedException {
        Matcher m = Pattern
            .compile("\"client\"\\s*:\\s*\\{[^{}]*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL)
            .matcher(versionJson);
        if (!m.find()) {
            throw new IOException("No client download for " + mcVersion);
        }
        return cached("client-" + mcVersion + ".jar", m.group(1));
    }

    /**
     * Downloads every vanilla library a client needs, extracting native archives into
     * {@code nativesDir}.
     *
     * <p>Modern version manifests express platform-specific natives as ordinary libraries carrying a
     * {@code rules} block, so an entry is taken only when its rules allow this OS. Legacy manifests use a
     * {@code classifiers}/{@code natives} pair instead, which is handled too since the matrix reaches
     * back to 1.20.
     */
    public List<Path> vanillaLibraries(String versionJson, Path librariesDir, Path nativesDir)
        throws IOException, InterruptedException {
        List<Path> out = new ArrayList<>();
        for (String entry : splitLibraries(versionJson)) {
            if (!rulesAllow(entry)) {
                continue;
            }
            // The "artifact" download is the jar that goes on the classpath.
            Matcher artifact = Pattern.compile(
                    "\"artifact\"\\s*:\\s*\\{.*?\"path\"\\s*:\\s*\"([^\"]+)\".*?\"url\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL)
                .matcher(entry);
            if (artifact.find()) {
                Path jar = librariesDir.resolve(artifact.group(1));
                if (!Files.exists(jar) || Files.size(jar) == 0) {
                    download(artifact.group(2), jar);
                }
                out.add(jar);
                if (artifact.group(1).contains("natives")) {
                    extractNatives(jar, nativesDir);
                }
                continue;
            }
            // Legacy layout: natives live under classifiers keyed by ${os}.
            Matcher classifier = Pattern.compile(
                    "\"natives-" + ClientRun.osName()
                        + "\"\\s*:\\s*\\{.*?\"path\"\\s*:\\s*\"([^\"]+)\".*?\"url\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL)
                .matcher(entry);
            if (classifier.find()) {
                Path jar = librariesDir.resolve(classifier.group(1));
                if (!Files.exists(jar) || Files.size(jar) == 0) {
                    download(classifier.group(2), jar);
                }
                extractNatives(jar, nativesDir);
            }
        }
        return out;
    }

    /** Splits the {@code libraries} array into one string per entry, brace-balanced. */
    private static List<String> splitLibraries(String versionJson) {
        int start = versionJson.indexOf("\"libraries\"");
        if (start < 0) return List.of();
        int arrayStart = versionJson.indexOf('[', start);
        if (arrayStart < 0) return List.of();
        List<String> entries = new ArrayList<>();
        int depth = 0;
        int entryStart = -1;
        for (int i = arrayStart; i < versionJson.length(); i++) {
            char c = versionJson.charAt(i);
            if (c == '{') {
                if (depth == 0) entryStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && entryStart >= 0) {
                    entries.add(versionJson.substring(entryStart, i + 1));
                    entryStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        return entries;
    }

    /**
     * Evaluates a library's {@code rules} for this OS.
     *
     * <p>Mojang's semantics: with no rules the library always applies; otherwise the last matching rule
     * wins, and an entry with rules but no match is excluded. Getting this wrong on Linux pulls in the
     * Windows natives and the client dies in GLFW, so it is worth doing properly.
     */
    private static boolean rulesAllow(String entry) {
        int rulesAt = entry.indexOf("\"rules\"");
        if (rulesAt < 0) {
            return true;
        }
        String os = ClientRun.osName();
        boolean allowed = false;
        Matcher m = Pattern.compile("\\{\\s*\"action\"\\s*:\\s*\"(allow|disallow)\"([^{}]*(\\{[^{}]*})?[^{}]*)}")
            .matcher(entry.substring(rulesAt));
        while (m.find()) {
            String action = m.group(1);
            String body = m.group(2);
            Matcher osMatcher = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            boolean applies = !osMatcher.find() || osMatcher.group(1).equals(os);
            if (applies) {
                allowed = "allow".equals(action);
            }
        }
        return allowed;
    }

    /** Unpacks the platform libraries (GLFW, OpenAL, …) a client loads through JNI. */
    private void extractNatives(Path jar, Path nativesDir) throws IOException {
        Files.createDirectories(nativesDir);
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory() || name.startsWith("META-INF/")) continue;
                // Flatten: the JVM's java.library.path is a directory of libraries, not a tree.
                Path target = nativesDir.resolve(name.substring(name.lastIndexOf('/') + 1));
                if (Files.exists(target)) continue;
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Downloads the client's assets into the standard {@code objects/<first two chars>/<hash>} layout.
     *
     * @return the asset index id the client must be launched with
     */
    public String downloadAssets(String versionJson, Path assetsDir)
        throws IOException, InterruptedException {
        Matcher m = Pattern.compile(
                "\"assetIndex\"\\s*:\\s*\\{.*?\"id\"\\s*:\\s*\"([^\"]+)\".*?\"url\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.DOTALL)
            .matcher(versionJson);
        if (!m.find()) {
            throw new IOException("No assetIndex in the version metadata");
        }
        String indexId = m.group(1);
        Path indexFile = assetsDir.resolve("indexes").resolve(indexId + ".json");
        if (!Files.exists(indexFile) || Files.size(indexFile) == 0) {
            download(m.group(2), indexFile);
        }

        Path objects = assetsDir.resolve("objects");
        String index = Files.readString(indexFile);
        Matcher hashes = Pattern.compile("\"hash\"\\s*:\\s*\"([0-9a-f]{40})\"").matcher(index);
        int downloaded = 0;
        while (hashes.find()) {
            String hash = hashes.group(1);
            Path target = objects.resolve(hash.substring(0, 2)).resolve(hash);
            if (Files.exists(target) && Files.size(target) > 0) {
                continue;
            }
            download(RESOURCES + hash.substring(0, 2) + "/" + hash, target);
            downloaded++;
        }
        if (downloaded > 0) {
            System.out.println("[harness] downloaded " + downloaded + " asset objects for " + indexId);
        }
        return indexId;
    }

    /** Fabric's launcher profile for a version, which names the loader libraries and the main class. */
    public String fabricProfileJson(String mcVersion) throws IOException, InterruptedException {
        String loader = fabricLoaderVersion(mcVersion);
        Path p = cache.resolve("fabric-profile-" + mcVersion + "-" + loader + ".json");
        if (!Files.exists(p) || Files.size(p) == 0) {
            download(FABRIC_META + "/versions/loader/" + mcVersion + "/" + loader + "/profile/json", p);
        }
        return Files.readString(p);
    }

    /**
     * The Fabric loader build to use for a version, preferring a stable release.
     *
     * <p>The Fabric Meta API lists builds newest-first, but "newest" and "stable" are not the same thing
     * — a fresh unstable build can sort ahead of the last stable one — so this walks the list looking for
     * {@code "stable":true} instead of trusting position 0. Falls back to the newest build (with a
     * warning) only if nothing in the list claims to be stable at all.
     */
    private String fabricLoaderVersion(String mcVersion) throws IOException, InterruptedException {
        String body = get(FABRIC_META + "/versions/loader/" + mcVersion);
        Matcher entries = Pattern.compile("\"loader\"\\s*:\\s*\\{([^{}]*)}").matcher(body);
        String newest = null;
        while (entries.find()) {
            String entry = entries.group(1);
            Matcher version = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(entry);
            if (!version.find()) {
                continue;
            }
            if (newest == null) {
                newest = version.group(1);
            }
            if (Pattern.compile("\"stable\"\\s*:\\s*true").matcher(entry).find()) {
                return version.group(1);
            }
        }
        if (newest == null) {
            throw new IOException("no Fabric loader for " + mcVersion);
        }
        System.out.println("[harness] no Fabric loader build is marked stable for " + mcVersion
            + "; using the newest build " + newest + " instead");
        return newest;
    }

    /**
     * Resolves the Maven coordinates in a Fabric profile to jars.
     *
     * <p>Unlike Mojang's manifest, Fabric's profile gives {@code group:artifact:version} plus a repository
     * base, so the path has to be assembled.
     */
    public List<Path> fabricLibraries(String profileJson, Path librariesDir)
        throws IOException, InterruptedException {
        List<Path> out = new ArrayList<>();
        Matcher m = Pattern
            .compile("\\{[^{}]*?\"name\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"url\"\\s*:\\s*\"([^\"]+)\"[^{}]*?}")
            .matcher(profileJson);
        while (m.find()) {
            String coordinates = m.group(1);
            String repository = m.group(2);
            String[] parts = coordinates.split(":");
            if (parts.length < 3) continue;
            String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2]
                + "/" + parts[1] + "-" + parts[2] + ".jar";
            Path jar = librariesDir.resolve(path);
            if (!Files.exists(jar) || Files.size(jar) == 0) {
                String base = repository.endsWith("/") ? repository : repository + "/";
                download(base + path, jar);
            }
            out.add(jar);
        }
        if (out.isEmpty()) {
            throw new IOException("Fabric profile listed no libraries");
        }
        return out;
    }

    /** Returns a cached artifact, downloading it on first use. */
    public Path cached(String name, String url) throws IOException, InterruptedException {
        Path p = cache.resolve(name.replaceAll("[^A-Za-z0-9._+-]", "_"));
        if (Files.exists(p) && Files.size(p) > 0) {
            return p;
        }
        download(url, p);
        return p;
    }

    /** How many times a network call is attempted before giving up. */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Runs {@code call}, retrying with exponential backoff (1s, 2s) on {@link IOException} — a dropped
     * connection or a transient 5xx from Mojang/Fabric/Modrinth/NeoForge should not turn into a blocking
     * {@code ERROR} for the whole (version, loader) combination on the first hiccup. An interrupt is not
     * retried: it means the run is being torn down, not that the network blipped.
     *
     * <p>Exhausting all attempts raises {@link HarnessException} rather than the underlying
     * {@code IOException}, so {@code ScenarioRunner} files it as infrastructure ({@code ERROR}) instead
     * of a mod incompatibility ({@code FAILED}).
     */
    private static <T> T withRetry(String what, RetryableCall<T> call) throws InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.call();
            } catch (IOException e) {
                last = e;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                long backoffMs = 1000L << (attempt - 1);
                System.out.println("[harness] " + what + " failed (attempt " + attempt + "/"
                    + MAX_ATTEMPTS + "): " + e.getMessage() + "; retrying in " + backoffMs + "ms");
                Thread.sleep(backoffMs);
            }
        }
        throw new HarnessException(what + " failed after " + MAX_ATTEMPTS + " attempts", last);
    }

    private interface RetryableCall<T> {
        T call() throws IOException, InterruptedException;
    }

    private void download(String url, Path target) throws InterruptedException {
        withRetry("download " + url, () -> {
            Path part = target.resolveSibling(target.getFileName() + ".part");
            Files.createDirectories(target.getParent());
            HttpResponse<InputStream> r = http.send(
                HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "QuickBackupMulti-Reforged-harness")
                    .timeout(Duration.ofMinutes(10))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) {
                throw new IOException("HTTP " + r.statusCode() + " for " + url);
            }
            try (InputStream in = r.body()) {
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(part) == 0) {
                Files.deleteIfExists(part);
                throw new IOException("Empty download for " + url);
            }
            // Only publish under the real name once the bytes are all there, so an aborted run cannot
            // poison the cache with a partial jar.
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
    }

    private String get(String url) throws InterruptedException {
        return withRetry("GET " + url, () -> {
            HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "QuickBackupMulti-Reforged-harness")
                    .timeout(Duration.ofMinutes(2))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IOException("HTTP " + r.statusCode() + " for " + url);
            }
            return r.body();
        });
    }

    private static String firstMatch(String body, String regex, String failure) throws IOException {
        Matcher m = Pattern.compile(regex).matcher(body);
        if (!m.find()) throw new IOException(failure);
        return m.group(1);
    }

    /**
     * The mod jar built for a loader, picked out of the root {@code build/libs} directory.
     *
     * <p>A branch produces exactly one jar per loader: {@code archivesBaseName} in the root
     * {@code build.gradle} bakes in the branch's own {@code minecraft_version} property, not whichever
     * Minecraft release a matrix entry happens to be testing against. So the matrix's per-release
     * versions all resolve to this same jar — that is the point of the matrix, to run one build against
     * every release the branch claims to support. Filtering by the matrix's {@code mcVersion} instead
     * (as this used to) fails for every release but the branch's own.
     *
     * <p>{@code build/libs} also accumulates jars from every branch and {@code mod_version} ever built
     * locally (nothing here cleans it), so a {@code mod_version} filter is required too — mtime alone is
     * not a reliable enough tiebreaker on a developer machine with multiple checked-out branches.
     */
    public static Path builtModJar(Path repoRoot, String loader) throws IOException {
        String mcVersion = McVersions.property(repoRoot.resolve("gradle.properties"), "minecraft_version");
        String modVersion = McVersions.property(repoRoot.resolve("gradle.properties"), "mod_version");
        Path libs = repoRoot.resolve("build/libs");
        if (!Files.isDirectory(libs)) {
            throw new IOException("No build/libs; run :" + loader + ":remapJar first");
        }
        try (var files = Files.list(libs)) {
            List<Path> candidates = files
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .filter(p -> p.getFileName().toString().contains("-mc" + mcVersion + "-" + loader + "-"))
                .filter(p -> p.getFileName().toString().contains(modVersion))
                .filter(p -> !p.getFileName().toString().contains("sources"))
                .filter(p -> !p.getFileName().toString().contains("shadow"))
                .toList();
            if (candidates.isEmpty()) {
                throw new IOException("No built jar matching -mc" + mcVersion + "-" + loader + "- and "
                    + "mod_version " + modVersion + " in " + libs);
            }
            // Newest wins, so a stale jar left over from a previous build of the same branch/mod_version
            // cannot be picked up.
            Path chosen = candidates.stream()
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElseThrow();
            System.out.println("[harness] using mod jar: " + chosen);
            return chosen;
        }
    }
}
