package gui.stock;

import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import inventory.RepositoryInventoryQuery;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import usecases.SynchronizeStockInteractor;
import static org.junit.jupiter.api.Assertions.*;

class StockDashboardServiceTest {
    private final UUID id = new UUID(0, 1);
    private final DatabaseProductRepository products = new DatabaseProductRepository();
    private final DatabaseStockRepository stocks = new DatabaseStockRepository(products);
    private final SynchronizeStockInteractor handler = new SynchronizeStockInteractor(products, stocks);
    private final RepositoryInventoryQuery query = new RepositoryInventoryQuery(products, stocks);

    StockDashboardServiceTest() {
        products.addProduct(new Product(id, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null));
        stocks.addStock(new Stock(new UUID(0, 2), id, 10, 4));
    }

    @Test
    void successfulAdjustmentUsesU1AndRefreshesTheSameSessionStateExactlyOnce() {
        var service = new StockDashboardService(query, handler, Runnable::run);
        var previous = service.refresh().join();
        var increased = service.execute(request(StockOperation.INCREASE, 5)).join();
        assertTrue(increased.response().isSuccess());
        assertEquals(15, increased.rows().getFirst().quantity());
        assertEquals(5, stocks.findByProductId(id).getVersion());
        assertEquals(10, previous.getFirst().quantity());
        assertTrue(handler.handle(request(StockOperation.DECREASE, 3)).isSuccess());
        assertEquals(12, service.refresh().join().getFirst().quantity());
        assertEquals(6, stocks.findByProductId(id).getVersion());
    }

    @Test
    void checkRemainsReadOnlyAndDecreasePersistsThroughTheExistingBoundary() {
        var service = new StockDashboardService(query, handler, Runnable::run);
        assertTrue(service.execute(request(StockOperation.CHECK, 0)).join().response().isSuccess());
        assertEquals(4, stocks.findByProductId(id).getVersion());
        assertEquals(7, service.execute(request(StockOperation.DECREASE, 3)).join().rows().getFirst().quantity());
        assertEquals(5, stocks.findByProductId(id).getVersion());
    }

    @Test
    void businessFailurePreservesDataAndExposesTheActualErrorWithFreshQuantities() {
        var service = new StockDashboardService(query, handler, Runnable::run);
        var rejected = service.execute(request(StockOperation.DECREASE, 11)).join();
        assertFalse(rejected.response().isSuccess());
        assertTrue(rejected.response().getMessage().contains("cannot exceed stock"));
        assertEquals(10, rejected.rows().getFirst().quantity());
        assertEquals(4, stocks.findByProductId(id).getVersion());
        assertNull(rejected.refreshFailure());
    }

    @Test
    void staleSaveIsNotRetriedAndTheRefreshShowsNewerSharedState() {
        AtomicInteger attempts = new AtomicInteger();
        var service = new StockDashboardService(query, request -> {
            attempts.incrementAndGet();
            Stock newer = stocks.findByProductId(id);
            newer.decrease(2);
            assertTrue(stocks.saveIfVersionMatches(newer));
            return new StockResponse(false, id, "Aura", -1, "Stock version conflict");
        }, Runnable::run);
        var result = service.execute(request(StockOperation.INCREASE, 1)).join();
        assertFalse(result.response().isSuccess());
        assertEquals("Stock version conflict", result.response().getMessage());
        assertEquals(8, result.rows().getFirst().quantity());
        assertEquals(1, attempts.get());
        assertEquals(5, stocks.findByProductId(id).getVersion());
    }

    @Test
    void rejectedSecondSubmissionNeverQueuesOrExecutesAnotherOperation() {
        ArrayDeque<Runnable> pending = new ArrayDeque<>();
        AtomicInteger calls = new AtomicInteger();
        var service = new StockDashboardService(query, request -> {
            calls.incrementAndGet();
            return handler.handle(request);
        }, pending::add);
        var first = service.execute(request(StockOperation.INCREASE, 1));
        var duplicate = service.execute(request(StockOperation.INCREASE, 1));
        assertThrows(CompletionException.class, duplicate::join);
        assertEquals(1, pending.size());
        pending.remove().run();
        assertTrue(first.join().response().isSuccess());
        assertEquals(1, calls.get());
        assertEquals(11, stocks.findByProductId(id).getAvailableQuantity());
        assertEquals(5, stocks.findByProductId(id).getVersion());
    }

    @Test
    void committedResultIsNotMisreportedAsFailureWhenItsRefreshFails() {
        IllegalStateException unavailable = new IllegalStateException("Read unavailable");
        var service = new StockDashboardService(() -> { throw unavailable; }, handler, Runnable::run);
        var result = service.execute(request(StockOperation.INCREASE, 1)).join();
        assertTrue(result.response().isSuccess());
        assertNull(result.rows());
        assertSame(unavailable, result.refreshFailure());
        assertEquals(11, stocks.findByProductId(id).getAvailableQuantity());
        assertEquals(5, stocks.findByProductId(id).getVersion());
    }

    @Test
    void unexpectedFailuresPropagateAndReleaseTheSubmissionGuard() {
        IllegalArgumentException failure = new IllegalArgumentException("Unexpected adapter failure");
        AtomicInteger calls = new AtomicInteger();
        var service = new StockDashboardService(query, request -> {
            calls.incrementAndGet();
            throw failure;
        }, Runnable::run);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertSame(failure, assertThrows(CompletionException.class,
                () -> service.execute(request(StockOperation.INCREASE, 1)).join()).getCause());
        }
        assertEquals(2, calls.get());
        assertEquals(10, stocks.findByProductId(id).getAvailableQuantity());
    }

    @Test
    void blockingReadsAndUseCaseCallsRunOnTheInjectedWorker() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> readThread = new AtomicReference<>();
        AtomicReference<Thread> handlerThread = new AtomicReference<>();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var service = new StockDashboardService(() -> {
                readThread.set(Thread.currentThread());
                return query.load();
            }, request -> {
                handlerThread.set(Thread.currentThread());
                return handler.handle(request);
            }, worker);
            service.refresh().get(5, TimeUnit.SECONDS);
            service.execute(request(StockOperation.CHECK, 0)).get(5, TimeUnit.SECONDS);
            assertNotSame(caller, readThread.get());
            assertNotSame(caller, handlerThread.get());
            assertSame(readThread.get(), handlerThread.get());
        }
    }

    @Test
    void workerRejectionReleasesTheGuardForAnExplicitLaterSubmission() {
        AtomicInteger schedulingAttempts = new AtomicInteger();
        var service = new StockDashboardService(query, handler, task -> {
            if (schedulingAttempts.getAndIncrement() == 0) {
                throw new java.util.concurrent.RejectedExecutionException("Worker unavailable");
            }
            task.run();
        });
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class,
            assertThrows(CompletionException.class, () -> service.execute(request(StockOperation.INCREASE, 1)).join()).getCause());
        assertTrue(service.execute(request(StockOperation.INCREASE, 1)).join().response().isSuccess());
        assertEquals(11, stocks.findByProductId(id).getAvailableQuantity());
        assertEquals(5, stocks.findByProductId(id).getVersion());
    }

    private StockRequest request(StockOperation operation, int quantity) {
        return new StockRequest(id, operation, quantity);
    }
}
