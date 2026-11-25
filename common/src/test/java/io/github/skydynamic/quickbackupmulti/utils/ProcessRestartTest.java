package io.github.skydynamic.quickbackupmulti.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The restart command line decides whether a rollback leaves a running server behind, so it is built by
 * a pure function and pinned here.
 */
class ProcessRestartTest {
    private static ProcessRestart.LaunchInfo info(String command, List<String> arguments, String javaHome,
                                                  String classPath, String sunJavaCommand, String osName) {
        return new ProcessRestart.LaunchInfo(command, arguments, List.of(), javaHome, classPath, sunJavaCommand, osName);
    }

    @Test
    void reusesTheArgvOfThisJvm() {
        List<String> command = ProcessRestart.buildCommand(info(
            "/usr/lib/jvm/java-21/bin/java",
            List.of("-Xmx4G", "-jar", "server.jar", "nogui"),
            "/usr/lib/jvm/java-21",
            null,
            "server.jar nogui",
            "Linux"
        ));

        assertEquals(List.of("/usr/lib/jvm/java-21/bin/java", "-Xmx4G", "-jar", "server.jar", "nogui"), command);
    }

    @Test
    void fallsBackToJavaHomeForAJarLaunch() {
        List<String> command = ProcessRestart.buildCommand(info(
            null,
            List.of(),
            "/opt/java",
            null,
            "fabric-server-mc.1.21.1-loader.0.16.9-launcher.1.0.1.jar nogui",
            "Linux"
        ));

        assertEquals(
            List.of("/opt/java/bin/java", "-jar", "fabric-server-mc.1.21.1-loader.0.16.9-launcher.1.0.1.jar", "nogui"),
            command
        );
    }

    @Test
    void fallsBackToJavaHomeForAClasspathLaunch() {
        List<String> command = ProcessRestart.buildCommand(info(
            null,
            List.of(),
            "C:\\Program Files\\Java\\jdk-21",
            "libraries/a.jar;libraries/b.jar",
            "net.neoforged.devlaunch.Main @user_jvm_args.txt",
            "Windows 11"
        ));

        assertEquals(
            List.of(
                "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe",
                "-cp", "libraries/a.jar;libraries/b.jar",
                "net.neoforged.devlaunch.Main", "@user_jvm_args.txt"
            ),
            command
        );
    }

    @Test
    void ignoresACommandWhoseArgumentsWereHidden() {
        // Some JVMs expose the executable but not the argv; "java" on its own starts nothing, so the
        // reconstruction from java.home has to take over instead of "succeeding".
        List<String> command = ProcessRestart.buildCommand(info(
            "/usr/lib/jvm/java-21/bin/java",
            List.of(),
            "/usr/lib/jvm/java-21",
            null,
            "server.jar nogui",
            "Linux"
        ));

        assertEquals(List.of("/usr/lib/jvm/java-21/bin/java", "-jar", "server.jar", "nogui"), command);
    }

    @Test
    void reportsAnEmptyCommandWhenNothingIsKnown() {
        assertTrue(ProcessRestart.buildCommand(info(null, List.of(), null, null, null, "Linux")).isEmpty());
        assertTrue(ProcessRestart.buildCommand(info(null, List.of(), "/opt/java", null, "   ", "Linux")).isEmpty());
        assertTrue(ProcessRestart.buildCommand(null).isEmpty());
    }

    @Test
    void splitsAConfiguredCommandLine() {
        assertEquals(List.of("java", "-jar", "server.jar", "nogui"),
            ProcessRestart.splitCommandLine("java   -jar  server.jar nogui"));
        assertEquals(List.of("java", "-jar", "C:\\Program Files\\my server.jar", "nogui"),
            ProcessRestart.splitCommandLine("java -jar \"C:\\Program Files\\my server.jar\" nogui"));
        assertEquals(List.of(), ProcessRestart.splitCommandLine(""));
        assertEquals(List.of(), ProcessRestart.splitCommandLine("   "));
        assertEquals(List.of(), ProcessRestart.splitCommandLine(null));
    }
}
