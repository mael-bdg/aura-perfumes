package view;

import java.io.PrintStream;
import java.util.Objects;
import presenter.OrderViewBoundary;

/** Console view displaying messages already formatted by the order presenter. */
public final class ConsoleOrderView implements OrderViewBoundary {
    private final PrintStream output;

    /** Creates a view using the current standard output. */
    public ConsoleOrderView() {
        this(System.out);
    }

    /** Creates a view with a caller-owned output stream. */
    public ConsoleOrderView(PrintStream output) {
        this.output = Objects.requireNonNull(output, "Console output is required");
    }

    /** Prints a formatted message and leaves the supplied stream open. */
    @Override
    public void display(String message) {
        output.println(message);
        output.flush();
    }
}
