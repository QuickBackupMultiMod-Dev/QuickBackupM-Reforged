package io.github.skydynamic.quickbackupmulti.harness;

/**
 * Signals that the harness itself could not do its job — a download failed, a process died, a marker
 * never appeared.
 *
 * <p>Kept distinct from an assertion failure on purpose: an assertion failure means the mod is broken
 * on this version, while this means the test could not reach a verdict. The compatibility report
 * separates the two so infrastructure flakiness is never filed as a mod incompatibility.
 */
public class HarnessException extends RuntimeException {
    public HarnessException(String message) {
        super(message);
    }

    public HarnessException(String message, Throwable cause) {
        super(message, cause);
    }
}
