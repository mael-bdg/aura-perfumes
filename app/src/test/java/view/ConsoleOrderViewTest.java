package view;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import presenter.OrderViewBoundary;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleOrderViewTest {
    @Test
    void displaysExactlyTheSuppliedFormattedTextAndLeavesStreamOpen() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        OrderViewBoundary view = new ConsoleOrderView(output);
        String message = "SUCCESS" + System.lineSeparator() + "Total = 10.30";
        view.display(message);
        assertEquals(message + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
        output.println("Output remains open");
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Output remains open"));
    }

    @Test
    void defaultConstructorUsesCurrentSystemOutput() {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            new ConsoleOrderView().display("Preview calculated");
        } finally {
            System.setOut(original);
        }
        assertEquals("Preview calculated" + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsMissingOutputStream() {
        assertThrows(NullPointerException.class, () -> new ConsoleOrderView(null));
    }
}
