package gui.orders;

import entities.ProductCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingCartTest {
    private final OrderDataQuery.Product product = new OrderDataQuery.Product(new UUID(0, 1), "Aura",
        ProductCategory.PERFUME, new BigDecimal("0.10"), 10);

    @Test
    void combinesRepeatedProductsAndChangesQuantitiesUsingExactLocalTotals() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(product, 2);
        cart.add(product, 1);
        assertEquals(1, cart.lines().size());
        assertEquals(3, cart.lines().getFirst().quantity());
        assertEquals(new BigDecimal("0.30"), cart.subtotal());
        cart.changeQuantity(product.id(), 7);
        assertEquals(new BigDecimal("0.70"), cart.lines().getFirst().total());
        assertEquals(10, product.available());
        cart.remove(product.id());
        assertTrue(cart.lines().isEmpty());
    }

    @Test
    void rejectsInvalidAndOverflowingEditsWithoutChangingTheCart() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(product, 2);
        var before = cart.lines();
        for (int invalid : new int[] {0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> cart.add(product, invalid));
            assertThrows(IllegalArgumentException.class, () -> cart.changeQuantity(product.id(), invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> cart.add(product, Integer.MAX_VALUE));
        assertEquals(before, cart.lines());
        assertThrows(UnsupportedOperationException.class, before::clear);
    }

    @Test
    void refreshesKnownDisplayPricesAndRetainsUnknownItemsForAuthoritativeValidation() {
        ShoppingCart cart = new ShoppingCart();
        cart.add(product, 3);
        var updated = new OrderDataQuery.Product(product.id(), "Aura", product.category(), new BigDecimal("0.125"), 4);
        cart.updateCatalog(List.of(updated));
        assertEquals(new BigDecimal("0.375"), cart.subtotal());
        cart.updateCatalog(List.of());
        assertEquals(3, cart.lines().getFirst().quantity());
        cart.clear();
        assertEquals(BigDecimal.ZERO, cart.subtotal());
    }
}
