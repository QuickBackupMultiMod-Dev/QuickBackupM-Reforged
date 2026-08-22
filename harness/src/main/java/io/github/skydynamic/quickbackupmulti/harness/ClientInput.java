package io.github.skydynamic.quickbackupmulti.harness;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

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
 * <p>Only lowercase letters, digits, space, {@code /}, {@code .} and {@code -} are supported. Every one
 * of those types with no Shift/AltGr, so the same code works regardless of the host's keyboard layout.
 * Callers are expected to phrase commands accordingly — a bare word for a backup name
 * ({@code clientprobe}, not {@code "client probe"}) and a 1-based {@code /qb restore <n>} rather than a
 * quoted name.
 */
public final class ClientInput {
    private static final int KEY_DELAY_MS = 40;
    /** Lets the window manager register focus, and the client's next tick pick up the chat screen. */
    private static final int SETTLE_DELAY_MS = 300;

    private final Robot robot;

    public ClientInput() {
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
     * Types {@code /<command>} into the chat box and presses Enter.
     *
     * @param command the command without its leading slash, e.g. {@code "qb make clientprobe"}
     */
    public void sendCommand(String command) throws InterruptedException {
        focusWindow();
        openChat();
        type(command);
        pressEnter();
    }

    /**
     * Clicks the centre of the screen so the game window has input focus rather than whatever last had
     * it. There is only ever one window in a harness run (one client per scenario, no other GUI
     * application sharing the display), so a screen-centre click is a reliable enough proxy for a real
     * window handle.
     */
    private void focusWindow() throws InterruptedException {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        robot.mouseMove(screen.width / 2, screen.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(SETTLE_DELAY_MS);
    }

    /** The client binds {@code /} to opening chat pre-filled with {@code /}, matching a real player. */
    private void openChat() throws InterruptedException {
        tap(KeyEvent.VK_SLASH);
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
