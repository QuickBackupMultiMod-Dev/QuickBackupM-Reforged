package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Boots a real Minecraft <em>client</em> with the mod installed, so the client-only half of the mod is
 * covered too.
 *
 * <p>This is a small launcher rather than a wrapper around an existing one: the point of the matrix is
 * to run the actual remapped jar against every supported game version, and no launcher can be scripted
 * per-version as reliably as assembling the classpath directly from Mojang's and Fabric's own metadata.
 *
 * <p>What the client run proves, and what it does not:
 * <ul>
 *   <li>the client mixins apply — {@code quickbackupmulti.mixins.json} sets {@code "required": true}
 *       and {@code defaultRequire: 1}, so a client mixin that no longer matches its target aborts
 *       startup. That makes a plain boot a strong signal for version drift;</li>
 *   <li>the mod's client entrypoint initialises and its config and storage paths resolve;</li>
 *   <li>{@code --quickPlaySingleplayer} loads a world, which runs {@code MixinIntegratedServer} and the
 *       world-load handler that registers schedules and opens the per-world database.</li>
 * </ul>
 * {@code /qb} commands, including a full {@code restore} + {@code confirm}, are driven through the chat
 * box with {@link ClientInput}'s {@code java.awt.Robot} keyboard injection — the client has no stdin
 * command reader, so this is the same input path a real player uses, not a workaround.
 */
public final class ClientRun implements AutoCloseable {
    /** Printed once the client has a window and has finished its initial resource reload. */
    private static final String MENU_READY = "Time to start:";
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(10);

    private final HarnessConfig config;
    private final String mcVersion;
    private final Path runDir;
    private final Path logDir;
    private final List<String> baseCommand;
    private GameProcess process;
    private int bootCount;

    private ClientRun(HarnessConfig config, String mcVersion, Path runDir, Path logDir,
                      List<String> baseCommand) {
        this.config = config;
        this.mcVersion = mcVersion;
        this.runDir = runDir;
        this.logDir = logDir;
        this.baseCommand = baseCommand;
    }

    /**
     * Provisions a client run directory: vanilla client jar, libraries, natives, assets, the Fabric
     * loader, and the mod under test.
     *
     * <p>Only Fabric is supported here. NeoForge's client installer needs an interactive launcher
     * profile, so a NeoForge client is out of scope; the shared client code is in {@code common} and is
     * loaded by both, so a Fabric client run still exercises it.
     */
    public static ClientRun provision(HarnessConfig config, String mcVersion, String scenario,
                                      ModConfigFile modConfig) throws IOException, InterruptedException {
        String id = mcVersion + "-fabric-client-" + scenario;
        Path runDir = config.workDir().resolve(id);
        Path logDir = config.reportDir().resolve("logs").resolve(id);

        ServerRun.deleteRecursively(runDir);
        Files.createDirectories(runDir);

        Provisioner provisioner = config.provisioner();
        // Shared across versions: assets and libraries are content-addressed, so re-downloading them per
        // version would waste gigabytes.
        Path shared = config.workDir().resolve("client-shared");
        Path librariesDir = Files.createDirectories(shared.resolve("libraries"));
        Path assetsDir = Files.createDirectories(shared.resolve("assets"));
        Path nativesDir = Files.createDirectories(runDir.resolve("natives"));

        String versionJson = provisioner.mojangVersionJson(mcVersion);
        Path clientJar = provisioner.clientJar(mcVersion, versionJson);
        String assetIndexId = provisioner.downloadAssets(versionJson, assetsDir);

        Set<String> classpath = new LinkedHashSet<>();
        classpath.add(clientJar.toAbsolutePath().toString());
        for (Path lib : provisioner.vanillaLibraries(versionJson, librariesDir, nativesDir)) {
            classpath.add(lib.toAbsolutePath().toString());
        }
        // Fabric's profile lists the loader, intermediary mappings and its own dependencies, and names
        // the client main class that bootstraps mod loading.
        String profileJson = provisioner.fabricProfileJson(mcVersion);
        for (Path lib : provisioner.fabricLibraries(profileJson, librariesDir)) {
            classpath.add(lib.toAbsolutePath().toString());
        }
        String mainClass = extract(profileJson, "\"mainClass\"\\s*:\\s*\"([^\"]+)\"",
            "Fabric profile for " + mcVersion + " has no mainClass");

        Path mods = Files.createDirectories(runDir.resolve("mods"));
        provisioner.copyModrinth("fabric-api", mcVersion, "fabric", mods.resolve("fabric-api.jar"));
        provisioner.copyModrinth("fabric-language-kotlin", mcVersion, "fabric",
            mods.resolve("fabric-language-kotlin.jar"));
        Files.copy(Provisioner.builtModJar(config.repoRoot(), "fabric"),
            mods.resolve("quickbackupmulti.jar"));

        modConfig.writeTo(runDir);

        // An argfile keeps the classpath (hundreds of entries) off a command line Windows would truncate.
        // Inside an argfile a backslash escapes the next character, so Windows paths must be doubled.
        Path argFile = runDir.resolve("client-classpath.txt");
        Files.writeString(argFile, "-cp \""
            + String.join(java.io.File.pathSeparator, classpath).replace("\\", "\\\\") + "\"");

        List<String> command = new ArrayList<>();
        command.add(config.javaExecutable(mcVersion));
        command.add("-Xmx2G");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        // Fabric resolves mods and config relative to the game directory, not the working directory.
        command.add("-Dfabric.gameVersion=" + mcVersion);
        if (!config.displayAvailable()) {
            // Without a display GLFW aborts in native code. Say so up front instead.
            throw new HarnessException("A client run needs a display; on a headless Linux runner start "
                + "one (for example 'xvfb-run -a ./gradlew functionalTest') and set DISPLAY.");
        }
        command.add("@" + argFile.getFileName());
        command.add(mainClass);
        command.addAll(List.of(
            "--gameDir", ".",
            "--assetsDir", assetsDir.toAbsolutePath().toString(),
            "--assetIndex", assetIndexId,
            // Offline credentials: no session server round-trip, so the run works air-gapped.
            "--username", "QbmHarness",
            "--uuid", "00000000000000000000000000000000",
            "--accessToken", "0",
            "--userType", "legacy",
            "--version", mcVersion,
            "--width", "854",
            "--height", "480"));

        return new ClientRun(config, mcVersion, runDir, logDir, command);
    }

