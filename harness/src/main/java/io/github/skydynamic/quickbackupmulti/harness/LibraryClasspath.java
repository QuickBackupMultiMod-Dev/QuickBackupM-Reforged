package io.github.skydynamic.quickbackupmulti.harness;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deduplicates a client classpath by Maven {@code group/artifact}, keeping the last jar for each
 * coordinate. Vanilla and Fabric both ship ASM; putting two versions on the classpath makes Knot
 * abort with {@code duplicate ASM classes found}.
 */
public final class LibraryClasspath {
    private LibraryClasspath() {
    }

    public static List<Path> dedupe(Collection<Path> jars) {
        Map<String, Path> byGa = new LinkedHashMap<>();
        for (Path jar : jars) {
            String ga = groupArtifactKey(jar);
            String key = ga == null ? jar.toString() : ga;
            byGa.remove(key);
            byGa.put(key, jar);
        }
        return new ArrayList<>(byGa.values());
    }

    static String groupArtifactKey(Path jar) {
        Path versionDir = jar.getParent();
        if (versionDir == null || versionDir.getParent() == null) {
            return null;
        }
        Path artifactDir = versionDir.getParent();
        Path groupDir = artifactDir.getParent();
        if (groupDir == null) {
            return null;
        }
        return groupDir.toString().replace('\\', '/') + "/" + artifactDir.getFileName();
    }
}
