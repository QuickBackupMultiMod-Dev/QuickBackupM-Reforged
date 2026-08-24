package io.github.skydynamic.quickbackupmulti.harness;

import java.time.Duration;

/**
 * How a scenario runs a {@code /qb} command on a client.
 *
 * <p>There are two implementations because there are two genuinely different situations. A client has no
 * console the way a dedicated server does, so the harness either talks to a cooperating build of the mod
 * ({@link InGameCommandChannel}) or drives the game's own chat box with synthetic keystrokes
 * ({@link ClientInput}). The first is what a scenario should get; the second is what it falls back to.
 */
public interface ClientCommandChannel {
    /**
     * Runs a command and returns once it has reached the game's command dispatcher.
     *
     * @param command the command without its leading slash, e.g. {@code "qb make clientprobe"}
     */
    void sendCommand(String command) throws InterruptedException;

    /** How this channel reaches the client, for a log line that explains which one a run used. */
    String describe();

    /**
     * How long to allow between sending a command and seeing it dispatched.
     *
     * <p>Generous because it also covers the client finishing its join: the harness sends as soon as the
     * integrated server reports the player logged in, which is marginally before the client has a player
     * of its own.
     */
    Duration DISPATCH_TIMEOUT = Duration.ofMinutes(3);
}
