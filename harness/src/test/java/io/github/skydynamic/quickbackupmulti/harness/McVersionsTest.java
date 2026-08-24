package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the range semantics the whole matrix rests on. Every branch's set of tested versions comes
 * out of these two methods, so getting them wrong silently means testing the wrong game versions.
 */
class McVersionsTest {
    /** Enough of a Mojang manifest to cover the ordering traps, in the real newest-first order. */
    private static final String MANIFEST = """
        {"latest":{"release":"26.2","snapshot":"26.2"},"versions":[
          {"id":"26.2","type":"release","url":"x"},
          {"id":"26.2-rc1","type":"snapshot","url":"x"},
          {"id":"26.1.2","type":"release","url":"x"},
          {"id":"26.1.1","type":"release","url":"x"},
          {"id":"26.1","type":"release","url":"x"},
          {"id":"1.21.11","type":"release","url":"x"},
          {"id":"1.21.10","type":"release","url":"x"},
          {"id":"1.21.9","type":"release","url":"x"},
          {"id":"25w14craftmine","type":"snapshot","url":"x"},
          {"id":"1.21.3","type":"release","url":"x"},
          {"id":"1.21.2","type":"release","url":"x"},
          {"id":"1.21.2-pre1","type":"snapshot","url":"x"},
          {"id":"1.21.1","type":"release","url":"x"},
          {"id":"1.21","type":"release","url":"x"},
          {"id":"1.20.6","type":"release","url":"x"},
          {"id":"1.20.5","type":"release","url":"x"},
          {"id":"1.20.4","type":"release","url":"x"},
          {"id":"b1.7.3","type":"old_beta","url":"x"}
        ]}""";

    @Test
    void releasesDropsSnapshotsAndOrdersOldestFirst() {
        List<String> releases = McVersions.releases(MANIFEST);
        assertFalse(releases.contains("1.21.2-pre1"), "pre-releases are out of scope");
        assertFalse(releases.contains("25w14craftmine"), "snapshots are out of scope");
        assertFalse(releases.contains("b1.7.3"), "old_beta is not type=release");
        assertEquals("1.20.4", releases.get(0));
        assertEquals("26.2", releases.get(releases.size() - 1));
    }

    @Test
    void halfOpenIntervalExcludesUpperBound() {
        // Branch 1.21's real spec: 1.21.4 itself belongs to the next branch.
        assertEquals(List.of("1.21", "1.21.1", "1.21.2", "1.21.3"),
            McVersions.inRange("[1.21, 1.21.4)", McVersions.releases(MANIFEST)));
    }

    @Test
    void closedIntervalIncludesBothBounds() {
        assertEquals(List.of("1.21", "1.21.1", "1.21.2"),
            McVersions.inRange("[1.21, 1.21.2]", McVersions.releases(MANIFEST)));
    }

    @Test
    void exclusiveLowerBoundSkipsTheNamedVersion() {
        assertEquals(List.of("1.21.1", "1.21.2"),
            McVersions.inRange("(1.21, 1.21.2]", McVersions.releases(MANIFEST)));
    }

    @Test
    void openUpperBoundReachesTheNewestRelease() {
        assertEquals(List.of("1.21.11", "26.1", "26.1.1", "26.1.2", "26.2"),
            McVersions.inRange("[1.21.11, )", McVersions.releases(MANIFEST)));
    }

    @Test
    void bareSpecIsNarrowedToTheNamedVersion() {
        // In Maven a bare version means ">=", and that is what ships in fabric.mod.json, but testing
        // every later version against a branch pinned to one is meaningless. See BARE_SPEC_WARNING.
        assertTrue(McVersions.isBareSpec("1.20.5"));
        assertEquals(List.of("1.20.5"), McVersions.inRange("1.20.5", McVersions.releases(MANIFEST)));
    }

    @Test
    void bareSpecForAnUnreleasedVersionYieldsNothing() {
        assertEquals(List.of(), McVersions.inRange("1.99.9", McVersions.releases(MANIFEST)));
    }

    @Test
    void intervalIsRecognisedAsNotBare() {
        assertFalse(McVersions.isBareSpec("[1.21, 1.21.4)"));
        assertFalse(McVersions.isBareSpec("  (1.21, )  "));
    }

    @Test
    void unparseableSpecFailsLoudly() {
        // Better to break the build than to silently test an empty matrix.
        assertThrows(IllegalArgumentException.class,
            () -> McVersions.inRange("[1.21", McVersions.releases(MANIFEST)));
        assertThrows(IllegalArgumentException.class,
            () -> McVersions.inRange("[1.21, 1.22, 1.23)", McVersions.releases(MANIFEST)));
    }

    @Test
    void compareIsNumericPerSegmentNotLexicographic() {
        assertTrue(McVersions.compare("1.21.10", "1.21.9") > 0, "string compare gets this backwards");
        assertTrue(McVersions.compare("26.1", "1.21.11") > 0, "the 26.x scheme sorts above 1.x");
        assertTrue(McVersions.compare("1.21", "1.21.0") == 0, "missing segments count as zero");
        assertTrue(McVersions.compare("1.21", "1.21.1") < 0);
        assertEquals(0, McVersions.compare("1.20.4", "1.20.4"));
    }

    @Test
    void neoForgePrefixMapsBothVersionSchemes() {
        assertEquals("21.3", McVersions.neoForgePrefix("1.21.3"));
        assertEquals("21.0", McVersions.neoForgePrefix("1.21"), "a two-segment 1.x means patch 0");
        assertEquals("26.1", McVersions.neoForgePrefix("26.1.2"));
        assertEquals("26.2", McVersions.neoForgePrefix("26.2"));
    }

    @Test
    void requiredJavaFollowsMojangsBumps() {
        assertEquals(17, McVersions.requiredJava("1.20.4"));
        assertEquals(21, McVersions.requiredJava("1.20.5"));
        assertEquals(21, McVersions.requiredJava("1.21.11"));
        assertEquals(25, McVersions.requiredJava("26.1"));
        assertEquals(25, McVersions.requiredJava("26.2"));
    }
}
