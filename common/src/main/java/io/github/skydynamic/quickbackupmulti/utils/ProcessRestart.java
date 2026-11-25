package io.github.skydynamic.quickbackupmulti.utils;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Restarts the dedicated server by launching a replacement process and exiting this one.
 *
 * <p>This is the only restart mechanism the mod has. Re-entering
 * {@code MinecraftServer#runServer()} on the instance that already ran it keeps the world-gen
 * registries that instance was built with, and since 1.21.x those registries refuse to be modified a
 * second time ({@code IllegalStateException: Dimensions in this dynamic registries instance have
 * already been modified}, issue #61). A fresh process is also the only way to get the mod loaders
 * re-initialised at all: Fabric's {@code Knot}/{@code FabricLoader} and NeoForge's {@code FMLLoader}
 * run exactly once per JVM and cannot be replayed from inside it.
 *
 * <p>Deliberately free of Minecraft types so the same code works on every Minecraft branch, and so
 * {@link #buildCommand} can be exercised directly by tests.
 */
public final class ProcessRestart {
    /**
     * Written to the log right before this process exits, with the replacement's pid appended.
     *
     * <p>Operators grep for it when a restart looks wrong; the functional tests use it to find the
     * replacement they have to shut down.
     */
    public static final String RELAUNCH_LOG_MARKER = "QBM: relaunching server";

    private ProcessRestart() {
    }

    /** Everything {@link #buildCommand} needs, so tests can feed it synthetic launches. */
    public record LaunchInfo(
        String command,
        List<String> arguments,
        List<String> jvmArguments,
        String javaHome,
        String classPath,
        String sunJavaCommand,
        String osName
    ) {
        /** The launch of the JVM this code is running in. */
        public static LaunchInfo current() {
            ProcessHandle.Info info = ProcessHandle.current().info();
            return new LaunchInfo(
                info.command().orElse(null),
                info.arguments().map(List::of).orElse(List.of()),
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getProperty("java.home"),
                System.getProperty("java.class.path"),
                System.getProperty("sun.java.command"),
                System.getProperty("os.name", "")
            );
        }
    }

    /**
     * The command line that starts a copy of this server, or an empty list when it cannot be worked
     * out.
     *
     * <p>Preference order: the exact argv of this JVM (correct for {@code java -jar server.jar nogui},
     * for NeoForge's {@code @user_jvm_args.txt} launch and for panel launchers), then a reconstruction
     * from {@code java.home} plus {@code sun.java.command}.
     */
    public static List<String> buildCommand(LaunchInfo info) {
        if (info == null) {
            return List.of();
        }

        // A command without arguments is not a server launch: some JVMs (and some containers) expose the
        // executable but refuse the argv, and `java` on its own would start nothing. Fall through to the
        // reconstruction below instead of "successfully" launching a JVM that prints its usage and dies.
        if (info.command() != null && !info.command().isBlank() && !info.arguments().isEmpty()) {
            List<String> cmd = new ArrayList<>();
            cmd.add(info.command());
            cmd.addAll(info.arguments() == null ? List.of() : info.arguments());
            return cmd;
        }

        List<String> parts = splitCommandLine(info.sunJavaCommand());
        if (parts.isEmpty() || info.javaHome() == null || info.javaHome().isBlank()) {
            return List.of();
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable(info.javaHome(), info.osName()));
        if (info.jvmArguments() != null) {
            cmd.addAll(info.jvmArguments());
        }

        // `sun.java.command` holds "<main or jar> <args...>"; a jar has to be re-launched with -jar,
        // which is what the old NeoForge-only implementation got wrong.
        String main = parts.get(0);
        if (main.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            cmd.add("-jar");
            cmd.add(main);
        } else {
            if (info.classPath() == null || info.classPath().isBlank()) {
                return List.of();
            }
            cmd.add("-cp");
            cmd.add(info.classPath());
            cmd.add(main);
        }
        cmd.addAll(parts.subList(1, parts.size()));
        return cmd;
    }

    /**
     * Starts a replacement server and exits this process.
     *
     * @return false when no command line could be produced or the child could not be started — the
     *     caller's process then simply stays down instead of dying in a loop.
     */
    public static boolean restart() {
        List<String> command = resolveRestartCommand();
        if (command.isEmpty()) {
            QuickbackupmultiReforged.logger.error(
                "Cannot determine how this server was started, so it will not restart itself. " +
                    "Start it again manually, or set \"relaunchCommand\" in the config to the command " +
                    "used to start it.");
            return false;
        }

        try {
            // inheritIO keeps the replacement on the same console/pipe, so a panel or a service
            // manager that watches our stdout keeps watching the server that is actually running.
            Process replacement = new ProcessBuilder(command)
                .directory(workingDirectory().toFile())
                .inheritIO()
                .start();
            QuickbackupmultiReforged.logger.info("{} (pid={}) {}", RELAUNCH_LOG_MARKER, replacement.pid(), command);
        } catch (IOException e) {
            QuickbackupmultiReforged.logger.error("Failed to start the replacement server process: {}", command, e);
            return false;
        }

        // halt() rather than exit(): this runs inside MinecraftServer#stopServer, and exit() would wait
        // for the vanilla "Server Shutdown Thread" hook, which calls stopServer() again on this very
        // thread — a deadlock. The world is saved, the database closed and the schedules cleared by the
        // time we get here, so there is nothing left for a shutdown hook to do.
        System.out.flush();
        System.err.flush();
        Runtime.getRuntime().halt(0);
        return true;
    }

    /** The configured override when set, otherwise the detected command line. */
    public static List<String> resolveRestartCommand() {
        String override = QuickbackupmultiReforged.getModConfig().getRelaunchCommand();
        if (!override.isEmpty()) {
            List<String> configured = splitCommandLine(override);
            if (!configured.isEmpty()) {
                return configured;
            }
            QuickbackupmultiReforged.logger.warn("relaunchCommand is set but empty after parsing: '{}'", override);
        }
        return buildCommand(LaunchInfo.current());
    }

    private static Path workingDirectory() {
        return Path.of(System.getProperty("user.dir", "."));
    }

    static String javaExecutable(String javaHome, String osName) {
        boolean windows = osName != null && osName.toLowerCase(Locale.ROOT).startsWith("windows");
        String separator = windows ? "\\" : "/";
        String executable = windows ? "java.exe" : "java";
        // Built textually rather than with Path so the separator follows the *target* platform, which is
        // what this method is asked about, not whatever host the tests happen to run on.
        String home = javaHome.endsWith("/") || javaHome.endsWith("\\")
            ? javaHome.substring(0, javaHome.length() - 1)
            : javaHome;
        return home + separator + "bin" + separator + executable;
    }

    /**
     * Splits a command line the way a shell would for the simple cases: whitespace separates tokens and
     * double quotes group them. No escaping, variable expansion or single quotes — this only has to
     * round-trip a plain server start command.
     */
    public static List<String> splitCommandLine(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                started = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (started) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    started = false;
                }
            } else {
                current.append(c);
                started = true;
            }
        }
        if (started) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
