package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Everything the harness reads from its environment, resolved once.
 *
 * <p>All of it comes from system properties set by the {@code functionalTest} Gradle task, so a run can
 * be narrowed from the command line ({@code -Pqbm.versions=1.21 -Pqbm.loaders=fabric}) without editing
 * a test.
 */
public final class HarnessConfig {
    private final Path repoRoot;
    private final Path cacheDir;
    private final Path workDir;
    private final Path reportDir;
    private final String branch;
    private final List<String> versionFilter;
    private final List<String> loaderFilter;
    private final List<String> sideFilter;
    private final List<String> scenarioFilter;
    private final boolean displayAvailable;
    private final boolean keepRunDirs;
    private Provisioner provisioner;

    private HarnessConfig(Path repoRoot, Path cacheDir, Path workDir, Path reportDir, String branch,
                          List<String> versionFilter, List<String> loaderFilter,
                          List<String> sideFilter, List<String> scenarioFilter,
                          boolean displayAvailable, boolean keepRunDirs) {
        this.repoRoot = repoRoot;
        this.cacheDir = cacheDir;
        this.workDir = workDir;
        this.reportDir = reportDir;
        this.branch = branch;
        this.versionFilter = versionFilter;
        this.loaderFilter = loaderFilter;
        this.sideFilter = sideFilter;
        this.scenarioFilter = scenarioFilter;
        this.displayAvailable = displayAvailable;
        this.keepRunDirs = keepRunDirs;
    }

    public static HarnessConfig fromSystemProperties() {
        Path repoRoot = path("qbm.repoRoot", Path.of("").toAbsolutePath());
        return new HarnessConfig(
            repoRoot,
            path("qbm.cacheDir", repoRoot.resolve(".gradle/qbm-harness-cache")),
            path("qbm.workDir", repoRoot.resolve("harness/build/run")),
            path("qbm.reportDir", repoRoot.resolve("build/compat-report")),
            System.getProperty("qbm.branch", ""),
            csv("qbm.versions"),
            csv("qbm.loaders"),
            csv("qbm.sides"),
            csv("qbm.scenarios"),
            Boolean.parseBoolean(System.getProperty("qbm.displayAvailable", "false")),
            Boolean.parseBoolean(System.getProperty("qbm.keepRunDirs", "false")));
    }

    private static Path path(String key, Path fallback) {
        String value = System.getProperty(key);
        return (value == null || value.isBlank()) ? fallback : Path.of(value);
    }

    /** An absent filter means "everything", which is different from a filter that matches nothing. */
    private static List<String> csv(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .toList();
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public Path workDir() {
        return workDir;
    }

    public Path reportDir() {
        return reportDir;
    }

    public String branch() {
        return branch;
    }

    /** Whether a client can open a window. Clients are skipped rather than failed when it cannot. */
    public boolean displayAvailable() {
        return displayAvailable;
    }

    /** Keeps run directories after a scenario, for debugging a failure by hand. */
    public boolean keepRunDirs() {
        return keepRunDirs;
    }

    public boolean includesVersion(String mcVersion) {
        return versionFilter.isEmpty() || versionFilter.contains(mcVersion.toLowerCase(Locale.ROOT));
    }

    public boolean includesLoader(String loader) {
        return loaderFilter.isEmpty() || loaderFilter.contains(loader.toLowerCase(Locale.ROOT));
    }

    public boolean includesSide(String side) {
        return sideFilter.isEmpty() || sideFilter.contains(side.toLowerCase(Locale.ROOT));
    }

    public boolean includesScenario(String scenario) {
        return scenarioFilter.isEmpty() || scenarioFilter.contains(scenario.toLowerCase(Locale.ROOT));
    }

    public Provisioner provisioner() throws IOException {
        if (provisioner == null) {
            provisioner = new Provisioner(cacheDir);
        }
        return provisioner;
    }

    /** This branch's resolved (version, loader) matrix. */
    public MatrixMain.Matrix matrix() throws IOException, InterruptedException {
        return MatrixMain.resolve(repoRoot, cacheDir);
    }

    /**
     * The {@code java} to launch the game with.
     *
     * <p>Minecraft pins a minimum JDK per version and refuses to boot below it. The runner's own JDK is
     * used when it is new enough; otherwise a matching toolchain is looked for under the usual
     * locations, and failing that the run is skipped with a message that names the version needed
     * instead of dying inside a boot log.
     */
    public String javaExecutable(String mcVersion) {
        int required = McVersions.requiredJava(mcVersion);
        if (Runtime.version().feature() >= required) {
            return currentJavaExecutable();
        }
        String override = System.getProperty("qbm.java" + required);
        if (override != null && !override.isBlank()) {
            return override;
        }
        String env = System.getenv("JAVA" + required + "_HOME");
        if (env != null && !env.isBlank()) {
            Path candidate = Path.of(env).resolve("bin").resolve(javaBinaryName());
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        throw new HarnessException("Minecraft " + mcVersion + " needs Java " + required
            + " but the harness is running on Java " + Runtime.version().feature()
            + ". Set -Dqbm.java" + required + "=<path to java> or JAVA" + required + "_HOME.");
    }

    /** True when a suitable JDK for this version is available, so a scenario can skip rather than fail. */
    public boolean hasJavaFor(String mcVersion) {
        try {
            javaExecutable(mcVersion);
            return true;
        } catch (HarnessException e) {
            return false;
        }
    }

    private static String currentJavaExecutable() {
        return Path.of(System.getProperty("java.home")).resolve("bin").resolve(javaBinaryName())
            .toString();
    }

    private static String javaBinaryName() {
        return GameProcess.isWindows() ? "java.exe" : "java";
    }
}
