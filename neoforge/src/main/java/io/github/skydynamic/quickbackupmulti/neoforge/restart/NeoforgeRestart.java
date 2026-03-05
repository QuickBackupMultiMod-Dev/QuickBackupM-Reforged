package io.github.skydynamic.quickbackupmulti.neoforge.restart;


import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;

public class NeoforgeRestart {
    private static final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

    public static List<String> generateUnixRestartCommand() {
        String jre = System.getProperty("java.home") + "/bin/java";
        return generateRestartCommand(jre);
    }

    public static List<String> generateWindowsRestartCommand() {
        String jre = System.getProperty("java.home") + "\\bin\\java.exe";
        return generateRestartCommand(jre);
    }

    private static List<String> generateRestartCommand(String jre) {
        String cp = runtimeMxBean.getClassPath();
        String mainClass = runtimeMxBean.getSystemProperties().get("sun.java.command");
        List<String> arguments = new ArrayList<>(runtimeMxBean.getInputArguments());

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
        command.add("-XstartOnFirstThread");
        command.addAll(arguments);
        command.addAll(List.of(mainClass.split(" ")));

        return command;
    }

    public static void restartServer() {
        List<String> command;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            command = generateWindowsRestartCommand();
            command.remove("-XstartOnFirstThread");
        } else {
            command = generateUnixRestartCommand();
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            builder.start();

            System.exit(0);
        } catch (Exception e) {
            QuickbackupmultiReforged.logger.error("Failed to restart server", e);
        }
    }
}
