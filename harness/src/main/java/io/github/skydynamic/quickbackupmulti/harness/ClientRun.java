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
 * {@code /qb} commands, including a full {@code restore} + {@code confirm}, are driven through
 * {@link ClientCommandChannel} — the mod's own stdin channel where available, synthetic keystrokes
 * otherwise. Either way the command reaches the game the same way a player's does; the client has no
 * console of its own, so this is not a workaround so much as the only in-band route there is.
 */
public final class ClientRun implements AutoCloseable {
    /**
     * The last stage of the client's initial resource reload that logs anything version-stable.
     *
     * <p>Reaching the title screen itself is <em>not</em> observable: vanilla logs nothing when the
     * loading overlay hands over to a screen, so there is no "menu ready" line to wait for. This marker
     * is the closest observable point — the sound engine comes up inside the first resource reload, well
     * after the mod's client entrypoint has run — and the menu scenario is scoped to what that actually
     * proves. It is {@link #bootIntoWorld} that gates on the client being interactive, because
     * {@code --quickPlaySingleplayer} only fires once the initial screen chain has been worked through.
     */
    private static final String BOOT_PROGRESSED = "Sound engine started";
    /**
     * Logged by the server's player list once the player is actually in the world.
     *
     * <p>The integrated server does not log a "world is ready" line of its own —
     * {@code Done (…)! For help, type "help"} comes from {@code DedicatedServer} and never appears on a
     * client. This is the next observable thing, and it is the right gate anyway: it means the player
     * entity exists and the chat box {@link ClientInput} types into will accept a command.
     */
    private static final String WORLD_READY = "logged in with entity id";
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
        writeGameOptions(runDir);

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
        // Asks the mod under test to listen for commands on stdin. Without it the mod behaves exactly as
        // a shipped build does, and the scenario falls back to synthetic keystrokes. -Pqbm.forceRobot
        // skips this so the fallback path can be exercised on a machine that has a real window manager.
        if (!config.forceRobot()) {
            command.add("-Dqbm.harness.channel=true");
        }
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

    /**
     * Writes the {@code options.txt} a fresh client would otherwise not have.
     *
     * <p>Without this file the client treats the run as a first launch and puts the accessibility
     * onboarding screen in front of everything else. That screen is modal: it blocks the title screen and,
     * because {@code --quickPlaySingleplayer} is only applied once the initial screen chain has been worked
     * through, it blocks world loading too. Nothing is logged when it appears, so the client just sits
     * there looking healthy — the log ends after the texture atlases, the process stays alive and keeps
     * rendering, and every wait times out with no indication why. Dismissing it would need a mouse click at
     * a screen position that moves between versions, so turning it off is the only stable option.
     *
     * <p>The rest are here to keep a scenario from depending on the host: the multiplayer warning is
     * another modal, the tutorial toasts overlap the chat box {@link ClientInput} types into, and a client
     * that pauses when it loses focus stops ticking the moment the window manager hands focus elsewhere —
     * which would stall a scenario on a machine someone is also using.
     */
    private static void writeGameOptions(Path runDir) throws IOException {
        Files.writeString(runDir.resolve("options.txt"), String.join(System.lineSeparator(), List.of(
            "onboardAccessibility:false",
            "skipMultiplayerWarning:true",
            "tutorialStep:none",
            "pauseOnLostFocus:false",
            "narrator:0",
            "")));
    }

    /**
     * Boots the client and waits until it is far enough in to have loaded the mod and its resources.
     *
     * <p>This deliberately stops short of claiming the main menu was reached — see
     * {@link #BOOT_PROGRESSED} for why that is unobservable from the log.
     */
    public ClientRun bootToMenu() throws IOException, InterruptedException {
        return boot(List.of(), BOOT_PROGRESSED, BOOT_TIMEOUT);
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

    /**
     * Blocks until the player is in the world, which is later than the integrated server merely starting
     * and is what a chat command needs. See {@link #WORLD_READY}.
     */
    public void awaitWorldReady(Duration timeout) throws InterruptedException {
        client().awaitLine(WORLD_READY, timeout);
    }

    /**
     * A command channel into this client, ready to use.
     *
     * <p>Prefers the mod's own stdin channel, which needs neither a focused window nor a keyboard and is
     * the only thing that can work on a CI runner whose display is a bare Xvfb with no window manager.
     * Falls back to synthetic keystrokes when the running mod does not offer one — an older build, or a
     * branch this has not been ported to yet — so a scenario still runs rather than failing on the
     * mechanism.
     *
     * <p>The fallback is verified before it is handed out, because input that lands in another window
     * produces no log line at all and is indistinguishable from a command that arrived and did nothing.
     * Call this only once the player is in the world — see {@link #awaitWorldReady}.
     */
    public ClientCommandChannel commands() throws InterruptedException {
        ClientCommandChannel channel;
        if (InGameCommandChannel.availableOn(client())) {
            channel = new InGameCommandChannel(client());
        } else {
            ClientInput input = new ClientInput(client(), client().pid());
            input.verifyReachesClient();
            channel = input;
        }
        // Which channel a run used decides how to read everything after it, so say so plainly rather than
        // letting a silent downgrade look like a normal run.
        System.out.println("[harness] client " + mcVersion + " commands via " + channel.describe());
        return channel;
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
     * The client keeps one shared database but a separate blob directory per world.
     *
     * <p>{@code QuickbackupmultiReforged.setNewDataBase} points {@code DatabaseManager} at the configured
     * {@code storagePath} while handing {@code StorageManager} a copy of the config with
     * {@code /<levelId>} appended, so the two are <em>not</em> in the same place on a client the way they
     * are on a dedicated server. Collections are keyed by level id rather than by {@code "server"}.
     */
    public BackupStore store(String levelId) {
        Path storage = runDir.resolve("QuickBackupMulti");
        return new BackupStore(storage, storage.resolve(levelId), levelId);
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