    /** Boots to the main menu and waits until the client is interactive. */
    public ClientRun bootToMenu() throws IOException, InterruptedException {
        return boot(List.of(), MENU_READY, BOOT_TIMEOUT);
    }

    /**
     * Boots straight into a singleplayer world.
     *
     * <p>{@code --quickPlaySingleplayer} is how the client's own launcher resumes a world, so it drives
     * exactly the code path a player would: the world-selection flow, the integrated server, and with it
     * the mod's world-load handler. It needs a save to already exist under {@code saves/}.
     */
    public ClientRun bootIntoWorld(String levelId) throws IOException, InterruptedException {
        return boot(List.of("--quickPlaySingleplayer", levelId),
            // The integrated server prints this once the world is up, which is the point at which the
            // mod's world-load handler has run.
            "Starting integrated minecraft server version", BOOT_TIMEOUT);
    }

    private ClientRun boot(List<String> extraArgs, String marker, Duration timeout)
        throws IOException, InterruptedException {
        if (process != null && process.isAlive()) {
            throw new HarnessException("Client is already running");
        }
        bootCount++;
        List<String> command = new ArrayList<>(baseCommand);
        command.addAll(extraArgs);
        process = GameProcess.start("client " + mcVersion, runDir,
            logDir.resolve("client-" + bootCount + ".log"), command);
        // A mixin that fails to apply throws during startup, so watch for both outcomes rather than
        // waiting out the full timeout on a crash.
        String seen = process.awaitAny(timeout, marker, "Mixin apply failed", "Mixin transformation of");
        if (!marker.equals(seen)) {
            throw new HarnessException("Client mixin application failed on " + mcVersion + ": " + seen
                + System.lineSeparator() + process.logTail(60));
        }
        return this;
    }

    /** The running client. */
    public GameProcess client() {
        if (process == null) {
            throw new HarnessException("Client has not been booted");
        }
        return process;
    }

    /**
     * Copies a world from a finished server run into this client's {@code saves/}, which is how a client
     * scenario gets a world with backups already in it without generating one twice.
     */
    public void installSave(Path worldDir, String levelId) throws IOException {
        Path target = runDir.resolve("saves").resolve(levelId);
        Files.createDirectories(target.getParent());
        copyRecursively(worldDir, target);
    }

    public Path runDir() {
        return runDir;
    }

    public Path savesDir() {
        return runDir.resolve("saves");
    }

    /**
     * The client keeps a separate backup store per world, under {@code <storagePath>/<levelId>}, because
     * one client can hold many worlds. Collections are keyed by level id rather than by {@code "server"}.
     */
    public BackupStore store(String levelId) {
        return new BackupStore(runDir.resolve("QuickBackupMulti").resolve(levelId), levelId);
    }

    /** Asks the client to quit, then makes sure it is gone. */
    public void quit() throws InterruptedException {
        if (process != null && process.isAlive()) {
            // A client has no console, so there is no graceful command to send: closing it is a kill.
            process.close();
        }
    }

    @Override
    public void close() {
        if (process != null) {
            process.close();
        }
        if (config.keepRunDirs()) {
            return;
        }
        try {
            ServerRun.deleteRecursively(runDir);
        } catch (IOException e) {
            // Same as ServerRun: the next provision() clears it, and a held handle is the usual cause.
        }
    }

    static void copyRecursively(Path from, Path to) throws IOException {
        try (var walk = Files.walk(from)) {
            for (Path source : walk.toList()) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static String extract(String json, String regex, String failure) {
        Matcher m = Pattern.compile(regex).matcher(json);
        if (!m.find()) throw new HarnessException(failure);
        return m.group(1);
    }

    /** True when this platform can host a client at all. */
    public static boolean supported(HarnessConfig config) {
        return config.displayAvailable();
    }

    static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }
}
