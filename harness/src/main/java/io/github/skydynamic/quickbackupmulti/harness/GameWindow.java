package io.github.skydynamic.quickbackupmulti.harness;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Finds and focuses the game's own window, so keyboard events injected by {@link ClientInput} go to
 * Minecraft rather than to whatever happened to have focus.
 *
 * <p>{@link java.awt.Robot} can only inject input into the window the OS considers focused; it cannot
 * enumerate or raise another process's windows. That leaves no portable way to do this, so each platform
 * gets its own implementation and anything unsupported reports "unknown" rather than guessing. Guessing
 * is what the harness used to do — it clicked the centre of the primary screen and assumed Minecraft was
 * underneath — and when that assumption failed the symptom was not a focus error but a scenario that
 * timed out several minutes later waiting for a command that had been typed into a bystander window.
 */
final class GameWindow {
    /** Long enough for PowerShell to start and compile the inline C#, which is the slow part. */
    private static final long TIMEOUT_SECONDS = 30;

    private static Path windowsScript;

    private GameWindow() {
    }

    /**
     * Raises the given process's window and returns its screen bounds.
     *
     * @return the window's bounds, or empty if this platform has no supported way to look them up or the
     *     process has no visible top-level window (yet)
     */
    static Optional<Rectangle> activate(long pid) throws InterruptedException {
        try {
            return switch (ClientRun.osName()) {
                case "windows" -> activateWindows(pid);
                case "linux" -> activateLinux(pid);
                // macOS needs an accessibility-permission grant that cannot be scripted, so a scenario
                // there falls back to ClientInput's screen-centre click.
                default -> Optional.empty();
            };
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Enumerates top-level windows, keeps the first visible one owned by {@code pid}, raises it, and
     * reads its rectangle.
     *
     * <p>{@code Get-Process().MainWindowHandle} would be shorter but reports 0 for a GLFW window often
     * enough to be unusable, so the enumeration is done directly.
     */
    private static Optional<Rectangle> activateWindows(long pid) throws IOException, InterruptedException {
        Path script = windowsScript();
        List<String> out = run(List.of("powershell.exe", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-File", script.toAbsolutePath().toString(),
            Long.toString(pid)));
        return parseRect(out);
    }

    /** {@code xdotool} is the only thing that can do this under Xvfb; treat its absence as unsupported. */
    private static Optional<Rectangle> activateLinux(long pid) throws IOException, InterruptedException {
        List<String> ids = run(List.of("xdotool", "search", "--onlyvisible", "--pid",
            Long.toString(pid)));
        String id = ids.stream().map(String::trim).filter(s -> !s.isEmpty())
            .reduce((first, second) -> second).orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        run(List.of("xdotool", "windowactivate", "--sync", id));
        // "Geometry: 854x480" and "Position: 100,200 (screen: 0)" over several lines.
        List<String> geometry = run(List.of("xdotool", "getwindowgeometry", "--shell", id));
        int x = 0;
        int y = 0;
        int w = 0;
        int h = 0;
        for (String line : geometry) {
            String[] kv = line.trim().split("=", 2);
            if (kv.length != 2) continue;
            try {
                int value = Integer.parseInt(kv[1].trim());
                switch (kv[0].trim()) {
                    case "X" -> x = value;
                    case "Y" -> y = value;
                    case "WIDTH" -> w = value;
                    case "HEIGHT" -> h = value;
                    default -> {
                    }
                }
            } catch (NumberFormatException e) {
                // Not a numeric field; SCREEN and WINDOW are also printed.
            }
        }
        return w > 0 && h > 0 ? Optional.of(new Rectangle(x, y, w, h)) : Optional.empty();
    }

    private static Optional<Rectangle> parseRect(List<String> output) {
        for (String line : output) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 4) continue;
            try {
                int left = Integer.parseInt(parts[0]);
                int top = Integer.parseInt(parts[1]);
                int right = Integer.parseInt(parts[2]);
                int bottom = Integer.parseInt(parts[3]);
                if (right > left && bottom > top) {
                    return Optional.of(new Rectangle(left, top, right - left, bottom - top));
                }
            } catch (NumberFormatException e) {
                // Not the rectangle line; PowerShell may also print warnings.
            }
        }
        return Optional.empty();
    }

    private static List<String> run(List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return List.of();
        }
        return List.of(output.split("\\R"));
    }

    /**
     * The activation script, written to a temp file rather than passed with {@code -Command} because it
     * contains an inline C# type definition that no amount of shell quoting survives intact.
     */
    private static synchronized Path windowsScript() throws IOException {
        if (windowsScript != null && Files.exists(windowsScript)) {
            return windowsScript;
        }
        Path script = Files.createTempFile("qbm-activate-window", ".ps1");
        script.toFile().deleteOnExit();
        Files.writeString(script, """
            param([int]$TargetPid)
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition @'
            using System;
            using System.Runtime.InteropServices;
            public class QbmWin {
              private delegate bool EnumProc(IntPtr h, IntPtr p);
              [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc cb, IntPtr p);
              [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
              [DllImport("user32.dll")] private static extern bool IsWindowVisible(IntPtr h);
              [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
              [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
              [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
              [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
              [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
              public static IntPtr Find(uint target) {
                IntPtr found = IntPtr.Zero;
                EnumWindows(delegate(IntPtr h, IntPtr p) {
                  uint wpid;
                  GetWindowThreadProcessId(h, out wpid);
                  if (wpid == target && IsWindowVisible(h)) { found = h; return false; }
                  return true;
                }, IntPtr.Zero);
                return found;
              }
            }
            '@
            $handle = [QbmWin]::Find([uint32]$TargetPid)
            if ($handle -eq [IntPtr]::Zero) { exit 2 }
            # SW_RESTORE: a minimised window cannot take focus and reports a meaningless rectangle.
            [QbmWin]::ShowWindow($handle, 9) | Out-Null
            [QbmWin]::BringWindowToTop($handle) | Out-Null
            [QbmWin]::SetForegroundWindow($handle) | Out-Null
            $rect = New-Object 'QbmWin+RECT'
            if (-not [QbmWin]::GetWindowRect($handle, [ref]$rect)) { exit 3 }
            Write-Output ("{0} {1} {2} {3}" -f $rect.Left, $rect.Top, $rect.Right, $rect.Bottom)
            """);
        windowsScript = script;
        return script;
    }
}
