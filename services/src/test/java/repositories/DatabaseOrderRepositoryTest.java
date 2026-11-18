package repositories;

import entities.FulfillmentType;
import entities.Order;
import entities.OrderItem;
import entities.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseOrderRepositoryTest {
    @Test
    void boutiqueUpdatesRejectMissingAndStaleSnapshotsAndPreserveCopies() {
        DatabaseOrderRepository repository = new DatabaseOrderRepository();
        Order newOrder = order(new UUID(0, 1), "001");
        assertFalse(repository.updateIfVersionMatches(newOrder, 0));
        repository.save(newOrder);
        Order candidate = repository.findById(newOrder.getOrderId());
        Order stale = repository.findById(newOrder.getOrderId());
        candidate.confirm();
        assertTrue(repository.updateIfVersionMatches(candidate, 1));
        assertEquals(1, candidate.getVersion());
        assertEquals(2, repository.findById(candidate.getOrderId()).getVersion());
        stale.cancel("Stale cancellation");
        assertFalse(repository.updateIfVersionMatches(stale, 1));
        assertFalse(repository.updateIfVersionMatches(candidate, 2));
        candidate.cancel("External mutation after saving");
        assertEquals(OrderStatus.CONFIRMED, repository.findById(candidate.getOrderId()).getStatus());
        assertEquals(2, repository.findById(candidate.getOrderId()).getVersion());
    }

    @Test
    void concurrentBoutiqueUpdatesPublishExactlyOneVersion() throws Exception {
        DatabaseOrderRepository repository = new DatabaseOrderRepository();
        Order order = order(new UUID(0, 1), "001");
        repository.save(order);
        Order confirmed = repository.findById(order.getOrderId());
        Order cancelled = repository.findById(order.getOrderId());
        confirmed.confirm();
        cancelled.cancel("Customer request");
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { gate.await(); return repository.updateIfVersionMatches(confirmed, 1); });
            var second = workers.submit(() -> { gate.await(); return repository.updateIfVersionMatches(cancelled, 1); });
            gate.countDown();
            boolean firstSaved = first.get(5, java.util.concurrent.TimeUnit.SECONDS);
            boolean secondSaved = second.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(firstSaved, secondSaved);
            assertEquals(2, repository.findById(order.getOrderId()).getVersion());
            assertEquals(firstSaved ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED,
                repository.findById(order.getOrderId()).getStatus());
        }
    }
    @Test
    void savesAndFindsDefensiveCopiesWithVersionAdvancement() {
        DatabaseOrderRepository repository = new DatabaseOrderRepository();
        Order supplied = order(new UUID(0, 1), "001");
        assertNull(repository.findById(supplied.getOrderId()));
        repository.save(supplied);
        assertEquals(0, supplied.getVersion());
        supplied.cancel("External mutation");
        Order retrieved = repository.findById(supplied.getOrderId());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, retrieved.getStatus());
        assertEquals(1, retrieved.getVersion());
        assertEquals(List.of(supplied.getItems().getFirst()), retrieved.getItems());
        assertEquals(supplied.getTotal(), retrieved.getTotal());
        assertThrows(UnsupportedOperationException.class, () -> retrieved.getItems().clear());
        retrieved.cancel("Customer request");
        assertNull(repository.findById(supplied.getOrderId()).getCancellationReason());
        repository.save(retrieved);
        assertEquals(1, retrieved.getVersion());
        Order stored = repository.findById(supplied.getOrderId());
        assertEquals(2, stored.getVersion());
        assertEquals(OrderStatus.CANCELLED, stored.getStatus());
        assertEquals("Customer request", stored.getCancellationReason());
    }

    @Test
    void rejectsStaleWritesAndDuplicateNumbersWithoutChangingExistingOrder() {
        DatabaseOrderRepository repository = new DatabaseOrderRepository();
        Order first = order(new UUID(0, 1), "001");
        repository.save(first);
        assertThrows(IllegalArgumentException.class, () -> repository.save(first));
        Order duplicate = order(new UUID(0, 2), "001");
        assertThrows(IllegalArgumentException.class, () -> repository.save(duplicate));
        assertNull(repository.findById(duplicate.getOrderId()));
        Order stored = repository.findById(first.getOrderId());
        assertEquals(1, stored.getVersion());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stored.getStatus());
    }

    private static Order order(UUID id, String number) {
        OrderItem item = new OrderItem(new UUID(0, 3), new UUID(0, 4), 1, BigDecimal.TEN);
        return new Order(id, number, new UUID(0, 5), List.of(item), BigDecimal.TEN, BigDecimal.ZERO,
            BigDecimal.TEN, 0, OrderStatus.PENDING_CONFIRMATION, FulfillmentType.DELIVERY, "Wrap", "Bell", null, null, 0);
    }
}
