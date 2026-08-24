package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shared setup for every functional scenario: the resolved matrix, the known-issue list, and the
 * recorder that produces the compatibility report.
 *
 * <p>Everything is static and set up once per JVM, because {@code functionalTest} runs single-forked
 * (each scenario owns a whole game process) and the report has to span all of the test classes in the
 * run — a per-class recorder would emit one report per class and each would overwrite the last.
 */
@Tag("functional")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class FunctionalTestBase {
    static HarnessConfig config;
    static KnownIssues knownIssues;
    static ResultRecorder recorder;
    static ScenarioRunner runner;
    static MatrixMain.Matrix matrix;

    @BeforeAll
    static void setUpHarness() throws IOException, InterruptedException {
        if (config != null) {
            return;
        }
        config = HarnessConfig.fromSystemProperties();
        knownIssues = KnownIssues.load(config.repoRoot());
        matrix = config.matrix();
        recorder = new ResultRecorder(matrix.branch, matrix.spec);
        matrix.warnings.forEach(recorder::warn);
        runner = new ScenarioRunner(config, knownIssues, recorder);

        System.out.println("[harness] branch " + matrix.branch + " range " + matrix.spec
            + " -> versions " + matrix.versions + " on " + matrix.loaders);
    }

    /**
     * Writes the report after the last class has run.
     *
     * <p>JUnit calls this per class, and the report is rewritten each time, so the final write contains
     * everything. The build failing on an unmarked failure is JUnit's job; the report is the record of
     * <em>which</em> versions are in what state.
     */
    @AfterAll
    static void writeReport() throws IOException {
        if (recorder == null) {
            return;
        }
        recorder.writeReport(config.reportDir());
        System.out.println("[harness] compatibility report: "
            + config.reportDir().resolve("compatibility.md"));
    }

    /** Every (version, loader) pair for server-side scenarios. */
    static Stream<Arguments> serverMatrix() throws IOException, InterruptedException {
        setUpHarness();
        List<Arguments> out = new ArrayList<>();
        for (String[] pair : matrix.pairs()) {
            out.add(Arguments.of(pair[0], pair[1]));
        }
        return out.stream();
    }

    /**
     * Every version for client scenarios, Fabric only.
     *
     * <p>NeoForge is left out on purpose: its client needs an interactive launcher profile that cannot be
     * provisioned headlessly. The client code under test lives in {@code common} and both loaders load
     * the same classes, so a Fabric client still exercises it.
     */
    static Stream<Arguments> clientMatrix() throws IOException, InterruptedException {
        setUpHarness();
        return matrix.versions.stream().map(Arguments::of);
    }
}
