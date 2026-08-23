package io.github.skydynamic.quickbackupmulti.harness;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Drives a real Minecraft process: reads its log as it is produced, waits for markers, and writes
 * commands to its standard input.
 *
 * <p>Log lines are the harness's only in-band channel into a running game, so the reader thread runs
 * for the whole lifetime of the process and buffers everything. Nothing here parses the game's
 * user-facing messages: those come from language files and change with the configured locale, so
 * assertions target locale-independent markers and on-disk state instead.
 *
 * <p>Cleanup is deliberately violent. A stuck server keeps a lock on {@code logs/latest.log} and on
 * the world directory, which on Windows makes the next version's run fail for reasons that have
 * nothing to do with the mod, so {@link #close()} kills descendants too and waits for the handles to
 * actually go away.
 */
public final class GameProcess implements AutoCloseable {
    /** How long to let a well-behaved process shut down before killing it. */
    private static final Duration GRACEFUL_STOP = Duration.ofSeconds(120);

    private final String name;
    private final Process process;
    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private final BufferedWriter stdin;
    private final Thread reader;
    private final Path logFile;

    private GameProcess(String name, Process process, Path logFile) {
        this.name = name;
        this.process = process;
        this.logFile = logFile;
        this.stdin = new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new Thread(this::pump, "log-" + name);
        this.reader.setDaemon(true);
        this.reader.start();
    }

    /**
     * Starts a process, merging stderr into stdout so the ordering of a crash relative to the last
     * normal log line is preserved.
     *
     * @param logFile where to mirror the captured log, so a CI run can upload it as an artifact
     */
    public static GameProcess start(String name, Path workDir, Path logFile, List<String> command)
        throws IOException {
        Files.createDirectories(workDir);
        if (logFile != null) {
            Files.createDirectories(logFile.getParent());
        }
        Process p = new ProcessBuilder(command)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .start();
        return new GameProcess(name, p, logFile);
    }

    private void pump() {
        // The game writes its log in the platform charset; decoding as UTF-8 with replacement keeps a
        // mojibake'd localised message from killing the reader thread mid-run.
        try (InputStream in = process.getInputStream();
             var br = new java.io.BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            // Expected on a forceful kill: the pipe closes under the reader.
        }
    }

    /**
     * Blocks until a log line contains {@code needle}.
     *
     * @throws HarnessException if the timeout elapses or the process dies first
     */
    public String awaitLine(String needle, Duration timeout) throws InterruptedException {
        return await("a log line containing " + quote(needle), l -> l.contains(needle), timeout);
    }

    /** Blocks until a log line matches {@code pattern}. */
    public String awaitPattern(Pattern pattern, Duration timeout) throws InterruptedException {
        return await("a log line matching /" + pattern.pattern() + "/",
            l -> pattern.matcher(l).find(), timeout);
    }

    /**
     * Blocks until {@code needle} has appeared at least {@code count} times.
     *
     * <p>{@link #awaitLine} always scans from the start of the log, so it returns immediately for
     * anything that has already been seen. That is wrong for a marker a run produces more than once —
     * a second {@code Done (…)} after an in-process restart, or the next tick of a repeating schedule —
     * where the question is whether it happened <em>again</em>. Counting is the reliable way to ask.
     */
    public void awaitOccurrences(String needle, int count, Duration timeout) throws InterruptedException {
        await(count + "x a log line containing " + quote(needle), timeout,
            () -> occurrences(needle) >= count,
            () -> "saw it " + occurrences(needle) + " time(s)");
    }

    /** Blocks until a pattern has matched at least {@code count} times. */
    public void awaitOccurrences(Pattern pattern, int count, Duration timeout)
        throws InterruptedException {
        await(count + "x a log line matching /" + pattern.pattern() + "/", timeout,
            () -> linesMatching(pattern).size() >= count,
            () -> "saw it " + linesMatching(pattern).size() + " time(s)");
    }

    /** How many captured lines contain {@code needle}. */
    public int occurrences(String needle) {
        int n = 0;
        synchronized (lines) {
            for (String line : lines) {
                if (line.contains(needle)) n++;
            }
        }
        return n;
    }

    /**
     * Blocks until any of {@code needles} appears, and returns which one did. Used where success and a
     * known failure are both possible and waiting out the full timeout on failure would waste minutes.
     */
    public String awaitAny(Duration timeout, String... needles) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        int next = 0;
        while (true) {
            List<String> batch = snapshotFrom(next);
            next += batch.size();
            for (String line : batch) {
                for (String needle : needles) {
                    if (line.contains(needle)) {
                        return needle;
                    }
                }
            }
            if (!process.isAlive() && next >= lines.size()) {
                throw new HarnessException(name + " exited with code " + process.exitValue()
                    + " while waiting for any of " + String.join(", ", needles) + logContext());
            }
            if (System.nanoTime() > deadline) {
                throw new HarnessException("Timed out after " + timeout.toSeconds() + "s waiting for any of "
                    + String.join(", ", needles) + " from " + name + logContext());
            }
            Thread.sleep(200);
        }
    }

    private String await(String what, Predicate<String> match, Duration timeout)
        throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        int next = 0;
        while (true) {
            List<String> batch = snapshotFrom(next);
            next += batch.size();
            for (String line : batch) {
                if (match.test(line)) {
                    return line;
                }
            }
            // Only give up on a dead process once its buffered output has been drained, or a marker
            // printed just before exit would be missed.
            if (!process.isAlive() && next >= lines.size()) {
                throw new HarnessException(name + " exited with code " + process.exitValue()
                    + " while waiting for " + what + logContext());
            }
            if (System.nanoTime() > deadline) {
                throw new HarnessException("Timed out after " + timeout.toSeconds() + "s waiting for "
                    + what + " from " + name + logContext());
            }
            Thread.sleep(200);
        }
    }

    private List<String> snapshotFrom(int from) {
        synchronized (lines) {
            return from >= lines.size() ? List.of() : List.copyOf(lines.subList(from, lines.size()));
        }
    }

    /**
     * Polls {@code done} until it holds, the process dies, or the timeout elapses.
     *
     * <p>Separate from {@link #await(String, Predicate, Duration)} because a condition over the whole
     * captured log cannot be evaluated line-by-line as lines arrive.
     */
    private void await(String what, Duration timeout, BooleanSupplier done, Supplier<String> progress)
        throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (done.getAsBoolean()) {
                return;
            }
            if (!process.isAlive()) {
                // Give the reader a moment to drain what was buffered before the pipe closed: a marker
                // printed just before exit is exactly what a restart scenario waits for.
                Thread.sleep(300);
                if (done.getAsBoolean()) {
                    return;
                }
                throw new HarnessException(name + " exited with code " + process.exitValue()
                    + " while waiting for " + what + " (" + progress.get() + ")" + logContext());
            }
            if (System.nanoTime() > deadline) {
                throw new HarnessException("Timed out after " + timeout.toSeconds() + "s waiting for "
                    + what + " from " + name + " (" + progress.get() + ")" + logContext());
            }
            Thread.sleep(200);
        }
    }

    /** True if the given text has already appeared. Never blocks. */
    public boolean sawLine(String needle) {
        synchronized (lines) {
            for (String line : lines) {
                if (line.contains(needle)) return true;
            }
        }
        return false;
    }

    /** Every line matching {@code pattern} so far, in order. */
    public List<String> linesMatching(Pattern pattern) {
        List<String> out = new ArrayList<>();
        synchronized (lines) {
            for (String line : lines) {
                if (pattern.matcher(line).find()) out.add(line);
            }
        }
        return out;
    }

    /** Sends a console command, exactly as an operator would type it. */
    public void send(String command) {
        try {
            stdin.write(command);
            stdin.write(System.lineSeparator());
            stdin.flush();
        } catch (IOException e) {
            throw new HarnessException("Could not send " + quote(command) + " to " + name
                + " (process alive: " + process.isAlive() + ")" + logContext(), e);
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** The OS process id, which is how a client's window is located. */
    public long pid() {
        return process.pid();
    }

    /** Waits for the process to exit on its own and returns its exit code. */
    public int awaitExit(Duration timeout) throws InterruptedException {
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new HarnessException(name + " did not exit within " + timeout.toSeconds() + "s"
                + logContext());
        }
        return process.exitValue();
    }

    /** Asks the server to shut down, then makes sure it did. */
    public int stopServer() throws InterruptedException {
        if (process.isAlive()) {
            try {
                send("stop");
            } catch (HarnessException e) {
                // Already gone; fall through to the wait, which will report the real exit code.
            }
        }
        if (!process.waitFor(GRACEFUL_STOP.toMillis(), TimeUnit.MILLISECONDS)) {
            killTree();
            throw new HarnessException(name + " ignored 'stop' for " + GRACEFUL_STOP.toSeconds()
                + "s and was killed" + logContext());
        }
        return process.exitValue();
    }

    /** The whole captured log, for embedding in a failure report. */
    public String log() {
        synchronized (lines) {
            return String.join(System.lineSeparator(), lines);
        }
    }

    /** The last {@code count} lines, which is what a human actually reads first. */
    public String logTail(int count) {
        synchronized (lines) {
            int from = Math.max(0, lines.size() - count);
            return String.join(System.lineSeparator(), lines.subList(from, lines.size()));
        }
    }

    private String logContext() {
        String tail = logTail(40);
        String where = logFile == null ? "" : System.lineSeparator() + "Full log: " + logFile;
        return System.lineSeparator() + "--- last 40 lines of " + name + " ---"
            + System.lineSeparator() + tail
            + System.lineSeparator() + "--- end ---" + where;
    }

    /** Writes the captured log to the configured file. Safe to call more than once. */
    public void flushLog() {
        if (logFile == null) return;
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, log());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        flushLog();
        killTree();
        // The reader holds the pipe; joining it briefly stops it from racing a later log flush.
        try {
            reader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Kills the process and everything it spawned.
     *
     * <p>Descendants matter: the NeoForge {@code DEFAULT} auto-restart mode launches a brand new JVM
     * and lets the original exit, so killing only the direct child would leave a server running that
     * still holds the world directory.
     */
    private void killTree() {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        process.destroyForcibly();
        for (ProcessHandle h : descendants) {
            h.destroyForcibly();
        }
        try {
            process.waitFor(30, TimeUnit.SECONDS);
            for (ProcessHandle h : descendants) {
                h.onExit().get(15, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Nothing further to do; the file-handle wait below is the real safety net.
        }
        if (isWindows()) {
            // Windows releases file handles asynchronously after the process dies. Deleting a run
            // directory too early fails with "device or resource busy", so give the OS a moment.
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String quote(String s) {
        return "'" + s + "'";
    }
}
