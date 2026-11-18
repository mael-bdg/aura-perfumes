package entities;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {
    private static final UUID ID = new UUID(0, 1);
    private static final UUID PRODUCT_ID = new UUID(0, 2);

    @Test
    void createsLineWithInheritedIdentityAndExactTotal() {
        OrderItem item = new OrderItem(ID, PRODUCT_ID, 3, new BigDecimal("0.10"));
        assertEquals(ID, item.getId());
        assertEquals(PRODUCT_ID, item.getProductId());
        assertEquals(3, item.getQuantity());
        assertEquals(new BigDecimal("0.10"), item.getUnitPrice());
        assertEquals(new BigDecimal("0.30"), item.getLineTotal());
    }

    @Test
    void acceptsZeroPriceAndCalculatesLargeTotalsWithoutIntegerOverflow() {
        assertEquals(BigDecimal.ZERO, new OrderItem(ID, PRODUCT_ID, 1, BigDecimal.ZERO).getLineTotal());
        OrderItem item = new OrderItem(ID, PRODUCT_ID, Integer.MAX_VALUE, new BigDecimal("2.00"));
        assertEquals(new BigDecimal("4294967294.00"), item.getLineTotal());
    }

    @Test
    void rejectsMissingIdentifiersOrPrice() {
        assertThrows(NullPointerException.class, () -> new OrderItem(null, PRODUCT_ID, 1, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new OrderItem(ID, null, 1, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new OrderItem(ID, PRODUCT_ID, 1, null));
    }

    @Test
    void rejectsNonpositiveQuantityAndNegativePrice() {
        for (int quantity : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new OrderItem(ID, PRODUCT_ID, quantity, BigDecimal.ONE));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new OrderItem(ID, PRODUCT_ID, 1, new BigDecimal("-0.01")));
    }
}
