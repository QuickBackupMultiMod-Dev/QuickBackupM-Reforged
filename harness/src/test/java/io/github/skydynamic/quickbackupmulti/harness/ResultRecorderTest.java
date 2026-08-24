package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.skydynamic.quickbackupmulti.harness.ResultRecorder.Status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The report writer, which turns a pile of scenario verdicts into the enumerated problem list the user
 * asked for. The important behaviours are that "green" is honest and that a stale marking is called out
 * loudly enough to get removed.
 */
class ResultRecorderTest {

    private static ResultRecorder recorder() {
        return new ResultRecorder("1.21", "[1.21,1.21.4]");
    }

    private static ResultRecorder.Result result(String mc, Status status) {
        return new ResultRecorder.Result(mc, "fabric", "server", "lifecycle", status, null, null);
    }

    @Test
    void aCleanRunIsGreen() {
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.PASSED));
        r.record(result("1.21.1", Status.PASSED));
        assertTrue(r.green());
    }

    @Test
    void aFailureIsNotGreen() {
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.PASSED));
        r.record(result("1.21.1", Status.FAILED));
        assertFalse(r.green());
    }

    @Test
    void aKnownFailureStaysGreen() {
        // The entire point of a marking: a known-broken version does not turn CI red.
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.KNOWN_FAILURE));
        assertTrue(r.green());
    }

    @Test
    void aSkipStaysGreen() {
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.SKIPPED));
        assertTrue(r.green());
    }

    @Test
    void aStaleMarkingIsNotGreen() {
        // A marking that outlived its bug has to be noisy, or it silently absorbs the next real regression.
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.UNEXPECTED_PASS));
        assertFalse(r.green());
    }

    @Test
    void aHarnessErrorIsNotGreen() {
        // Infrastructure trouble is not a mod incompatibility, but it does mean the run reached no verdict,
        // so it must not be quietly reported as success.
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.ERROR));
        assertFalse(r.green());
    }

    @Test
    void recordingTheSameCombinationTwiceKeepsTheLatest() {
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.FAILED));
        r.record(result("1.21", Status.PASSED));
        assertEquals(1, r.results().size());
        assertEquals(Status.PASSED, r.results().get(0).status());
        assertTrue(r.green());
    }

    @Test
    void resultsAreSortedByVersionThenLoaderSideScenario() {
        ResultRecorder r = recorder();
        r.record(new ResultRecorder.Result("1.21.10", "fabric", "server", "lifecycle", Status.PASSED, null, null));
        r.record(new ResultRecorder.Result("1.21.2", "neoforge", "server", "lifecycle", Status.PASSED, null, null));
        r.record(new ResultRecorder.Result("1.21.2", "fabric", "server", "lifecycle", Status.PASSED, null, null));

        var results = r.results();
        // Numeric-per-segment: 1.21.2 comes before 1.21.10, not after it lexically.
        assertEquals("1.21.2", results.get(0).mc());
        assertEquals("fabric", results.get(0).loader());
        assertEquals("1.21.2", results.get(1).mc());
        assertEquals("neoforge", results.get(1).loader());
        assertEquals("1.21.10", results.get(2).mc());
    }

    @Test
    void markdownListsStaleMarkingsForRemoval() {
        ResultRecorder r = recorder();
        r.record(new ResultRecorder.Result("1.21.2", "neoforge", "server", "lifecycle",
            Status.UNEXPECTED_PASS, "the marking is stale; delete it", "#42"));

        String md = r.markdown();
        assertTrue(md.contains("Stale markings"), "report should have a stale-markings section");
        assertTrue(md.contains("1.21.2"), "report should name the version that now passes");
        assertTrue(md.contains("#42"), "report should name the issue the marking was filed against");
        assertTrue(md.contains(".github/known-issues.json"),
            "report should tell the reader where to remove the marking");
    }

    @Test
    void markdownEscapesPipesInDetailSoTheTableSurvives() {
        ResultRecorder r = recorder();
        r.record(new ResultRecorder.Result("1.21", "fabric", "server", "lifecycle",
            Status.FAILED, "expected a | b but got c", null));
        String md = r.markdown();
        assertFalse(md.contains("got c | expected"), "sanity");
        assertTrue(md.contains("a \\| b"), "a pipe in a detail must be escaped");
    }

    @Test
    void writeReportEmitsBothFiles(@TempDir Path dir) throws IOException {
        ResultRecorder r = recorder();
        r.record(result("1.21", Status.PASSED));
        r.writeReport(dir);

        Path md = dir.resolve("compatibility.md");
        Path json = dir.resolve("compatibility.json");
        assertTrue(Files.exists(md));
        assertTrue(Files.exists(json));
        assertTrue(Files.readString(json).contains("\"green\":true"));
        assertTrue(Files.readString(json).contains("\"branch\":\"1.21\""));
    }

    @Test
    void emptyRunReportsThatNothingWasTested() {
        assertTrue(recorder().markdown().contains("No versions were tested"));
    }
}
