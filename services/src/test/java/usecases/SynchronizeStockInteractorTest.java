package usecases;

import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SynchronizeStockInteractorTest {
    private UUID productId;
    private UUID stockId;
    private DatabaseProductRepository products;
    private DatabaseStockRepository stocks;
    private SynchronizeStockInteractor interactor;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        stockId = UUID.randomUUID();
        products = new DatabaseProductRepository();
        stocks = new DatabaseStockRepository(products);
        products.addProduct(product(productId));
        stocks.addStock(new Stock(stockId, productId, 10, 4));
        interactor = new SynchronizeStockInteractor(products, stocks);
    }

    @Test
    void checkReturnsQuantityWithoutChangingStoredState() {
        assertEquals(10, interactor.checkStock(productId));
        assertEquals(10, interactor.checkStock(productId));
        assertStoredState(10, 4);
        assertEquals(stockId, stocks.findByProductId(productId).getId());
        assertEquals(productId, products.findById(productId).getProductId());
    }

    @Test
    void increasePersistsQuantityAndAdvancesStoredVersion() {
        assertEquals(15, interactor.increaseStock(productId, 5));
        assertStoredState(15, 5);
        assertEquals(17, interactor.increaseStock(productId, 2));
        assertStoredState(17, 6);
    }

    @Test
    void decreasePersistsQuantityIncludingZero() {
        assertEquals(7, interactor.decreaseStock(productId, 3));
        assertStoredState(7, 5);
        assertEquals(0, interactor.decreaseStock(productId, 7));
        assertStoredState(0, 6);
    }

    @Test
    void rejectsNonPositiveAdjustmentsWithoutChangingStoredState() {
        for (int quantity : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertRejected(() -> interactor.increaseStock(productId, quantity));
            assertStoredState(10, 4);
            assertRejected(() -> interactor.decreaseStock(productId, quantity));
            assertStoredState(10, 4);
        }
    }

    @Test
    void rejectsInsufficientStockWithoutChangingStoredState() {
        assertRejected(() -> interactor.decreaseStock(productId, 11));
        assertStoredState(10, 4);
    }

    @Test
    void rejectsUnknownProductsForEveryOperation() {
        UUID unknown = UUID.randomUUID();
        assertRejected(() -> interactor.checkStock(unknown));
        assertRejected(() -> interactor.increaseStock(unknown, 1));
        assertRejected(() -> interactor.decreaseStock(unknown, 1));
        assertStoredState(10, 4);
    }

    @Test
    void rejectsNullProductIdentifiersForEveryOperation() {
        assertRejected(() -> interactor.checkStock(null));
        assertRejected(() -> interactor.increaseStock(null, 1));
        assertRejected(() -> interactor.decreaseStock(null, 1));
        assertStoredState(10, 4);
    }

    @Test
    void reportsMissingStockForEveryOperation() {
        UUID missingStock = UUID.randomUUID();
        products.addProduct(product(missingStock));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> interactor.checkStock(missingStock));
        assertTrue(error.getMessage().contains("Missing stock"));
        assertRejected(() -> interactor.increaseStock(missingStock, 1));
        assertRejected(() -> interactor.decreaseStock(missingStock, 1));
        assertStoredState(10, 4);
    }

    @Test
    void preventsOverflowAndPermitsExactIntegerLimit() {
        assertRejected(() -> interactor.increaseStock(productId, Integer.MAX_VALUE));
        assertStoredState(10, 4);
        assertEquals(Integer.MAX_VALUE,
            interactor.increaseStock(productId, Integer.MAX_VALUE - 10));
        assertRejected(() -> interactor.increaseStock(productId, 1));
        assertStoredState(Integer.MAX_VALUE, 5);
    }

    @Test
    void registrationAndRetrievalProtectStoredStockFromExternalMutation() {
        UUID otherId = UUID.randomUUID();
        products.addProduct(product(otherId));
        Stock supplied = new Stock(UUID.randomUUID(), otherId, 3, 0);
        stocks.addStock(supplied);
        supplied.increase(100);
        Stock retrieved = stocks.findByProductId(otherId);
        retrieved.decrease(3);
        assertEquals(3, interactor.checkStock(otherId));
        assertEquals(0, stocks.findByProductId(otherId).getVersion());
    }

    @Test
    void rejectsStaleSaveWithoutOverwritingSuccessfulUpdate() {
        Stock stale = stocks.findByProductId(productId);
        stale.decrease(5);
        interactor.increaseStock(productId, 2);
        assertFalse(stocks.saveIfVersionMatches(stale));
        assertStoredState(12, 5);
    }

    @Test
    void enforcesUniqueProductsAndOneStockPerProduct() {
        assertRejected(() -> products.addProduct(product(productId)));
        assertRejected(() -> stocks.addStock(new Stock(UUID.randomUUID(), productId, 20, 0)));
        assertRejected(() -> stocks.addStock(new Stock(UUID.randomUUID(), UUID.randomUUID(), 20, 0)));
        UUID otherId = UUID.randomUUID();
        products.addProduct(product(otherId));
        assertRejected(() -> stocks.addStock(new Stock(stockId, otherId, 20, 0)));
        assertNull(stocks.findByProductId(otherId));
        assertStoredState(10, 4);
    }

    @Test
    void rejectsSaveWithIncorrectIdentityOrMissingAssociation() {
        assertRejected(() -> stocks.saveIfVersionMatches(new Stock(UUID.randomUUID(), productId, 20, 4)));
        UUID otherId = UUID.randomUUID();
        products.addProduct(product(otherId));
        assertFalse(stocks.saveIfVersionMatches(new Stock(stockId, otherId, 20, 4)));
        assertFalse(stocks.saveIfVersionMatches(new Stock(stockId, UUID.randomUUID(), 20, 4)));
        assertStoredState(10, 4);
    }

    @Test
    void rejectsNullServiceRecords() {
        assertRejected(() -> products.addProduct(null));
        assertRejected(() -> stocks.addStock(null));
        assertRejected(() -> stocks.saveIfVersionMatches(null));
        assertStoredState(10, 4);
    }

    @Test
    void failedSaveDoesNotPersistAdjustedQuantityWhenVersionIsExhausted() {
        UUID otherId = UUID.randomUUID();
        products.addProduct(product(otherId));
        stocks.addStock(new Stock(UUID.randomUUID(), otherId, 3, Long.MAX_VALUE));
        assertRejected(() -> interactor.increaseStock(otherId, 1));
        assertRejected(() -> interactor.decreaseStock(otherId, 1));
        assertEquals(3, interactor.checkStock(otherId));
        assertEquals(Long.MAX_VALUE, stocks.findByProductId(otherId).getVersion());
    }

    @Test
    void handlerChecksStockAndIgnoresAdjustmentQuantity() {
        StockRequestHandler handler = interactor;
        for (int quantity : new int[] {0, -1, Integer.MAX_VALUE}) {
            StockResponse response = handler.handle(new StockRequest(productId, StockOperation.CHECK, quantity));
            assertSuccessfulResponse(response, 10);
            assertStoredState(10, 4);
        }
    }

    @Test
    void handlerIncreasesStockAndPreservesConflictProtection() {
        Stock stale = stocks.findByProductId(productId);
        stale.decrease(2);
        StockResponse response = interactor.handle(new StockRequest(productId, StockOperation.INCREASE, 5));
        assertSuccessfulResponse(response, 15);
        assertStoredState(15, 5);
        assertFalse(stocks.saveIfVersionMatches(stale));
        assertStoredState(15, 5);
    }

    @Test
    void handlerDecreasesStockIncludingToZero() {
        assertSuccessfulResponse(interactor.handle(new StockRequest(productId, StockOperation.DECREASE, 3)), 7);
        assertStoredState(7, 5);
        assertSuccessfulResponse(interactor.handle(new StockRequest(productId, StockOperation.DECREASE, 7)), 0);
        assertStoredState(0, 6);
    }

    @Test
    void handlerRejectsNonPositiveAdjustmentsWithoutPersistingChanges() {
        for (StockOperation operation : new StockOperation[] {StockOperation.INCREASE, StockOperation.DECREASE}) {
            for (int quantity : new int[] {0, -1, Integer.MIN_VALUE}) {
                StockResponse response = interactor.handle(new StockRequest(productId, operation, quantity));
                assertFailedResponse(response, productId, "Aura", 10);
                assertStoredState(10, 4);
            }
        }
    }

    @Test
    void handlerRejectsInsufficientStockAndOverflowWithoutPersistingChanges() {
        assertFailedResponse(interactor.handle(new StockRequest(productId, StockOperation.DECREASE, 11)),
            productId, "Aura", 10);
        assertStoredState(10, 4);
        assertFailedResponse(interactor.handle(new StockRequest(productId, StockOperation.INCREASE, Integer.MAX_VALUE)),
            productId, "Aura", 10);
        assertStoredState(10, 4);
    }

    @Test
    void handlerReportsUnknownProductWithoutInventingNameOrQuantity() {
        UUID unknown = UUID.randomUUID();
        for (StockOperation operation : StockOperation.values()) {
            StockResponse response = interactor.handle(new StockRequest(unknown, operation, 1));
            assertFailedResponse(response, unknown, null, -1);
            assertTrue(response.getMessage().contains("Unknown product"));
            assertStoredState(10, 4);
        }
    }

    @Test
    void handlerReportsMissingStockWithKnownProductNameAndUnavailableQuantity() {
        UUID missingStock = UUID.randomUUID();
        products.addProduct(product(missingStock));
        for (StockOperation operation : StockOperation.values()) {
            StockResponse response = interactor.handle(new StockRequest(missingStock, operation, 1));
            assertFailedResponse(response, missingStock, "Aura", -1);
            assertTrue(response.getMessage().contains("Missing stock"));
            assertNull(stocks.findByProductId(missingStock));
            assertStoredState(10, 4);
        }
    }

    @Test
    void handlerRejectsNullRequestWithoutPersistingChanges() {
        assertFailedResponse(interactor.handle(null), null, null, -1);
        assertStoredState(10, 4);
    }

    @Test
    void handlerReportsSaveFailureWithoutPersistingAdjustedQuantity() {
        UUID otherId = UUID.randomUUID();
        products.addProduct(product(otherId));
        stocks.addStock(new Stock(UUID.randomUUID(), otherId, 3, Long.MAX_VALUE));
        for (StockOperation operation : new StockOperation[] {StockOperation.INCREASE, StockOperation.DECREASE}) {
            StockResponse response = interactor.handle(new StockRequest(otherId, operation, 1));
            assertFailedResponse(response, otherId, "Aura", 3);
            assertEquals(3, stocks.findByProductId(otherId).getAvailableQuantity());
            assertEquals(Long.MAX_VALUE, stocks.findByProductId(otherId).getVersion());
        }
    }

    @Test
    void handlerPropagatesUnexpectedTechnicalFailure() {
        UUID otherId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("Unexpected product lookup failure");
        Product brokenProduct = new Product(otherId, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null) {
            @Override
            public String getName() {
                throw failure;
            }
        };
        products.addProduct(brokenProduct);
        stocks.addStock(new Stock(UUID.randomUUID(), otherId, 3, 0));
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> interactor.handle(new StockRequest(otherId, StockOperation.INCREASE, 1))));
        assertEquals(3, stocks.findByProductId(otherId).getAvailableQuantity());
        assertEquals(0, stocks.findByProductId(otherId).getVersion());
    }

    @Test
    void handlerReportsRealVersionConflictWithoutRetryOrOverwritingNewerData() {
        for (StockOperation operation : new StockOperation[] {StockOperation.INCREASE, StockOperation.DECREASE}) {
            Stock before = stocks.findByProductId(productId);
            java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
            StockRepository conflicting = new StockRepository() {
                @Override
                public Stock findByProductId(UUID id) {
                    return stocks.findByProductId(id);
                }

                @Override
                public boolean saveIfVersionMatches(Stock stale) {
                    saves.incrementAndGet();
                    Stock newer = stocks.findByProductId(productId);
                    newer.increase(2);
                    assertTrue(stocks.saveIfVersionMatches(newer));
                    return stocks.saveIfVersionMatches(stale);
                }
            };
            SynchronizeStockInteractor subject = new SynchronizeStockInteractor(products, conflicting);
            StockResponse response = subject.handle(new StockRequest(productId, operation, 1));
            assertFailedResponse(response, productId, "Aura", -1);
            assertTrue(response.getMessage().contains("conflicts"));
            assertEquals(1, saves.get());
            assertStoredState(before.getAvailableQuantity() + 2, before.getVersion() + 1);
        }
    }

    @Test
    void compatibilityMethodsRejectFailedSaveWithoutRetry() {
        java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
        StockRepository rejecting = new StockRepository() {
            @Override
            public Stock findByProductId(UUID id) {
                return stocks.findByProductId(id);
            }

            @Override
            public boolean saveIfVersionMatches(Stock stock) {
                saves.incrementAndGet();
                return false;
            }
        };
        SynchronizeStockInteractor subject = new SynchronizeStockInteractor(products, rejecting);
        assertRejected(() -> subject.increaseStock(productId, 1));
        assertRejected(() -> subject.decreaseStock(productId, 1));
        assertEquals(2, saves.get());
        assertStoredState(10, 4);
    }

    @Test
    void unexpectedRepositorySaveFailurePropagatesWithoutChangingStoredStock() {
        IllegalStateException failure = new IllegalStateException("Storage unavailable");
        StockRepository broken = new StockRepository() {
            @Override
            public Stock findByProductId(UUID id) {
                return stocks.findByProductId(id);
            }

            @Override
            public boolean saveIfVersionMatches(Stock stock) {
                throw failure;
            }
        };
        SynchronizeStockInteractor subject = new SynchronizeStockInteractor(products, broken);
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> subject.handle(new StockRequest(productId, StockOperation.INCREASE, 1))));
        assertStoredState(10, 4);
    }

    private void assertSuccessfulResponse(StockResponse response, int quantity) {
        assertTrue(response.isSuccess());
        assertEquals(productId, response.getProductId());
        assertEquals("Aura", response.getProductName());
        assertEquals(quantity, response.getAvailableQuantity());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }

    private static void assertFailedResponse(StockResponse response, UUID id, String name, int quantity) {
        assertFalse(response.isSuccess());
        assertEquals(id, response.getProductId());
        assertEquals(name, response.getProductName());
        assertEquals(quantity, response.getAvailableQuantity());
        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
    }

    private void assertStoredState(int quantity, long version) {
        Stock stored = stocks.findByProductId(productId);
        assertEquals(quantity, stored.getAvailableQuantity());
        assertEquals(version, stored.getVersion());
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable operation) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, operation);
        assertNotNull(error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }

    private static Product product(UUID id) {
        return new Product(id, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null);
    }
}
