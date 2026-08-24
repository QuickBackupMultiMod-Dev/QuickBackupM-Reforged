package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves which Minecraft <em>release</em> versions a branch claims to support.
 *
 * <p>The single source of truth is {@code minecraft_supported_versions} in {@code gradle.properties},
 * a Maven version range. The same string is converted by the {@code minecraftFabricRange} /
 * {@code minecraftNeoForgeRange} closures in the root {@code build.gradle} into the range that ends up
 * in {@code fabric.mod.json} and {@code neoforge.mods.toml}, so parsing it here keeps the test matrix
 * and the shipped metadata in agreement by construction.
 *
 * <p>Only versions Mojang marks {@code type == "release"} are ever returned: snapshots and
 * pre-releases are explicitly out of scope for the compatibility matrix.
 */
public final class McVersions {
    /**
     * A bare version such as {@code 1.20.5} means ">= 1.20.5" in Maven, and that is what the Fabric
     * converter emits, so those branches advertise every version ever released. Treating that
     * literally would put ~30 unrelated versions in the matrix, so the resolver narrows a bare spec
     * to the single version named and records the discrepancy instead of testing nonsense.
     */
    public static final String BARE_SPEC_WARNING =
        "declares a bare version instead of an interval, so the shipped Fabric range is an unbounded "
            + "'>=' and the jar advertises support for every later Minecraft version; "
            + "only the named version is tested";

    private McVersions() {
    }

    /** All release ids from a Mojang {@code version_manifest_v2.json}, oldest first. */
    public static List<String> releases(String manifestJson) {
        List<String> ids = new ArrayList<>();
        // The manifest is a flat "versions" array of {"id": ..., "type": ...} objects in newest-first
        // order. A dependency-free scan keeps the harness usable from a bare Gradle task.
        Matcher m = Pattern
            .compile("\\{[^{}]*?\"id\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"type\"\\s*:\\s*\"([^\"]+)\"[^{}]*?}")
            .matcher(manifestJson);
        while (m.find()) {
            if ("release".equals(m.group(2))) {
                ids.add(m.group(1));
            }
        }
        Collections.reverse(ids);
        return ids;
    }

    /**
     * The release versions inside {@code spec}, ordered oldest first.
     *
     * @param spec     a Maven range such as {@code [1.21, 1.21.4)}, or a bare version
     * @param releases release ids oldest first, as returned by {@link #releases(String)}
     */
    public static List<String> inRange(String spec, List<String> releases) {
        String trimmed = spec.trim();
        if (!(trimmed.startsWith("[") || trimmed.startsWith("("))) {
            // See BARE_SPEC_WARNING: narrow to the named version rather than an open upper bound.
            return releases.contains(trimmed) ? List.of(trimmed) : List.of();
        }

        Matcher m = Pattern
            .compile("^([\\[(])\\s*([^,\\])]*)\\s*,\\s*([^,\\])]*)\\s*([\\])])$")
            .matcher(trimmed);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                "Cannot parse minecraft_supported_versions '" + spec + "'. "
                    + "Use a single Maven interval such as [1.21, ) or [1.21, 1.21.5).");
        }
        boolean lowerInclusive = "[".equals(m.group(1));
        String lower = m.group(2).trim();
        String upper = m.group(3).trim();
        boolean upperInclusive = "]".equals(m.group(4));

        List<String> result = new ArrayList<>();
        for (String id : releases) {
            if (!lower.isEmpty()) {
                int c = compare(id, lower);
                if (c < 0 || (c == 0 && !lowerInclusive)) continue;
            }
            if (!upper.isEmpty()) {
                int c = compare(id, upper);
                if (c > 0 || (c == 0 && !upperInclusive)) continue;
            }
            result.add(id);
        }
        return result;
    }

    /** True when {@code spec} is a bare version rather than a Maven interval. */
    public static boolean isBareSpec(String spec) {
        String trimmed = spec.trim();
        return !(trimmed.startsWith("[") || trimmed.startsWith("("));
    }

    /**
     * Compares two Minecraft release ids numerically per dot-separated segment, so {@code 1.21.10}
     * sorts above {@code 1.21.9} (a plain string comparison gets that backwards) and {@code 26.1}
     * sorts above {@code 1.21.11}.
     */
    public static int compare(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        for (int i = 0; i < Math.max(as.length, bs.length); i++) {
            int c = Integer.compare(segment(as, i), segment(bs, i));
            if (c != 0) return c;
        }
        return 0;
    }

    private static int segment(String[] parts, int i) {
        if (i >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Reads a {@code key = value} pair out of a properties file without interpreting escapes. */
    public static String property(Path propertiesFile, String key) throws IOException {
        for (String line : Files.readAllLines(propertiesFile)) {
            String s = line.trim();
            if (s.startsWith("#") || !s.contains("=")) continue;
            int eq = s.indexOf('=');
            if (s.substring(0, eq).trim().equals(key)) {
                return s.substring(eq + 1).trim();
            }
        }
        throw new IOException("Missing '" + key + "' in " + propertiesFile);
    }

    /** The NeoForge artifact prefix that matches a Minecraft version: 1.21.3 -> 21.3, 26.1.2 -> 26.1. */
    public static String neoForgePrefix(String mcVersion) {
        String[] p = mcVersion.split("\\.");
        if ("1".equals(p[0])) {
            return p[1] + "." + (p.length > 2 ? p[2] : "0");
        }
        return p[0] + "." + (p.length > 1 ? p[1] : "0");
    }

    /**
     * The Java major version Mojang requires for a Minecraft release. Used to skip a
     * (branch, version) pair with a clear message instead of failing deep inside a boot log when the
     * runner's JDK is too old.
     */
    public static int requiredJava(String mcVersion) {
        if (compare(mcVersion, "26.0") >= 0) return 25;
        if (compare(mcVersion, "1.20.5") >= 0) return 21;
        return 17;
    }

    /** Normalises a loader name to the lowercase form used in the matrix and known-issues file. */
    public static String loaderId(String loader) {
        return loader.trim().toLowerCase(Locale.ROOT);
    }
}
