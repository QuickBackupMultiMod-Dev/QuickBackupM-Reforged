package io.github.skydynamic.quickbackupmulti.harness;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Optional;

/**
 * Drives the Minecraft client's chat box with real keyboard events, via {@link Robot}, so a functional
 * test can exercise {@code /qb} commands the same way a player would.
 *
 * <p>This is the only viable in-band command channel into a running client: the game does not read a
 * command from stdin the way a dedicated server does, and building a companion mod purely to open one
 * would end up testing the companion mod's plumbing rather than the shipped client code. Robot injects
 * OS-level input events, so it needs a real or virtual (Xvfb) display — the same requirement
 * {@link ClientRun} already has for booting a client at all.
 *
 * <p>Robot delivers to whatever window the OS has focused, and cannot itself raise one, so every send
 * starts by focusing the game through {@link GameWindow}. That matters for more than reliability: a send
 * aimed at an unfocused client types its command into whichever window <em>is</em> focused, which on a
 * developer's machine is an editor or a terminal. {@link #verifyReachesClient} exists so that failure is
 * caught immediately, and named, instead of surfacing minutes later as an unrelated timeout.
 *
 * <p>Only lowercase letters, digits, space, {@code /}, {@code .} and {@code -} are supported. Every one
 * of those types with no Shift/AltGr, so the same code works regardless of the host's keyboard layout.
 * Callers are expected to phrase commands accordingly — a bare word for a backup name
 * ({@code clientprobe}, not {@code "client probe"}) and a 1-based {@code /qb restore <n>} rather than a
 * quoted name. {@link InGameCommandChannel} has no such restriction, which is one more reason it is
 * preferred when the mod under test offers it.
 */
public final class ClientInput implements ClientCommandChannel {
    private static final int KEY_DELAY_MS = 40;
    /** Lets the window manager register focus, and the client's next tick pick up the chat screen. */
    private static final int SETTLE_DELAY_MS = 300;
    /** How long a chat message may take to round-trip to the integrated server and be logged. */
    private static final Duration ECHO_TIMEOUT = Duration.ofSeconds(20);
    /** Focus can lose a race with the window manager once; give it a few tries before giving up. */
    private static final int VERIFY_ATTEMPTS = 3;

    private final Robot robot;
    private final GameProcess client;
    private final long pid;

    /**
     * @param client the running client, used to confirm that injected input actually arrived
     * @param pid    that client's process id, used to find its window
     */
    ClientInput(GameProcess client, long pid) {
        this.client = client;
        this.pid = pid;
        try {
            robot = new Robot();
        } catch (AWTException | HeadlessException e) {
            throw new HarnessException("Could not create a java.awt.Robot; a display is required "
                + "(on Linux, run under xvfb-run and set DISPLAY)", e);
        }
        robot.setAutoDelay(KEY_DELAY_MS);
        robot.setAutoWaitForIdle(true);
    }

    /**
     * Proves keyboard input reaches the client, by sending a plain chat message and waiting for the
     * integrated server to log it.
     *
     * <p>Worth doing once before the commands a scenario actually cares about. A {@code /qb} command that
     * never arrives is indistinguishable, from the log, from one that arrived and silently did nothing —
     * both leave no trace at all — so without this probe an input failure gets misattributed to the mod.
     * A plain message is used rather than a command because the server logs chat unconditionally, while a
     * command's output depends on permissions and on the mod being wired up, which is the very thing the
     * scenario is about to test.
     *
     * @throws HarnessException if the message never appears, with the cause named
     */
    public void verifyReachesClient() throws InterruptedException {
        String token = "qbmprobe" + pid;
        for (int attempt = 1; attempt <= VERIFY_ATTEMPTS; attempt++) {
            focusWindow();
            openChatForMessage();
            type(token);
            pressEnter();
            if (awaitEcho(token)) {
                return;
            }
        }
        throw new HarnessException("Keyboard input never reached the Minecraft client after "
            + VERIFY_ATTEMPTS + " attempts: a chat message typed with java.awt.Robot was not echoed by "
            + "the integrated server. The game window could not be focused, so anything this harness "
            + "types would go to another window instead. On Linux install xdotool and run under "
            + "xvfb-run; on Windows make sure the client window is not minimised and that no other "
            + "application is grabbing focus." + System.lineSeparator() + client.logTail(20));
    }

    private boolean awaitEcho(String token) throws InterruptedException {
        try {
            // The server logs chat as "<name> message" regardless of the client's locale.
            client.awaitLine("<QbmHarness> " + token, ECHO_TIMEOUT);
            return true;
        } catch (HarnessException e) {
            return false;
        }
    }

    /**
     * Types {@code /<command>} into the chat box and presses Enter.
     *
     * @param command the command without its leading slash, e.g. {@code "qb make clientprobe"}
     */
    @Override
    public void sendCommand(String command) throws InterruptedException {
        focusWindow();
        openChatAsCommand();
        type(command);
        pressEnter();
    }

    @Override
    public String describe() {
        return "synthetic keystrokes (java.awt.Robot)";
    }

    /**
     * Raises the game window and puts the pointer inside it.
     *
     * <p>The click is what actually hands focus to the game on a window manager that ignores a
     * programmatic raise, and it has to land on the game: clicking a fixed point such as the centre of
     * the primary screen focuses whatever is on top there, which is how a command ends up typed into
     * another application. So the window's real rectangle is looked up first, and the screen is used only
     * as a fallback when the platform cannot report one — in which case {@link #verifyReachesClient} is
     * what stops a scenario from trusting it.
     */
    private void focusWindow() throws InterruptedException {
        Rectangle target = GameWindow.activate(pid).orElseGet(() -> {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, screen.width, screen.height);
        });
        robot.mouseMove(target.x + target.width / 2, target.y + target.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(SETTLE_DELAY_MS);
    }

    /** The client binds {@code /} to opening chat pre-filled with {@code /}, matching a real player. */
    private void openChatAsCommand() throws InterruptedException {
        tap(KeyEvent.VK_SLASH);
        Thread.sleep(SETTLE_DELAY_MS);
    }

    /** {@code T} opens an empty chat box, for a message that must not be read as a command. */
    private void openChatForMessage() throws InterruptedException {
        tap(KeyEvent.VK_T);
        Thread.sleep(SETTLE_DELAY_MS);
    }

    private void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            tap(keyCodeFor(text.charAt(i)));
        }
    }

    private void pressEnter() {
        tap(KeyEvent.VK_ENTER);
    }

    private void tap(int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    private static int keyCodeFor(char c) {
        if (c >= 'a' && c <= 'z') {
            return KeyEvent.VK_A + (c - 'a');
        }
        if (c >= '0' && c <= '9') {
            return KeyEvent.VK_0 + (c - '0');
        }
        if (c == ' ') {
            return KeyEvent.VK_SPACE;
        }
        if (c == '.') {
            return KeyEvent.VK_PERIOD;
        }
        if (c == '-') {
            return KeyEvent.VK_MINUS;
        }
        if (c == '/') {
            return KeyEvent.VK_SLASH;
        }
        throw new HarnessException("ClientInput cannot type '" + c + "'; only lowercase letters, digits, "
            + "space, '.', '-' and '/' are supported");
    }

    /** True when a {@link Robot} can be created, i.e. a display is available. Never throws. */
    public static boolean supported() {
        try {
            new Robot();
            return true;
        } catch (AWTException | HeadlessException e) {
            return false;
        }
    }
}
