package io.github.skydynamic.quickbakcupmulti.restart;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

public class ServerRestart {
    private static final RuntimeMXBean RUNTIME_MX_BEAN = ManagementFactory.getRuntimeMXBean();

    private ServerRestart() {}

    public static List<String> generateUnixRestartCommand() {
        String jre = System.getProperty("java.home") + "/bin/java";
        return generateRestartCommand(jre);
    }

    public static List<String> generateWindowsRestartCommand() {
        String jre = System.getProperty("java.home") + "\\bin\\java.exe";
        return generateRestartCommand(jre);
    }

    private static List<String> generateRestartCommand(String jre) {
        String cp = RUNTIME_MX_BEAN.getClassPath();
        String mainClass = RUNTIME_MX_BEAN.getSystemProperties().get("sun.java.command");
        List<String> arguments = new ArrayList<>(RUNTIME_MX_BEAN.getInputArguments());

        if (mainClass == null || mainClass.isEmpty()) {
            throw new IllegalArgumentException("Main class is not specified.");
        }
        if (cp.isEmpty()) {
            throw new IllegalArgumentException("Classpath is empty.");
        }

        List<String> command = new ArrayList<>();
        command.add(jre);
        command.add("-cp");
        command.add(cp);
        command.addAll(arguments);
        command.addAll(List.of(mainClass.split(" ")));

        return command;
    }

    public static void restartServer() {
        List<String> command;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            command = generateWindowsRestartCommand();
        } else {
            command = generateUnixRestartCommand();
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            builder.start();

            System.exit(0);
        } catch (Exception e) {
            QuickbakcupmultiReforged.logger.error("Failed to restart server", e);
        }
    }
}
