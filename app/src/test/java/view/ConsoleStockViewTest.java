package view;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import presenter.StockViewBoundary;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleStockViewTest {
    @Test
    void displaysFormattedMessageVerbatimToInjectedStream() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        StockViewBoundary view = new ConsoleStockView(output);
        String message = "SUCCESS" + System.lineSeparator() + "Product name = Aura";
        view.display(message);
        assertEquals(message + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
        output.println("Stream remains open");
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Stream remains open"));
    }

    @Test
    void defaultsToCurrentSystemOutput() {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            new ConsoleStockView().display("Stock checked");
        } finally {
            System.setOut(original);
        }
        assertEquals("Stock checked" + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNullOutputStream() {
        assertThrows(NullPointerException.class, () -> new ConsoleStockView(null));
    }
}
