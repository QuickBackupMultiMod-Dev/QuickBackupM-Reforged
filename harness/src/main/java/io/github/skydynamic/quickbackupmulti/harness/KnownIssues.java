package io.github.skydynamic.quickbackupmulti.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The list of (branch, version, loader, side, scenario) combinations that are known to be broken.
 *
 * <p>A marked combination that fails does not turn CI red — the failure is already known and tracked.
 * The important half is the other direction: a marked combination that <em>passes</em> is reported as a
 * stale marking that should be deleted, because a marking nobody removes eventually hides a real
 * regression.
 *
 * <p>Backed by {@code .github/known-issues.json} at the repository root, which is deliberately
 * hand-edited: each entry has to carry a reason and an issue link, so "this is broken" is always
 * traceable to a decision someone made.
 */
public final class KnownIssues {
    /**
     * One marked combination. Any of {@code mc}, {@code loader}, {@code side} or {@code scenario} may be
     * {@code "*"} to cover a whole row of the matrix — a mixin that broke for a whole Minecraft version
     * should not need eight near-identical entries.
     */
    public record Entry(String branch, String mc, String loader, String side, String scenario,
                        String reason, String issue) {
        boolean matches(String branch, String mc, String loader, String side, String scenario) {
            return glob(this.branch, branch)
                && glob(this.mc, mc)
                && glob(this.loader, loader)
                && glob(this.side, side)
                && glob(this.scenario, scenario);
        }

        private static boolean glob(String pattern, String value) {
            return "*".equals(pattern) || pattern.equalsIgnoreCase(value);
        }

        /** A human-readable identifier, used in the report and in failure messages. */
        public String describe() {
            return branch + " / " + mc + " / " + loader + " / " + side + " / " + scenario;
        }
    }

    private final List<Entry> entries;

    private KnownIssues(List<Entry> entries) {
        this.entries = entries;
    }

    /** Wraps an already-parsed list, for tests and for callers that hold entries directly. */
    static KnownIssues of(List<Entry> entries) {
        return new KnownIssues(entries);
    }

    /** Loads the file, treating an absent file as "nothing is known to be broken". */
    public static KnownIssues load(Path repoRoot) {
        Path file = file(repoRoot);
        if (!Files.exists(file)) {
            return new KnownIssues(List.of());
        }
        try {
            return new KnownIssues(parse(Files.readString(file)));
        } catch (IOException e) {
            throw new HarnessException("Could not read " + file, e);
        }
    }

    public static Path file(Path repoRoot) {
        return repoRoot.resolve(".github/known-issues.json");
    }

    /** The entry covering a combination, if any. */
    public Optional<Entry> find(String branch, String mc, String loader, String side, String scenario) {
        return entries.stream()
            .filter(e -> e.matches(branch, mc, loader, side, scenario))
            .findFirst();
    }

    public List<Entry> all() {
        return entries;
    }

    /**
     * Parses the {@code issues} array.
     *
     * <p>Hand-rolled rather than pulling in a JSON library, because the harness deliberately has almost
     * no dependencies: it has to be runnable from a bare Gradle task on a fresh runner. The format is
     * fixed and small, and a malformed entry fails loudly rather than being skipped — a silently ignored
     * marking would make a red build look green for the wrong reason.
     */
    static List<Entry> parse(String json) {
        List<Entry> out = new ArrayList<>();
        int at = json.indexOf("\"issues\"");
        if (at < 0) {
            throw new HarnessException("known-issues.json has no \"issues\" array");
        }
        Matcher objects = Pattern.compile("\\{[^{}]*}").matcher(json.substring(at));
        while (objects.find()) {
            String entry = objects.group();
            out.add(new Entry(
                required(entry, "branch"),
                optional(entry, "mc"),
                optional(entry, "loader"),
                optional(entry, "side"),
                optional(entry, "scenario"),
                required(entry, "reason"),
                required(entry, "issue")));
        }
        return out;
    }

    private static String required(String entry, String key) {
        String value = field(entry, key);
        if (value == null) {
            throw new HarnessException("A known-issues entry is missing \"" + key + "\": " + entry
                + " — every marking must record what is broken and where it is tracked.");
        }
        return value;
    }

    /** An absent selector means "every value", which is what {@code *} expresses. */
    private static String optional(String entry, String key) {
        String value = field(entry, key);
        return value == null ? "*" : value;
    }

    private static String field(String entry, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(entry);
        return m.find() ? m.group(1).replace("\\\"", "\"") : null;
    }

    /** Normalises a side name so {@code SERVER} and {@code server} mark the same thing. */
    public static String side(boolean client) {
        return client ? "client" : "server";
    }

    static String normalise(String s) {
        return s == null ? "*" : s.trim().toLowerCase(Locale.ROOT);
    }
}
