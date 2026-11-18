import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuraPerfumesApplicationTest {
    private final UUID productId = UUID.randomUUID();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    @Test
    void mainMenuLaunchesU1AndReturnsBeforeApplicationExit() {
        run("1", "1", productId.toString(), "4", "0");
        String text = text();
        int mainMenu = text.indexOf("=== Aura Perfumes ===");
        int submenu = text.indexOf("1. CHECK stock", mainMenu);
        int check = text.indexOf("Available quantity = 10", submenu);
        int submenuExit = text.indexOf("Goodbye.", check);
        int returnedMenu = text.indexOf("=== Aura Perfumes ===", submenuExit);
        int applicationExit = text.indexOf("Application closed.", returnedMenu);
        assertTrue(mainMenu >= 0);
        assertTrue(submenu > mainMenu);
        assertTrue(check > submenu);
        assertTrue(submenuExit > check);
        assertTrue(returnedMenu > submenuExit);
        assertTrue(applicationExit > returnedMenu);
    }

    @Test
    void exitLeavesInputAndOutputOpenWithoutOpeningU1() {
        Scanner input = new Scanner("0\nremaining\n");
        new AuraPerfumesApplication(input, output, productId).run();
        assertTrue(text().contains("0. Exit"));
        assertTrue(text().contains("Application closed."));
        assertFalse(text().contains("1. CHECK stock"));
        assertEquals("remaining", input.nextLine());
        output.println("Output remains open");
        assertTrue(text().contains("Output remains open"));
    }

    @Test
    void invalidMainMenuInputAllowsLaterSubmenuNavigation() {
        run("bad", "", "99", "2147483648", " 1 ", "4", " 0 ");
        assertEquals(4, text().lines().filter(line -> line.startsWith("Invalid main-menu choice")).count());
        assertTrue(text().contains("1. CHECK stock"));
        assertTrue(text().endsWith("Application closed." + System.lineSeparator()));
    }

    @Test
    void sampleScenarioPersistsAcrossSubmenuVisitsAndRejectedAdjustment() {
        run("1", "1", productId.toString(), "2", productId.toString(), "5",
            "3", productId.toString(), "3", "3", productId.toString(), "100", "4",
            "1", "1", productId.toString(), "4", "0");
        String text = text();
        assertTrue(text.contains("Available quantity = 10"));
        assertTrue(text.contains("Available quantity = 15"));
        assertEquals(3, text.lines().filter(line -> line.equals("Available quantity = 12")).count());
        assertTrue(text.contains("FAILURE"));
        assertTrue(text.contains("Decrease must be positive and cannot exceed stock"));
        int returnToMain = text.indexOf("=== Aura Perfumes ===", text.indexOf("Goodbye."));
        assertTrue(returnToMain >= 0);
        assertTrue(text.substring(returnToMain).contains("Available quantity = 12"));
        assertFalse(text.substring(returnToMain).contains("Available quantity = 10"));
    }

    @Test
    void displaysSampleProductUuidAndUsesItForStockLookup() {
        run("1", "1", productId.toString(), "4", "0");
        assertTrue(text().contains("Sample product UUID = " + productId));
        assertTrue(text().contains("Product UUID = " + productId));
        assertTrue(text().contains("Product name = Aura"));
        assertTrue(text().contains("SUCCESS"));
    }

    @Test
    void generatedSampleUuidIsValidAndDisplayed() {
        new AuraPerfumesApplication(new Scanner("0\n"), output).run();
        String firstLine = text().lines().findFirst().orElseThrow();
        assertTrue(firstLine.startsWith("Sample product UUID = "));
        assertDoesNotThrow(() -> UUID.fromString(firstLine.substring("Sample product UUID = ".length())));
    }

    @Test
    void endOfInputAtMainMenuOrInsideU1ClosesApplicationCleanly() {
        for (String input : new String[] {"", "1\n", "1\n2\n" + productId + "\n"}) {
            bytes.reset();
            assertDoesNotThrow(() -> new AuraPerfumesApplication(new Scanner(input), output, productId).run());
            assertTrue(text().endsWith("Application closed." + System.lineSeparator()));
        }
    }

    @Test
    void invalidUuidInsideU1AllowsReturnToMainAndReentry() {
        run("1", "1", "bad-uuid", "4", "1", "1", productId.toString(), "4", "0");
        assertTrue(text().contains("Invalid product UUID"));
        assertTrue(text().contains("Available quantity = Unavailable"));
        assertTrue(text().contains("Available quantity = 10"));
        assertFalse(text().contains("Available quantity = -1"));
        assertTrue(text().endsWith("Application closed." + System.lineSeparator()));
    }

    private void run(String... commands) {
        Scanner input = new Scanner(String.join("\n", commands) + "\n");
        new AuraPerfumesApplication(input, output, productId).run();
    }

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
