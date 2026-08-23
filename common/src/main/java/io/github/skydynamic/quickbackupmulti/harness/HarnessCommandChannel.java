package io.github.skydynamic.quickbackupmulti.harness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An in-band command channel for the functional test harness, so an automated client can run {@code /qb}
 * commands without anyone driving a keyboard.
 *
 * <p>Disabled unless {@code -Dqbm.harness.channel=true} is set, which only the harness does. Nothing here
 * runs — not even a thread — in a normal game.
 *
 * <p>The harness previously typed commands with {@code java.awt.Robot}, which delivers to whichever window
 * the OS has focused. That cannot work on CI, where the client runs under a bare Xvfb display with no
 * window manager to focus anything, and on a developer's machine it means a mistargeted command is typed
 * into whatever window happens to be in front. Reading commands from stdin removes the window from the
 * picture entirely; it also matches how the harness already drives a dedicated server, whose console reads
 * the same stream.
 *
 * <p>Commands are handed to {@link ClientPacketListener#sendCommand(String)} — the same call vanilla's
 * chat screen makes when a player presses Enter on a command — so the packet sent, the server-side
 * dispatch and the permission check are identical to a real player's. Only vanilla's keystroke handling is
 * skipped, and that is not this mod's code.
 *
 * <p>This class must only ever be <em>referenced</em> from behind a client check: it touches
 * {@link Minecraft}, which does not exist on a dedicated server, and the JVM loads a class at first use.
 */
public final class HarnessCommandChannel {
    /** Set by the harness when it launches a client; absent in every real installation. */
    public static final String ENABLE_PROPERTY = "qbm.harness.channel";
    /** A prefix the harness greps for. Logged in English so a configured locale cannot change it. */
    private static final String MARKER = "[QBM-HARNESS] ";
    private static final Logger LOGGER = LoggerFactory.getLogger("Qbm-Harness");

    /** How long a queued command waits for the player to exist before it is reported as undeliverable. */
    private static final long PLAYER_WAIT_MILLIS = 120_000;
    private static final long POLL_INTERVAL_MILLIS = 100;

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private HarnessCommandChannel() {
    }

    /**
     * Starts the reader thread, unless the harness property is absent or it is already running.
     *
     * <p>Safe to call more than once.
     */
    public static void startIfEnabled() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread reader = new Thread(HarnessCommandChannel::pump, "qbm-harness-command-channel");
        // A daemon: this must never be the thread that keeps a client alive after it asks to quit.
        reader.setDaemon(true);
        reader.start();
        LOGGER.info("{}command channel ready", MARKER);
    }

    private static void pump() {
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                String command = line.strip();
                if (command.isEmpty()) {
                    continue;
                }
                // Vanilla strips the slash before handing a command to the connection, so accept both
                // spellings rather than making the caller remember which one this expects.
                dispatch(command.startsWith("/") ? command.substring(1) : command);
            }
        } catch (IOException e) {
            // The harness closed the pipe, which is how a run ends. Nothing to report.
        }
    }

    private static void dispatch(String command) {
        ClientPacketListener connection = awaitConnection();
        if (connection == null) {
            LOGGER.warn("{}dispatch failed {}: no player in a world after {}ms",
                MARKER, command, PLAYER_WAIT_MILLIS);
            return;
        }
        // sendCommand touches the connection's chat state, which belongs to the render thread.
        Minecraft.getInstance().execute(() -> {
            try {
                connection.sendCommand(command);
                LOGGER.info("{}dispatched {}", MARKER, command);
            } catch (RuntimeException e) {
                LOGGER.warn("{}dispatch failed {}: {}", MARKER, command, e.toString());
            }
        });
    }

    /**
     * Waits until there is a player in a world, which is what a command needs.
     *
     * <p>The harness sends a command as soon as it believes the world is up, and "the world is up" is
     * observed from a log line written by the integrated server — a moment before the client has finished
     * attaching its own player. Waiting here absorbs that gap, and any join that is simply slow, without
     * the harness having to guess at a sleep.
     *
     * @return the live connection, or {@code null} if no player appeared in time
     */
    private static ClientPacketListener awaitConnection() {
        long deadline = System.currentTimeMillis() + PLAYER_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                ClientPacketListener connection = minecraft.getConnection();
                if (connection != null) {
                    return connection;
                }
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
