package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Prints this branch's test matrix as JSON, so a CI workflow and a local {@code ./gradlew} run derive
 * it from exactly the same code.
 *
 * <p>Usage: {@code MatrixMain [outputFile]}. The JSON goes to stdout always, and to the file when one
 * is given.
 */
public final class MatrixMain {
    private MatrixMain() {
    }

    public static void main(String[] args) throws Exception {
        Path repoRoot = Path.of(System.getProperty("qbm.repoRoot", "."));
        Path cache = Path.of(System.getProperty("qbm.cacheDir",
            repoRoot.resolve(".gradle/qbm-harness-cache").toString()));

        Matrix matrix = resolve(repoRoot, cache);
        String json = matrix.toJson();
        System.out.println(json);
        if (args.length > 0) {
            Path out = Path.of(args[0]);
            Files.createDirectories(out.getParent());
            Files.writeString(out, json);
        }
        for (String w : matrix.warnings) {
            System.err.println("WARNING: " + w);
        }
    }

    /** Resolves the matrix from {@code gradle.properties} plus the (cached) Mojang manifest. */
    public static Matrix resolve(Path repoRoot, Path cache) throws IOException, InterruptedException {
        Path props = repoRoot.resolve("gradle.properties");
        String spec = McVersions.property(props, "minecraft_supported_versions");
        String branch = McVersions.property(props, "minecraft_version");
        String platforms = McVersions.property(props, "enabled_platforms");

        List<String> releases = McVersions.releases(new Provisioner(cache).versionManifest());
        List<String> versions = McVersions.inRange(spec, releases);

        Matrix matrix = new Matrix(branch, spec, versions);
        for (String p : platforms.split(",")) {
            String loader = McVersions.loaderId(p);
            if (!loader.isEmpty()) {
                matrix.loaders.add(loader);
            }
        }
        if (McVersions.isBareSpec(spec)) {
            matrix.warnings.add("branch " + branch + " " + McVersions.BARE_SPEC_WARNING);
        }
        if (versions.isEmpty()) {
            matrix.warnings.add("branch " + branch + " resolved no release versions from '" + spec
                + "'; the range may only cover snapshots.");
        }
        return matrix;
    }

    /** The resolved matrix: which Minecraft releases, on which loaders, for one branch. */
    public static final class Matrix {
        public final String branch;
        public final String spec;
        public final List<String> versions;
        public final List<String> loaders = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        Matrix(String branch, String spec, List<String> versions) {
            this.branch = branch;
            this.spec = spec;
            this.versions = versions;
        }

        /** Every (version, loader) pair, which is what a CI job fans out over. */
        public List<String[]> pairs() {
            List<String[]> out = new ArrayList<>();
            for (String v : versions) {
                for (String l : loaders) {
                    out.add(new String[]{v, l});
                }
            }
            return out;
        }

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"branch\":").append(quote(branch))
                .append(",\"spec\":").append(quote(spec))
                .append(",\"versions\":").append(array(versions))
                .append(",\"loaders\":").append(array(loaders))
                .append(",\"warnings\":").append(array(warnings))
                .append(",\"include\":[");
            List<String[]> pairs = pairs();
            for (int i = 0; i < pairs.size(); i++) {
                String[] pair = pairs.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"mc\":").append(quote(pair[0]))
                    .append(",\"loader\":").append(quote(pair[1]))
                    .append(",\"java\":").append(McVersions.requiredJava(pair[0]))
                    .append('}');
            }
            return sb.append("]}").toString();
        }

        private static String array(List<String> items) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(quote(items.get(i)));
            }
            return sb.append(']').toString();
        }

        private static String quote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.append('"').toString();
        }
    }
}
