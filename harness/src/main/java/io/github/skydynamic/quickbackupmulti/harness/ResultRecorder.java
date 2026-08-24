package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects one verdict per (version, loader, side, scenario) and writes the compatibility report.
 *
 * <p>This is what turns a pile of test results into the enumeration of problem versions the matrix
 * exists to produce: which versions work, which are known-broken and why, and which markings are now
 * stale.
 *
 * <p>Instances are shared across a whole {@code functionalTest} run and written to from many test
 * methods, so every mutator is synchronised.
 */
public final class ResultRecorder {
    /** What happened to one combination. */
    public enum Status {
        /** Worked. */
        PASSED,
        /** Broke, and was not marked as known-broken: this fails the build. */
        FAILED,
        /** Broke exactly as {@code known-issues.json} says it would: recorded, not fatal. */
        KNOWN_FAILURE,
        /**
         * Marked as known-broken but passed. Reported so the marking gets deleted — a marking left in
         * place after a fix silently suppresses the next real regression.
         */
        UNEXPECTED_PASS,
        /** Not attempted (no display for a client, no JDK for the version, filtered out). */
        SKIPPED,
        /**
         * The harness could not reach a verdict — a download failed, a process died for an unrelated
         * reason. Kept separate from FAILED so infrastructure trouble is never filed as an
         * incompatibility.
         */
        ERROR
    }

    /** One row of the report. */
    public record Result(String mc, String loader, String side, String scenario, Status status,
                         String detail, String issue) {
        String key() {
            return mc + "|" + loader + "|" + side + "|" + scenario;
        }
    }

    private final Map<String, Result> results = new LinkedHashMap<>();
    private final String branch;
    private final String spec;
    private final List<String> warnings = new ArrayList<>();

    public ResultRecorder(String branch, String spec) {
        this.branch = branch;
        this.spec = spec;
    }

    public synchronized void record(Result result) {
        results.put(result.key(), result);
    }

    /** A branch-level note, such as a support range that over-claims. */
    public synchronized void warn(String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    public synchronized List<Result> results() {
        return results.values().stream()
            .sorted(Comparator.comparing((Result r) -> r.mc(), McVersions::compare)
                .thenComparing(Result::loader)
                .thenComparing(Result::side)
                .thenComparing(Result::scenario))
            .toList();
    }

    public synchronized List<Result> withStatus(Status status) {
        return results().stream().filter(r -> r.status() == status).toList();
    }

    /** True when nothing needs a human's attention. */
    public synchronized boolean green() {
        return withStatus(Status.FAILED).isEmpty()
            && withStatus(Status.ERROR).isEmpty()
            && withStatus(Status.UNEXPECTED_PASS).isEmpty();
    }

    /**
     * Writes {@code compatibility.md} and, when running under GitHub Actions, appends the same content to
     * the job summary so the result is visible without downloading an artifact.
     */
    public synchronized void writeReport(Path reportDir) throws IOException {
        Files.createDirectories(reportDir);
        String markdown = markdown();
        Files.writeString(reportDir.resolve("compatibility.md"), markdown);
        Files.writeString(reportDir.resolve("compatibility.json"), json());

        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), markdown + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
    }

    String markdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Minecraft compatibility — branch `").append(branch).append("`\n\n");
        sb.append("Declared support range: `").append(spec).append("`\n\n");

        List<Result> all = results();
        if (all.isEmpty()) {
            sb.append("No versions were tested.\n");
            return sb.toString();
        }

        sb.append("| Minecraft | Loader | Side | Scenario | Result | Notes |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (Result r : all) {
            sb.append("| ").append(r.mc())
                .append(" | ").append(r.loader())
                .append(" | ").append(r.side())
                .append(" | ").append(r.scenario())
                .append(" | ").append(icon(r.status()))
                .append(" | ").append(notes(r))
                .append(" |\n");
        }

        sb.append('\n');
        appendCount(sb, "Passed", Status.PASSED);
        appendCount(sb, "Failed", Status.FAILED);
        appendCount(sb, "Known failures", Status.KNOWN_FAILURE);
        appendCount(sb, "Stale markings", Status.UNEXPECTED_PASS);
        appendCount(sb, "Skipped", Status.SKIPPED);
        appendCount(sb, "Harness errors", Status.ERROR);

        List<Result> stale = withStatus(Status.UNEXPECTED_PASS);
        if (!stale.isEmpty()) {
            sb.append("\n### Stale markings — remove these from `.github/known-issues.json`\n\n");
            for (Result r : stale) {
                sb.append("- `").append(r.mc()).append(" / ").append(r.loader())
                    .append(" / ").append(r.side()).append(" / ").append(r.scenario())
                    .append("` now passes");
                if (r.issue() != null && !r.issue().isBlank()) {
                    sb.append(" (marked against ").append(r.issue()).append(')');
                }
                sb.append('\n');
            }
        }

        if (!warnings.isEmpty()) {
            sb.append("\n### Notes\n\n");
            for (String w : warnings) {
                sb.append("- ").append(w).append('\n');
            }
        }
        return sb.toString();
    }

    private void appendCount(StringBuilder sb, String label, Status status) {
        int n = withStatus(status).size();
        if (n > 0) {
            sb.append("- ").append(label).append(": ").append(n).append('\n');
        }
    }

    private static String icon(Status status) {
        return switch (status) {
            case PASSED -> "✅ pass";
            case FAILED -> "❌ **fail**";
            case KNOWN_FAILURE -> "⚠️ known issue";
            case UNEXPECTED_PASS -> "🧹 stale marking";
            case SKIPPED -> "⏭️ skipped";
            case ERROR -> "💥 harness error";
        };
    }

    private static String notes(Result r) {
        StringBuilder sb = new StringBuilder();
        if (r.issue() != null && !r.issue().isBlank()) {
            sb.append(r.issue()).append(' ');
        }
        if (r.detail() != null && !r.detail().isBlank()) {
            // One line, escaped: a stack trace would destroy the table.
            String detail = r.detail().replace("|", "\\|").replaceAll("\\s+", " ").trim();
            sb.append(detail.length() > 300 ? detail.substring(0, 300) + "…" : detail);
        }
        return sb.length() == 0 ? "" : sb.toString();
    }

    String json() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"branch\":\"").append(branch).append("\",\"spec\":\"").append(spec)
            .append("\",\"green\":").append(green()).append(",\"results\":[");
        List<Result> all = results();
        for (int i = 0; i < all.size(); i++) {
            Result r = all.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"mc\":\"").append(r.mc())
                .append("\",\"loader\":\"").append(r.loader())
                .append("\",\"side\":\"").append(r.side())
                .append("\",\"scenario\":\"").append(r.scenario())
                .append("\",\"status\":\"").append(r.status())
                .append("\",\"issue\":").append(quoteOrNull(r.issue()))
                .append(",\"detail\":").append(quoteOrNull(r.detail()))
                .append('}');
        }
        return sb.append("]}").toString();
    }

    private static String quoteOrNull(String s) {
        if (s == null) return "null";
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "").replace("\t", " ") + '"';
    }
}
