package controller;

import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import presenter.StockPresenter;
import view.ConsoleStockView;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import usecases.StockRequestHandler;
import usecases.StockRepository;
import usecases.SynchronizeStockInteractor;
import static org.junit.jupiter.api.Assertions.*;

class StockControllerTest {
    private final UUID productId = UUID.randomUUID();
    private final List<StockRequest> requests = new ArrayList<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    private final StockRequestHandler handler = request -> {
        requests.add(request);
        return new StockResponse(true, request.getProductId(), "Aura", 10, "Operation completed");
    };

    @Test
    void checkConvertsUuidAndSubmitsCorrectRequest() {
        controller("").checkStock(productId.toString());
        assertRequest(0, StockOperation.CHECK, 0);
        assertEquals(1, requests.size());
    }

    @Test
    void increaseSubmitsCorrectRequest() {
        controller("").increaseStock(productId.toString(), 5);
        assertRequest(0, StockOperation.INCREASE, 5);
        assertEquals(1, requests.size());
    }

    @Test
    void decreaseSubmitsCorrectRequest() {
        controller("").decreaseStock(productId.toString(), 3);
        assertRequest(0, StockOperation.DECREASE, 3);
        assertEquals(1, requests.size());
    }

    @Test
    void acceptsWhitespaceAndUppercaseUuid() {
        controller("").checkStock("  " + productId.toString().toUpperCase(java.util.Locale.ROOT) + "  ");
        assertRequest(0, StockOperation.CHECK, 0);
    }

    @Test
    void rejectsInvalidUuidsWithoutCallingHandler() {
        StockController controller = controller("");
        for (String id : new String[] {null, "", " ", "invalid", "1-1-1-1-1"}) {
            controller.checkStock(id);
            controller.increaseStock(id, 1);
            controller.decreaseStock(id, 1);
        }
        assertTrue(requests.isEmpty());
        assertTrue(text().contains("Invalid product UUID"));
    }

    @Test
    void displaysSuccessfulHandlerResponse() {
        controller("").checkStock(productId.toString());
        assertTrue(text().contains("SUCCESS"));
        assertTrue(text().contains("Product name = Aura"));
        assertTrue(text().contains("Product UUID = " + productId));
        assertTrue(text().contains("Available quantity = 10"));
        assertTrue(text().contains("Message = Operation completed"));
    }

    @Test
    void displaysFailureWithKnownUnchangedQuantity() {
        StockRequestHandler failing = request -> new StockResponse(false, productId, "Aura", 10, "Insufficient stock");
        new StockController(failing, presenter(), new Scanner(""), output).decreaseStock(productId.toString(), 11);
        assertTrue(text().contains("FAILURE"));
        assertTrue(text().contains("Product name = Aura"));
        assertTrue(text().contains("Product UUID = " + productId));
        assertTrue(text().contains("Available quantity = 10"));
        assertTrue(text().contains("Message = Insufficient stock"));
    }

    @Test
    void displaysUnavailableContextWithoutNegativeStockQuantity() {
        StockRequestHandler failing = request -> new StockResponse(false, productId, null, -1, "Unknown product");
        new StockController(failing, presenter(), new Scanner(""), output).checkStock(productId.toString());
        assertTrue(text().contains("FAILURE"));
        assertTrue(text().contains("Product name = Unavailable"));
        assertTrue(text().contains("Available quantity = Unavailable"));
        assertTrue(text().contains("Message = Unknown product"));
        assertFalse(text().contains("Available quantity = -1"));
    }

    @Test
    void invalidMenuAndInputFormatsAllowLaterValidCommand() {
        String input = String.join("\n", "hello", "9", "", "1", "invalid",
            "2", productId.toString(), "abc", "3", productId.toString(), "2147483648",
            "1", productId.toString(), "4") + "\n";
        controller(input).run();
        assertEquals(1, requests.size());
        assertRequest(0, StockOperation.CHECK, 0);
        assertTrue(text().contains("Invalid menu choice"));
        assertTrue(text().contains("Invalid product UUID"));
        assertTrue(text().contains("Invalid quantity format"));
        assertTrue(text().contains("SUCCESS"));
        assertTrue(text().contains("Goodbye."));
    }

    @Test
    void interactiveLoopSubmitsEveryOperation() {
        controller(String.join("\n", "1", productId.toString(), "2", productId.toString(), "5",
            "3", productId.toString(), "3", "4") + "\n").run();
        assertEquals(3, requests.size());
        assertRequest(0, StockOperation.CHECK, 0);
        assertRequest(1, StockOperation.INCREASE, 5);
        assertRequest(2, StockOperation.DECREASE, 3);
    }

    @Test
    void exitStopsLoopAndLeavesInputOpen() {
        Scanner input = new Scanner("4\nremaining\n");
        new StockController(handler, presenter(), input, output).run();
        assertTrue(requests.isEmpty());
        assertEquals("remaining", input.nextLine());
        assertTrue(text().contains("Goodbye."));
    }

