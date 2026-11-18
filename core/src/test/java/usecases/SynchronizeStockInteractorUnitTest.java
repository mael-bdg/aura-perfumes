package usecases;

import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated gateway interaction tests complementing the services integration suite. */
class SynchronizeStockInteractorUnitTest {
    private static final UUID PRODUCT_ID = new UUID(0, 1);
    private static final UUID STOCK_ID = new UUID(0, 2);
    private final FakeProductRepository products = new FakeProductRepository();
    private final FakeStockRepository stocks = new FakeStockRepository();
    private final SynchronizeStockInteractor interactor = new SynchronizeStockInteractor(products, stocks);

    @Test
    void checkNeverCallsSaveAndPreservesSnapshotVersion() {
        StockResponse response = interactor.handle(request(StockOperation.CHECK, -1));
        assertTrue(response.isSuccess());
        assertEquals(PRODUCT_ID, response.getProductId());
        assertEquals("Aura", response.getProductName());
        assertEquals(10, response.getAvailableQuantity());
        assertEquals(10, interactor.checkStock(PRODUCT_ID));
        assertEquals(PRODUCT_ID, products.lastLookup);
        assertEquals(PRODUCT_ID, stocks.lastLookup);
        assertTrue(stocks.saveAttempts.isEmpty());
        assertOriginalUnchanged();
    }

    @Test
    void validAdjustmentsSaveExactlyOneSnapshotWithOriginalIdentityAndVersion() {
        for (StockOperation operation : new StockOperation[] {StockOperation.INCREASE, StockOperation.DECREASE}) {
            stocks.saveAttempts.clear();
            StockResponse response = interactor.handle(request(operation, 3));
            int expectedQuantity = operation == StockOperation.INCREASE ? 13 : 7;
            assertTrue(response.isSuccess());
            assertEquals(PRODUCT_ID, response.getProductId());
            assertEquals("Aura", response.getProductName());
            assertEquals(expectedQuantity, response.getAvailableQuantity());
            assertEquals(1, stocks.saveAttempts.size());
            Stock saved = stocks.saveAttempts.getFirst();
            assertEquals(STOCK_ID, saved.getId());
            assertEquals(PRODUCT_ID, saved.getProductId());
            assertEquals(expectedQuantity, saved.getAvailableQuantity());
            assertEquals(4, saved.getVersion());
            assertOriginalUnchanged();
        }
    }

    @Test
    void invalidAdjustmentsNeverReachSave() {
        for (StockOperation operation : new StockOperation[] {StockOperation.INCREASE, StockOperation.DECREASE}) {
            for (int quantity : new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                StockResponse response = interactor.handle(request(operation, quantity));
                assertFalse(response.isSuccess());
                assertEquals(10, response.getAvailableQuantity());
                assertNotNull(response.getMessage());
                assertFalse(response.getMessage().isBlank());
                assertTrue(stocks.saveAttempts.isEmpty());
                assertOriginalUnchanged();
            }
        }
    }

    @Test
    void unknownProductShortCircuitsStockLookupAndSave() {
        products.product = null;
        for (StockOperation operation : StockOperation.values()) {
            StockResponse response = interactor.handle(request(operation, 1));
            assertFalse(response.isSuccess());
            assertEquals(PRODUCT_ID, response.getProductId());
            assertNull(response.getProductName());
            assertEquals(-1, response.getAvailableQuantity());
            assertTrue(response.getMessage().contains("Unknown product"));
        }
        assertEquals(0, stocks.lookupCount);
        assertTrue(stocks.saveAttempts.isEmpty());
        assertOriginalUnchanged();
    }

    @Test
    void missingStockNeverReachesSaveAndPreservesKnownProductContext() {
        stocks.missing = true;
        for (StockOperation operation : StockOperation.values()) {
            StockResponse response = interactor.handle(request(operation, 1));
            assertFalse(response.isSuccess());
            assertEquals(PRODUCT_ID, response.getProductId());
            assertEquals("Aura", response.getProductName());
            assertEquals(-1, response.getAvailableQuantity());
            assertTrue(response.getMessage().contains("Missing stock"));
        }
        assertTrue(stocks.saveAttempts.isEmpty());
        assertOriginalUnchanged();
    }

    @Test
    void nullRequestDoesNotCallEitherRepository() {
        StockResponse response = interactor.handle(null);
        assertFalse(response.isSuccess());
        assertNull(response.getProductId());
        assertNull(response.getProductName());
        assertEquals(-1, response.getAvailableQuantity());
        assertTrue(response.getMessage().contains("request is required"));
        assertEquals(0, products.lookupCount);
        assertEquals(0, stocks.lookupCount);
        assertTrue(stocks.saveAttempts.isEmpty());
    }

    @Test
    void failedVersionCheckIsReportedWithoutRetryOrAdditionalLookup() {
        stocks.acceptSave = false;
        StockResponse response = interactor.handle(request(StockOperation.INCREASE, 5));
        assertFalse(response.isSuccess());
        assertEquals(PRODUCT_ID, response.getProductId());
        assertEquals("Aura", response.getProductName());
        assertEquals(-1, response.getAvailableQuantity());
        assertTrue(response.getMessage().contains("conflicts"));
        assertEquals(1, products.lookupCount);
        assertEquals(1, stocks.lookupCount);
        assertEquals(1, stocks.saveAttempts.size());
        assertEquals(15, stocks.saveAttempts.getFirst().getAvailableQuantity());
        assertEquals(4, stocks.saveAttempts.getFirst().getVersion());
        assertOriginalUnchanged();
    }

    @Test
    void unexpectedSaveFailurePropagatesWithoutRetry() {
        IllegalStateException failure = new IllegalStateException("Storage unavailable");
        stocks.saveFailure = failure;
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> interactor.handle(request(StockOperation.DECREASE, 1))));
        assertEquals(1, stocks.saveAttempts.size());
        assertEquals(9, stocks.saveAttempts.getFirst().getAvailableQuantity());
        assertEquals(1, stocks.lookupCount);
        assertOriginalUnchanged();
    }

    private static StockRequest request(StockOperation operation, int quantity) {
        return new StockRequest(PRODUCT_ID, operation, quantity);
    }

    private void assertOriginalUnchanged() {
        assertEquals(10, stocks.original.getAvailableQuantity());
        assertEquals(4, stocks.original.getVersion());
    }

    private static final class FakeProductRepository implements ProductRepository {
        private Product product = new Product(PRODUCT_ID, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null);
        private int lookupCount;
        private UUID lastLookup;

        @Override
        public Product findById(UUID productId) {
            lookupCount++;
            lastLookup = productId;
            return product;
        }
    }

    /** Records save attempts without implementing persistence or version advancement. */
    private static final class FakeStockRepository implements StockRepository {
        private final Stock original = new Stock(STOCK_ID, PRODUCT_ID, 10, 4);
        private final List<Stock> saveAttempts = new ArrayList<>();
        private int lookupCount;
        private UUID lastLookup;
        private boolean missing;
        private boolean acceptSave = true;
        private RuntimeException saveFailure;

        @Override
        public Stock findByProductId(UUID productId) {
            lookupCount++;
            lastLookup = productId;
            return missing ? null : copy(original);
        }

        @Override
        public boolean saveIfVersionMatches(Stock stock) {
            saveAttempts.add(copy(stock));
            if (saveFailure != null) {
                throw saveFailure;
            }
            return acceptSave;
        }

        private static Stock copy(Stock stock) {
            return new Stock(stock.getId(), stock.getProductId(), stock.getAvailableQuantity(), stock.getVersion());
        }
    }
}
