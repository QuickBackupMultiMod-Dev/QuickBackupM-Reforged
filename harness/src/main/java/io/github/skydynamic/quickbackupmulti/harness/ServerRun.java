package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One Minecraft version on one loader, in its own directory, ready to be booted and driven.
 *
 * <p>A run is throwaway: it is created fresh per scenario so that a leaked world, backup store or
 * database from a previous scenario can never make the next one pass (or fail) for the wrong reason.
 * Only the download cache is shared.
 */
public final class ServerRun implements AutoCloseable {
    /** Marker the dedicated server prints once the world is loaded and commands can be accepted. */
    private static final Pattern DONE = Pattern.compile("Done \\([0-9.]+s\\)! For help");
    /** Generous: a cold first boot generates the world and Mixin has to apply everything. */
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(8);

    private final HarnessConfig config;
    private final String mcVersion;
    private final String loader;
    private final Path runDir;
    private final Path logDir;
    private final List<String> launchCommand;
    private GameProcess process;
    private int bootCount;

    private ServerRun(HarnessConfig config, String mcVersion, String loader, Path runDir, Path logDir,
                      List<String> launchCommand) {
        this.config = config;
        this.mcVersion = mcVersion;
        this.loader = loader;
        this.runDir = runDir;
        this.logDir = logDir;
        this.launchCommand = launchCommand;
    }

    /**
     * Provisions a clean run directory: loader server, mod jar, EULA, server properties and mod config.
     *
     * @param scenario a short name that keeps each scenario's directory and logs separate
     */
    public static ServerRun provision(HarnessConfig config, String mcVersion, String loader,
                                      String scenario, ModConfigFile modConfig)
        throws IOException, InterruptedException {
        String id = mcVersion + "-" + loader + "-" + scenario;
        Path runDir = config.workDir().resolve(id);
        Path logDir = config.reportDir().resolve("logs").resolve(id);

        deleteRecursively(runDir);
        Files.createDirectories(runDir);

        Provisioner provisioner = config.provisioner();
        String javaExecutable = config.javaExecutable(mcVersion);
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Xmx2G");
        // The game's own log encoding, so a localised message cannot corrupt the captured stream.
        command.add("-Dfile.encoding=UTF-8");

        if ("fabric".equals(loader)) {
            provisioner.fabricServer(runDir, mcVersion);
            command.add("-jar");
            command.add("server.jar");
        } else if ("neoforge".equals(loader)) {
            String neoForgeVersion = provisioner.neoForgeServer(runDir, mcVersion, javaExecutable);
            command.addAll(neoForgeLaunchArgs(runDir, neoForgeVersion));
        } else {
            throw new HarnessException("Unknown loader '" + loader + "'");
        }
        command.add("nogui");

        Files.copy(Provisioner.builtModJar(config.repoRoot(), loader),
            runDir.resolve("mods").resolve("quickbackupmulti.jar"));

        writeEula(runDir);
        writeServerProperties(runDir);
        modConfig.writeTo(runDir);

        return new ServerRun(config, mcVersion, loader, runDir, logDir, command);
    }

    /**
     * Turns the installer's generated argfile into a java command line.
     *
     * <p>NeoForge ships {@code run.sh}/{@code run.bat} wrappers, but going through a shell script would
     * put a process between the harness and the JVM, so stdin-driven commands and a clean kill both
     * become unreliable. The argfile the scripts reference is the stable part, so it is used directly.
     */
    private static List<String> neoForgeLaunchArgs(Path runDir, String neoForgeVersion) throws IOException {
        Path argsFile = runDir.resolve("libraries/net/neoforged/neoforge/" + neoForgeVersion
            + "/unix_args.txt");
        if (!Files.exists(argsFile)) {
            Path win = runDir.resolve("libraries/net/neoforged/neoforge/" + neoForgeVersion
                + "/win_args.txt");
            if (Files.exists(win)) {
                argsFile = win;
            } else {
                throw new HarnessException("NeoForge " + neoForgeVersion
                    + " installed without an args file; looked in " + argsFile.getParent());
            }
        }
        // @argfile keeps the (very long) classpath off the command line, which Windows would truncate.
        return List.of("@" + runDir.relativize(argsFile).toString().replace('\\', '/'));
    }

    private static void writeEula(Path runDir) throws IOException {
        // Accepting the EULA is a precondition of running a dedicated server at all; a test run of the
        // vanilla server is exactly the use the EULA contemplates.
        Files.writeString(runDir.resolve("eula.txt"), "eula=true" + System.lineSeparator());
    }

    private static void writeServerProperties(Path runDir) throws IOException {
        // A flat world with no structures and a 3-chunk radius keeps generation (and therefore every
        // boot) fast, and keeps the backup small enough that assertions run in seconds.
        Files.writeString(runDir.resolve("server.properties"), String.join(System.lineSeparator(),
            "level-name=world",
            "level-type=flat",
            "generate-structures=false",
            "spawn-protection=0",
            "max-world-size=1000",
            // Offline mode: no session server round-trip, so the boot works on an air-gapped runner.
            "online-mode=false",
            "server-port=0",
            "sync-chunk-writes=true",
            "view-distance=3",
            "simulation-distance=3",
            "enable-command-block=false",
            "spawn-monsters=false",
            "spawn-npcs=false",
            "spawn-animals=false",
            // Nothing may tick the world while a scenario asserts on file contents.
            "difficulty=peaceful",
            "gamemode=creative",
            "max-players=1",
            "motd=qbm-harness",
            ""));
    }

