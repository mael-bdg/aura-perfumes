package entities;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockTest {
    @Test
    void createsStockWithInheritedIdentityAndVersion() {
        UUID id = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Stock stock = new Stock(id, productId, 10, 7);
        assertEquals(id, stock.getId());
        assertEquals(productId, stock.getProductId());
        assertEquals(10, stock.getAvailableQuantity());
        assertEquals(7, stock.getVersion());
    }

    @Test
    void acceptsZeroQuantityAndVersion() {
        Stock stock = new Stock(UUID.randomUUID(), UUID.randomUUID(), 0, 0);
        assertEquals(0, stock.getAvailableQuantity());
        assertEquals(0, stock.getVersion());
    }

    @Test
    void rejectsInvalidInitialState() {
        UUID id = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> new Stock(null, productId, 0, 0));
        assertThrows(NullPointerException.class, () -> new Stock(id, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stock(id, productId, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stock(id, productId, 0, -1));
    }

    @Test
    void adjustsQuantityWithoutChangingVersion() {
        Stock stock = new Stock(UUID.randomUUID(), UUID.randomUUID(), 10, Long.MAX_VALUE);
        stock.increase(5);
        assertEquals(15, stock.getAvailableQuantity());
        assertEquals(Long.MAX_VALUE, stock.getVersion());
        stock.decrease(4);
        assertEquals(11, stock.getAvailableQuantity());
        stock.decrease(11);
        assertEquals(0, stock.getAvailableQuantity());
        assertEquals(Long.MAX_VALUE, stock.getVersion());
    }

    @Test
    void rejectsNonPositiveAdjustmentsWithoutChangingState() {
        Stock stock = new Stock(UUID.randomUUID(), UUID.randomUUID(), 10, 7);
        for (int quantity : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> stock.increase(quantity));
            assertEquals(10, stock.getAvailableQuantity());
            assertThrows(IllegalArgumentException.class, () -> stock.decrease(quantity));
            assertEquals(10, stock.getAvailableQuantity());
            assertEquals(7, stock.getVersion());
        }
    }

    @Test
    void rejectsInsufficientStockWithoutChangingState() {
        Stock stock = new Stock(UUID.randomUUID(), UUID.randomUUID(), 10, 7);
        assertThrows(IllegalArgumentException.class, () -> stock.decrease(11));
        assertEquals(10, stock.getAvailableQuantity());
        assertEquals(7, stock.getVersion());
        Stock empty = new Stock(UUID.randomUUID(), UUID.randomUUID(), 0, 0);
        assertThrows(IllegalArgumentException.class, () -> empty.decrease(1));
        assertEquals(0, empty.getAvailableQuantity());
    }

    @Test
    void permitsIntegerLimitAndRejectsOverflowWithoutChangingState() {
        Stock stock = new Stock(UUID.randomUUID(), UUID.randomUUID(), 0, 7);
        stock.increase(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, stock.getAvailableQuantity());
        for (int quantity : new int[] {1, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> stock.increase(quantity));
            assertEquals(Integer.MAX_VALUE, stock.getAvailableQuantity());
            assertEquals(7, stock.getVersion());
        }
    }
}
