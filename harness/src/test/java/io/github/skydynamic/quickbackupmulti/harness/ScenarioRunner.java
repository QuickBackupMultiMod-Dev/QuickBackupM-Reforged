package io.github.skydynamic.quickbackupmulti.harness;

import org.opentest4j.TestAbortedException;

import java.util.Optional;

/**
 * Runs one scenario against one (version, loader, side) and turns its outcome into a recorded verdict.
 *
 * <p>This is where the xfail policy lives. A scenario body either completes or throws; what that means
 * depends on whether the combination is marked in {@code known-issues.json}:
 *
 * <table>
 *   <tr><th></th><th>not marked</th><th>marked</th></tr>
 *   <tr><td>threw</td><td>FAILED — rethrown, build goes red</td>
 *       <td>KNOWN_FAILURE — recorded, test aborted so CI stays green</td></tr>
 *   <tr><td>passed</td><td>PASSED</td>
 *       <td>UNEXPECTED_PASS — <em>fails</em>, because the marking is now hiding a working version</td></tr>
 * </table>
 *
 * <p>A marked failure aborts rather than passes so it shows as skipped in the JUnit report, which keeps
 * "green" honest: nothing claims to have verified a version that is known not to work.
 */
public final class ScenarioRunner {
    private final HarnessConfig config;
    private final KnownIssues knownIssues;
    private final ResultRecorder recorder;

    public ScenarioRunner(HarnessConfig config, KnownIssues knownIssues, ResultRecorder recorder) {
        this.config = config;
        this.knownIssues = knownIssues;
        this.recorder = recorder;
    }

    /** A scenario body, which may throw anything. */
    public interface Body {
        void run() throws Exception;
    }

    /**
     * Runs {@code body} for one combination.
     *
     * @throws TestAbortedException when the combination is filtered out, unsupported on this machine, or
     *                              a known failure — all of which are "skipped", not "passed"
     */
    public void run(String mc, String loader, String side, String scenario, Body body) {
        if (!config.includesVersion(mc) || !config.includesLoader(loader)
            || !config.includesSide(side) || !config.includesScenario(scenario)) {
            // Filtered out by -Pqbm.versions and friends: not recorded at all, since a narrowed run
            // should not report the versions it deliberately did not touch.
            throw new TestAbortedException("filtered out by qbm.* properties");
        }
        if (!config.hasJavaFor(mc)) {
            String detail = "needs Java " + McVersions.requiredJava(mc)
                + "; the runner has Java " + Runtime.version().feature();
            recorder.record(new ResultRecorder.Result(mc, loader, side, scenario,
                ResultRecorder.Status.SKIPPED, detail, null));
            throw new TestAbortedException(detail);
        }

        Optional<KnownIssues.Entry> marked =
            knownIssues.find(config.branch(), mc, loader, side, scenario);

        try {
            body.run();
        } catch (TestAbortedException e) {
            // The scenario itself decided it could not run here (no display, for instance). Its own
            // recorder call already described why.
            throw e;
        } catch (HarnessException e) {
            // Infrastructure, not the mod. Never reported as an incompatibility, and never suppressed by
            // a marking either: a download failure is not evidence that the marked bug is still there.
            recorder.record(new ResultRecorder.Result(mc, loader, side, scenario,
                ResultRecorder.Status.ERROR, e.getMessage(), null));
            throw e;
        } catch (Throwable e) {
            if (marked.isPresent()) {
                KnownIssues.Entry entry = marked.get();
                recorder.record(new ResultRecorder.Result(mc, loader, side, scenario,
                    ResultRecorder.Status.KNOWN_FAILURE, entry.reason(), entry.issue()));
                throw new TestAbortedException("known issue (" + entry.issue() + "): "
                    + entry.reason() + " — failed as expected: " + summarise(e));
            }
            recorder.record(new ResultRecorder.Result(mc, loader, side, scenario,
                ResultRecorder.Status.FAILED, summarise(e), null));
            throw asRuntime(e);
        }

        if (marked.isPresent()) {
            KnownIssues.Entry entry = marked.get();
            recorder.record(new ResultRecorder.Result(mc, loader, side, scenario,
                ResultRecorder.Status.UNEXPECTED_PASS, "the marking is stale; delete it", entry.issue()));
            // Deliberately a failure. A marking that outlives its bug silently absorbs the next real
            // regression on that version, so it has to be noisy enough that someone removes it.
            throw new AssertionError("This combination is marked as a known issue in "
                + KnownIssues.file(config.repoRoot()) + " but it passed."
                + System.lineSeparator() + "  entry:  " + entry.describe()
                + System.lineSeparator() + "  reason: " + entry.reason()
                + System.lineSeparator() + "  issue:  " + entry.issue()
                + System.lineSeparator()
                + "Remove the entry (or narrow it) now that this version works.");
        }

        recorder.record(new ResultRecorder.Result(mc, loader, side, scenario,
            ResultRecorder.Status.PASSED, null, null));
    }

    /** Records a skip with a reason, for a scenario that cannot run on this machine. */
    public void skip(String mc, String loader, String side, String scenario, String reason) {
        recorder.record(new ResultRecorder.Result(mc, loader, side, scenario,
            ResultRecorder.Status.SKIPPED, reason, null));
        throw new TestAbortedException(reason);
    }

    private static String summarise(Throwable e) {
        String message = e.getMessage();
        String type = e.getClass().getSimpleName();
        if (message == null || message.isBlank()) {
            return type;
        }
        // The first line is the assertion; the rest is usually an embedded log tail that belongs in the
        // artifact, not in a table cell.
        String firstLine = message.lines().findFirst().orElse(message);
        return type + ": " + firstLine;
    }

    private static RuntimeException asRuntime(Throwable e) {
        if (e instanceof RuntimeException re) return re;
        if (e instanceof Error error) throw error;
        return new RuntimeException(e);
    }
}
