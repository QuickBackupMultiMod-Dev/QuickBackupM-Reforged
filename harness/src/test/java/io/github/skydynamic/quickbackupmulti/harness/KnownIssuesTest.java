package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The xfail marking parser and matcher. These are the tests that keep a stale or malformed marking from
 * quietly turning a red build green.
 */
class KnownIssuesTest {

    private static KnownIssues fromJson(String json) {
        return KnownIssues.of(KnownIssues.parse(json));
    }

    @Test
    void parsesAFullyQualifiedEntry() {
        var entries = KnownIssues.parse("""
            { "issues": [
              { "branch": "1.21", "mc": "1.21.2", "loader": "neoforge", "side": "server",
                "scenario": "lifecycle", "reason": "boot fails", "issue": "#99" }
            ] }
            """);
        assertEquals(1, entries.size());
        KnownIssues.Entry e = entries.get(0);
        assertEquals("1.21", e.branch());
        assertEquals("1.21.2", e.mc());
        assertEquals("neoforge", e.loader());
        assertEquals("server", e.side());
        assertEquals("lifecycle", e.scenario());
        assertEquals("boot fails", e.reason());
        assertEquals("#99", e.issue());
    }

    @Test
    void absentSelectorsDefaultToWildcard() {
        KnownIssues.Entry e = KnownIssues.parse("""
            { "issues": [
              { "branch": "1.21", "reason": "mixin drift across the whole version", "issue": "#100" }
            ] }
            """).get(0);
        assertEquals("*", e.mc());
        assertEquals("*", e.loader());
        assertEquals("*", e.side());
        assertEquals("*", e.scenario());
    }

    @Test
    void aWildcardEntryMatchesEveryCombinationInTheBranch() {
        KnownIssues known = fromJson("""
            { "issues": [
              { "branch": "1.21", "reason": "whole branch broken", "issue": "#1" }
            ] }
            """);
        assertTrue(known.find("1.21", "1.21.3", "fabric", "server", "lifecycle").isPresent());
        assertTrue(known.find("1.21", "1.21", "neoforge", "client", "menu").isPresent());
        // A different branch must not be caught by this branch's marking.
        assertFalse(known.find("1.21.4", "1.21.4", "fabric", "server", "lifecycle").isPresent());
    }

    @Test
    void matchingIsCaseInsensitive() {
        KnownIssues known = fromJson("""
            { "issues": [
              { "branch": "1.21", "loader": "NeoForge", "reason": "x", "issue": "#2" }
            ] }
            """);
        assertTrue(known.find("1.21", "1.21", "neoforge", "server", "lifecycle").isPresent());
        assertTrue(known.find("1.21", "1.21", "NEOFORGE", "server", "lifecycle").isPresent());
        assertFalse(known.find("1.21", "1.21", "fabric", "server", "lifecycle").isPresent());
    }

    @Test
    void aMissingReasonIsFatal() {
        // The whole point of a marking is to record *why* and *where tracked*, so a marking without a
        // reason must fail loudly rather than silently suppressing a failure.
        HarnessException e = assertThrows(HarnessException.class, () -> KnownIssues.parse("""
            { "issues": [ { "branch": "1.21", "issue": "#3" } ] }
            """));
        assertTrue(e.getMessage().contains("reason"), "message should name the missing field");
    }

    @Test
    void aMissingIssueIsFatal() {
        assertThrows(HarnessException.class, () -> KnownIssues.parse("""
            { "issues": [ { "branch": "1.21", "reason": "broken" } ] }
            """));
    }

    @Test
    void aMissingBranchIsFatal() {
        assertThrows(HarnessException.class, () -> KnownIssues.parse("""
            { "issues": [ { "reason": "broken", "issue": "#4" } ] }
            """));
    }

    @Test
    void aMissingIssuesArrayIsFatal() {
        assertThrows(HarnessException.class, () -> KnownIssues.parse("{ \"other\": [] }"));
    }

    @Test
    void anEmptyIssuesArrayParsesToNothing() {
        assertTrue(KnownIssues.parse("{ \"issues\": [] }").isEmpty());
    }

    @Test
    void findReturnsTheFirstMatchingEntry() {
        KnownIssues known = fromJson("""
            { "issues": [
              { "branch": "1.21", "mc": "1.21.2", "reason": "specific", "issue": "#5" },
              { "branch": "1.21", "reason": "catch-all", "issue": "#6" }
            ] }
            """);
        Optional<KnownIssues.Entry> match = known.find("1.21", "1.21.2", "fabric", "server", "lifecycle");
        assertTrue(match.isPresent());
        assertEquals("#5", match.get().issue(), "the more specific, earlier entry should win");
    }

    @Test
    void anUnmatchedCombinationReturnsEmpty() {
        KnownIssues known = fromJson("""
            { "issues": [
              { "branch": "1.21", "mc": "1.21.2", "reason": "specific", "issue": "#5" }
            ] }
            """);
        assertTrue(known.find("1.21", "1.21.3", "fabric", "server", "lifecycle").isEmpty());
    }

    @Test
    void sideNormalisesFromBoolean() {
        assertEquals("client", KnownIssues.side(true));
        assertEquals("server", KnownIssues.side(false));
    }
}