    @Test
    void endOfInputAtEveryPromptTerminatesCleanly() {
        for (String input : new String[] {"", "1\n", "2\n" + productId + "\n"}) {
            assertDoesNotThrow(() -> controller(input).run());
        }
        assertTrue(requests.isEmpty());
    }

    @Test
    void businessValidationRemainsInCoreAndLoopContinuesAfterRejection() {
        DatabaseProductRepository products = new DatabaseProductRepository();
        DatabaseStockRepository stocks = new DatabaseStockRepository(products);
        products.addProduct(new Product(productId, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null));
        stocks.addStock(new Stock(UUID.randomUUID(), productId, 10, 0));
        String input = String.join("\n", "2", productId.toString(), "5", "3", productId.toString(), "3",
            "2", productId.toString(), "0", "3", productId.toString(), "-1",
            "3", productId.toString(), "100", "1", productId.toString(), "4") + "\n";
        new StockController(new SynchronizeStockInteractor(products, stocks), presenter(), new Scanner(input), output).run();
        assertEquals(12, stocks.findByProductId(productId).getAvailableQuantity());
        assertEquals(2, stocks.findByProductId(productId).getVersion());
        assertTrue(text().contains("Available quantity = 15"));
        assertTrue(text().contains("Available quantity = 12"));
        assertTrue(text().contains("FAILURE"));
        assertTrue(text().contains("Goodbye."));
    }

    @Test
    void consoleReportsVersionConflictAndPreservesNewerStock() {
        DatabaseProductRepository products = new DatabaseProductRepository();
        DatabaseStockRepository stocks = new DatabaseStockRepository(products);
        products.addProduct(new Product(productId, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null));
        stocks.addStock(new Stock(UUID.randomUUID(), productId, 10, 0));
        StockRepository conflicting = new StockRepository() {
            @Override
            public Stock findByProductId(UUID id) {
                return stocks.findByProductId(id);
            }

            @Override
            public boolean saveIfVersionMatches(Stock stale) {
                Stock newer = stocks.findByProductId(productId);
                newer.increase(5);
                assertTrue(stocks.saveIfVersionMatches(newer));
                return stocks.saveIfVersionMatches(stale);
            }
        };
        String input = String.join("\n", "3", productId.toString(), "2", "1", productId.toString(), "4") + "\n";
        new StockController(new SynchronizeStockInteractor(products, conflicting), presenter(), new Scanner(input), output).run();
        assertEquals(15, stocks.findByProductId(productId).getAvailableQuantity());
        assertEquals(1, stocks.findByProductId(productId).getVersion());
        assertTrue(text().contains("FAILURE"));
        assertTrue(text().contains("conflicts"));
        assertTrue(text().contains("Available quantity = Unavailable"));
        assertTrue(text().contains("Available quantity = 15"));
        assertTrue(text().contains("Goodbye."));
    }

    @Test
    void doesNotSwallowUnexpectedHandlerFailureAsInputError() {
        IllegalArgumentException failure = new IllegalArgumentException("Unexpected handler failure");
        StockRequestHandler broken = request -> { throw failure; };
        StockController controller = new StockController(broken, presenter(), new Scanner(""), output);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
            () -> controller.checkStock(productId.toString())));
    }

    @Test
    void forwardsExactHandlerResponsesToIndependentOutputBoundaryWithoutPrintingResults() {
        for (boolean success : new boolean[] {true, false}) {
            StockResponse response = new StockResponse(success, productId, "Aura", 10, "Outcome");
            List<StockResponse> presented = new ArrayList<>();
            StockController controller = new StockController(request -> response, presented::add,
                new Scanner(""), output);
            controller.checkStock(productId.toString());
            controller.increaseStock(productId.toString(), 1);
            controller.decreaseStock(productId.toString(), 1);
            assertEquals(3, presented.size());
            presented.forEach(actual -> assertSame(response, actual));
        }
        assertEquals("", text());
    }

    @Test
    void forwardsInvalidUuidFailureWithoutCallingHandlerOrPrintingResults() {
        List<StockResponse> presented = new ArrayList<>();
        StockController controller = new StockController(handler, presented::add, new Scanner(""), output);
        controller.checkStock("invalid");
        controller.increaseStock(null, 1);
        controller.decreaseStock("1-1-1-1-1", 1);
        assertTrue(requests.isEmpty());
        assertEquals(3, presented.size());
        for (StockResponse response : presented) {
            assertFalse(response.isSuccess());
            assertNull(response.getProductId());
            assertNull(response.getProductName());
            assertEquals(-1, response.getAvailableQuantity());
            assertTrue(response.getMessage().contains("Invalid product UUID"));
        }
        assertEquals("", text());
    }

    private StockOutputBoundary presenter() {
        return new StockPresenter(new ConsoleStockView(output));
    }

    private StockController controller(String input) {
        return new StockController(handler, presenter(), new Scanner(input), output);
    }

    private void assertRequest(int index, StockOperation operation, int quantity) {
        StockRequest request = requests.get(index);
        assertEquals(productId, request.getProductId());
        assertEquals(operation, request.getOperation());
        assertEquals(quantity, request.getQuantity());
    }

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
