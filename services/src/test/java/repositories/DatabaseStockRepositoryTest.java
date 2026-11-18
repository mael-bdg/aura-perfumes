package repositories;

import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseStockRepositoryTest {
    private UUID productId;
    private Product product;
    private Stock original;
    private DatabaseProductRepository products;
    private DatabaseStockRepository stocks;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        product = new Product(productId, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null);
        products = new DatabaseProductRepository();
        products.addProduct(product);
        stocks = new DatabaseStockRepository(products);
        original = new Stock(UUID.randomUUID(), productId, 10, 4);
        stocks.addStock(original);
    }

    @Test
    void productLookupAndMissingRecordsFollowGatewayConvention() {
        assertSame(product, products.findById(productId));
        UUID unknown = UUID.randomUUID();
        assertNull(products.findById(unknown));
        assertNull(stocks.findByProductId(unknown));
        UUID missingStock = UUID.randomUUID();
        products.addProduct(new Product(missingStock, "Sample", ProductCategory.SAMPLE_SET, BigDecimal.ZERO, null));
        assertNull(stocks.findByProductId(missingStock));
        assertThrows(IllegalArgumentException.class, () -> products.findById(null));
        assertThrows(IllegalArgumentException.class, () -> stocks.findByProductId(null));
    }

    @Test
    void matchedSaveAdvancesStoredVersionOnlyAndKeepsDefensiveCopies() {
        Stock snapshot = stocks.findByProductId(productId);
        assertNotSame(original, snapshot);
        snapshot.increase(5);
        assertEquals(10, stocks.findByProductId(productId).getAvailableQuantity());
        assertTrue(stocks.saveIfVersionMatches(snapshot));
        assertEquals(4, snapshot.getVersion());
        assertEquals(5, stocks.findByProductId(productId).getVersion());
        snapshot.increase(100);
        assertEquals(15, stocks.findByProductId(productId).getAvailableQuantity());
        assertFalse(stocks.saveIfVersionMatches(snapshot));
        assertEquals(15, stocks.findByProductId(productId).getAvailableQuantity());
        assertEquals(5, stocks.findByProductId(productId).getVersion());
    }

    @Test
    void futureVersionIsRejectedWithoutWriting() {
        Stock future = new Stock(original.getId(), productId, 20, 5);
        assertFalse(stocks.saveIfVersionMatches(future));
        assertEquals(10, stocks.findByProductId(productId).getAvailableQuantity());
        assertEquals(4, stocks.findByProductId(productId).getVersion());
    }

    @Test
    void missingSaveDoesNotInsertStockOrChangeExistingRecord() {
        UUID missing = UUID.randomUUID();
        assertFalse(stocks.saveIfVersionMatches(new Stock(UUID.randomUUID(), missing, 2, 0)));
        assertNull(stocks.findByProductId(missing));
        assertEquals(10, stocks.findByProductId(productId).getAvailableQuantity());
        assertEquals(4, stocks.findByProductId(productId).getVersion());
    }

    @Test
    void concurrentSavesOfSameVersionHaveExactlyOneWinner() throws Exception {
        Stock first = stocks.findByProductId(productId);
        Stock second = stocks.findByProductId(productId);
        first.increase(1);
        second.decrease(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return stocks.saveIfVersionMatches(first);
            });
            var secondResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return stocks.saveIfVersionMatches(second);
            });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            boolean firstWon = firstResult.get(5, TimeUnit.SECONDS);
            boolean secondWon = secondResult.get(5, TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertEquals(firstWon ? 11 : 9, stocks.findByProductId(productId).getAvailableQuantity());
            assertEquals(5, stocks.findByProductId(productId).getVersion());
        }
    }
}
