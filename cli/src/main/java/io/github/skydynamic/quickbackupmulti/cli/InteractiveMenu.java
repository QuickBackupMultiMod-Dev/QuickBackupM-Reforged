package io.github.skydynamic.quickbackupmulti.cli;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A single-select, paginated terminal menu navigable with the arrow keys or WASD.
 *
 * <p>Controls: <b>W</b>/<b>↑</b> move up, <b>S</b>/<b>↓</b> move down, <b>A</b>/<b>←</b> previous page,
 * <b>D</b>/<b>→</b> next page, <b>Enter</b> confirm, <b>Esc</b> cancel/back, <b>Ctrl+C</b> quit immediately.
 *
 * <p>Uses JLine's raw terminal mode so key presses are read without waiting for Enter, which works across
 * cmd.exe, PowerShell and Windows Terminal. When no interactive terminal is available (e.g. output is piped),
 * it falls back to a plain numbered prompt read from stdin.
 *
 * @param <T> the type of the selectable items
 */
public final class InteractiveMenu<T> {
    /**
     * Shared stdin reader for the numbered fallback. A {@link BufferedReader} reads ahead, so a fresh one per
     * prompt would swallow input intended for later prompts; keeping one instance preserves it across calls.
     */
    private static BufferedReader sharedIn;

    private static BufferedReader stdin() {
        if (sharedIn == null) {
            sharedIn = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        return sharedIn;
    }

    private final String title;
    private final List<T> items;
    private final java.util.function.Function<T, String> labeler;

    public InteractiveMenu(String title, List<T> items, java.util.function.Function<T, String> labeler) {
        this.title = title;
        this.items = items;
        this.labeler = labeler;
    }

    /**
     * Prompt the user to pick one item.
     *
     * @return the chosen item, or {@code null} if the user cancelled or the list was empty
     */
    public T prompt() {
        if (items.isEmpty()) {
            return null;
        }
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            if (terminal.getType() == null || Terminal.TYPE_DUMB.equals(terminal.getType())) {
                terminal.close();
                return promptNumbered();
            }
            try {
                return promptInteractive(terminal);
            } finally {
                terminal.close();
            }
        } catch (IOException e) {
            return promptNumbered();
        }
    }

    private T promptInteractive(Terminal terminal) throws IOException {
        terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selected = 0;
        int pageSize = computePageSize(terminal);

        try {
            while (true) {
                int page = selected / pageSize;
                render(terminal, selected, page, pageSize);

                int c = reader.read();
                if (c == -1) {
                    return null;
                }

                // Ctrl+C: raw mode delivers it as ETX (0x03) rather than a signal. Exit the whole process.
                if (c == 3) {
                    clear(terminal);
                    terminal.close();
                    System.exit(130);
                }

                if (c == 27) {
                    int next = reader.peek(50);
                    if (next == '[' || next == 'O') {
                        reader.read();
                        int code = reader.read();
                        switch (code) {
                            case 'A' -> selected = Math.max(0, selected - 1);
                            case 'B' -> selected = Math.min(items.size() - 1, selected + 1);
                            case 'D' -> selected = prevPage(selected, pageSize);
                            case 'C' -> selected = nextPage(selected, pageSize);
                            default -> { }
                        }
                        continue;
                    }
                    // Bare Esc: step back / cancel.
                    clear(terminal);
                    return null;
                }

                switch (Character.toLowerCase(c)) {
                    case 'w' -> selected = Math.max(0, selected - 1);
                    case 's' -> selected = Math.min(items.size() - 1, selected + 1);
                    case 'a' -> selected = prevPage(selected, pageSize);
                    case 'd' -> selected = nextPage(selected, pageSize);
                    case '\r', '\n' -> {
                        clear(terminal);
                        return items.get(selected);
                    }
                    default -> { }
                }
            }
        } finally {
            terminal.writer().flush();
        }
    }

    private int prevPage(int selected, int pageSize) {
        return Math.max(0, selected - pageSize);
    }

    private int nextPage(int selected, int pageSize) {
        return Math.min(items.size() - 1, selected + pageSize);
    }

    private int computePageSize(Terminal terminal) {
        int height = terminal.getHeight();
        int usable = height > 0 ? height - 4 : 15;
        return Math.max(1, Math.min(usable, 20));
    }

    private void render(Terminal terminal, int selected, int page, int pageSize) {
        terminal.writer().print("[H[2J");
        terminal.writer().flush();

        int totalPages = (items.size() + pageSize - 1) / pageSize;
        int start = page * pageSize;
        int end = Math.min(items.size(), start + pageSize);

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\r\n");
        sb.append("  (W/S or ↑↓ move, A/D or ←→ page, Enter select, Esc back, Ctrl+C quit)\r\n\r\n");

        for (int i = start; i < end; i++) {
            if (i == selected) {
                sb.append("  > ").append(labeler.apply(items.get(i))).append("\r\n");
            } else {
                sb.append("    ").append(labeler.apply(items.get(i))).append("\r\n");
            }
        }

        if (totalPages > 1) {
            sb.append("\r\n  page ").append(page + 1).append('/').append(totalPages)
                .append("  (item ").append(selected + 1).append('/').append(items.size()).append("\r\n");
        }

        terminal.writer().print(sb);
        terminal.writer().flush();
    }

    private void clear(Terminal terminal) {
        terminal.writer().print("[H[2J");
        terminal.writer().flush();
    }

    private T promptNumbered() {
        BufferedReader in = stdin();
        System.out.println(title);
        for (int i = 0; i < items.size(); i++) {
            System.out.printf("  [%d] %s%n", i + 1, labeler.apply(items.get(i)));
        }
        System.out.print("Enter a number (blank to cancel): ");
        System.out.flush();
        try {
            String line = in.readLine();
            if (line == null || line.isBlank()) {
                return null;
            }
            int choice = Integer.parseInt(line.trim());
            if (choice < 1 || choice > items.size()) {
                System.err.println("Selection out of range.");
                return null;
            }
            return items.get(choice - 1);
        } catch (IOException | NumberFormatException e) {
            System.err.println("Invalid selection.");
            return null;
        }
    }
}
