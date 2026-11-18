package view;

import java.io.PrintStream;
import java.util.Objects;
import presenter.StockViewBoundary;

/** Console view displaying messages formatted by the stock presenter. */
public final class ConsoleStockView implements StockViewBoundary {
    private final PrintStream output;

    /** Creates a view using the current standard output. */
    public ConsoleStockView() {
        this(System.out);
    }

    /** Creates a view with a caller-owned output stream. */
    public ConsoleStockView(PrintStream output) {
        this.output = Objects.requireNonNull(output, "Console output is required");
    }

    /** Prints one formatted message without closing the output stream. */
    @Override
    public void display(String message) {
        output.println(message);
        output.flush();
    }
}