    /** Boots the server and waits until it accepts commands. */
    public ServerRun boot() throws IOException, InterruptedException {
        if (process != null && process.isAlive()) {
            throw new HarnessException("Server is already running");
        }
        bootCount++;
        Path logFile = logDir.resolve("boot-" + bootCount + ".log");
        process = GameProcess.start(mcVersion + "/" + loader, runDir, logFile, launchCommand);
        process.awaitPattern(DONE, BOOT_TIMEOUT);
        return this;
    }

    /**
     * Runs {@code /qb make <name>} and waits for the backup thread to finish.
     *
     * <p>The wait targets {@code Make Backup thread close}, which the mod logs unconditionally in
     * English, rather than the translated success message: the latter comes from a language file and
     * would silently stop matching if a translation changed.
     */
    public void makeBackup(String name) throws InterruptedException {
        makeBackup(name, Duration.ofMinutes(5));
    }

    public void makeBackup(String name, Duration timeout) throws InterruptedException {
        server().send("qb make \"" + name + "\"");
        server().awaitLine("Make Backup thread close", timeout);
    }

    private static final Duration RESTORE_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Restores a backup by name or 1-based index, and waits until the restore has actually started
     * running (not merely been requested).
     *
     * <p>{@code qb restore} only arms a 10-second confirmation countdown; the restore itself happens in
     * {@code OnServerStoppedHandler} once that countdown fires and the server halts, which is also where
     * {@code "Make a temp backup success."} is logged — <em>before</em> the reconstruct, not after. So
     * this only proves the restore is under way, not that it finished; use
     * {@link #restoreAndAwaitExit} or {@link #restoreAndAwaitRestart} for that. stdin is a single ordered
     * stream drained by one console thread, so sending {@code confirm} immediately after {@code restore}
     * is safe — there is no race to wait out between the two commands themselves.
     *
     * <p>{@code "Make a temp backup success."} can already be present in the log from an earlier restore
     * in the same scenario, so a plain {@code awaitLine} would return immediately without waiting for
     * anything; a baseline count taken before sending the commands makes this wait for a <em>new</em>
     * occurrence instead.
     */
    public void restore(String target) throws InterruptedException {
        int before = server().occurrences("Make a temp backup success.");
        server().send("qb restore \"" + target + "\"");
        server().send("qb confirm");
        server().awaitOccurrences("Make a temp backup success.", before + 1, RESTORE_TIMEOUT);
    }

    /**
     * Restores a backup and waits for the server process to exit — the completion signal when
     * {@code autoRestartMode} is {@code DISABLE}.
     */
    public int restoreAndAwaitExit(String target) throws InterruptedException {
        restore(target);
        return server().awaitExit(RESTORE_TIMEOUT);
    }

    /**
     * Restores a backup and waits for the in-process restart to finish booting — the completion signal
     * on Fabric when {@code autoRestartMode} is {@code DEFAULT}, which relaunches inside the same JVM
     * rather than exiting.
     */
    public void restoreAndAwaitRestart(String target) throws InterruptedException {
        int bootsBefore = server().linesMatching(DONE).size();
        restore(target);
        server().awaitOccurrences(DONE, bootsBefore + 1, BOOT_TIMEOUT);
    }

    /** The running server, or a clear failure if a scenario forgot to boot it. */
    public GameProcess server() {
        if (process == null) {
            throw new HarnessException("Server has not been booted");
        }
        return process;
    }

    /** Stops the server and waits for it to exit. */
    public void stop() throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.stopServer();
        }
    }

    public Path runDir() {
        return runDir;
    }

    /** The live world directory that a backup captures and a restore overwrites. */
    public Path worldDir() {
        return runDir.resolve("world");
    }

    /** The mod's backup store: blob directories, full copies and the H2 database. */
    public Path storageDir() {
        return runDir.resolve("QuickBackupMulti");
    }

    public String mcVersion() {
        return mcVersion;
    }

    public String loader() {
        return loader;
    }

    /** Opens the mod's H2 metadata for assertions about what was actually recorded. */
    public BackupStore store() {
        return new BackupStore(storageDir(), "server");
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
            deleteRecursively(runDir);
        } catch (IOException e) {
            // A leftover directory is untidy but harmless: the next provision() deletes it first, and on
            // Windows a lingering handle is the usual cause.
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Every file under a directory, relative to it, sorted — the shape a restore has to reproduce. */
    public static List<String> relativeFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                .map(p -> root.relativize(p).toString().replace('\\', '/'))
                // session.lock is recreated by the server on every boot and is explicitly excluded from
                // backups, so comparing it would fail for a reason that is not a bug.
                .filter(p -> !p.endsWith("session.lock"))
                .sorted(Comparator.naturalOrder())
                .toList();
        }
    }
}
