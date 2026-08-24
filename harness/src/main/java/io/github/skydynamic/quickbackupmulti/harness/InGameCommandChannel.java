package io.github.skydynamic.quickbackupmulti.harness;

/**
 * Runs client commands by writing them to the client's stdin, where the mod's harness command channel
 * picks them up and hands them to the game's connection.
 *
 * <p>This is the channel a client scenario should use. It involves no window and no keyboard, so it works
 * on a CI runner whose only display is a bare Xvfb with nothing to focus a window, and it cannot type into
 * a bystander application the way synthetic keystrokes can. It also accepts any command text, where
 * {@link ClientInput} is limited to the characters it knows how to type — so a client scenario can use a
 * quoted backup name exactly as the dedicated-server scenarios do.
 *
 * <p>Every send waits for the mod to report the command dispatched. Without that a command that never
 * arrived would be indistinguishable from one that arrived and did nothing, which is precisely the failure
 * that used to surface minutes later as an unrelated timeout.
 */
final class InGameCommandChannel implements ClientCommandChannel {
    /** Logged by {@code HarnessCommandChannel} once its reader thread is up. */
    static final String READY_MARKER = "[QBM-HARNESS] command channel ready";
    private static final String DISPATCHED_MARKER = "[QBM-HARNESS] dispatched ";

    private final GameProcess client;

    InGameCommandChannel(GameProcess client) {
        this.client = client;
    }

    /** True when the running client has a mod build with the channel enabled and listening. */
    static boolean availableOn(GameProcess client) {
        return client.sawLine(READY_MARKER);
    }

    @Override
    public void sendCommand(String command) throws InterruptedException {
        // Count first: awaitLine scans from the start of the log, so re-running the same command in one
        // scenario would otherwise match the previous run's line and return immediately.
        String marker = DISPATCHED_MARKER + command;
        int before = client.occurrences(marker);
        client.send(command);
        client.awaitOccurrences(marker, before + 1, DISPATCH_TIMEOUT);
    }

    @Override
    public String describe() {
        return "in-game command channel (client stdin)";
    }
}
