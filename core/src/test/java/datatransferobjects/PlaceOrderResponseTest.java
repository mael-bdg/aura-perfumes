package datatransferobjects;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderResponseTest {
    @Test
    void exposesExactAmountsAndUmlFields() {
        UUID orderId = new UUID(0, 1);
        PlaceOrderResponse response = new PlaceOrderResponse(true, orderId, new BigDecimal("10.30"),
            new BigDecimal("0.20"), new BigDecimal("10.10"), 2, "Preview calculated");
        assertTrue(response.isSuccess());
        assertEquals(orderId, response.getOrderId());
        assertEquals(new BigDecimal("10.30"), response.getSubtotal());
        assertEquals(new BigDecimal("0.20"), response.getDiscount());
        assertEquals(new BigDecimal("10.10"), response.getTotal());
        assertEquals(2, response.getRedeemedPoints());
        assertEquals("Preview calculated", response.getMessage());
    }

    @Test
    void representsUnavailableIdentityAndAmountsExplicitly() {
        PlaceOrderResponse response = new PlaceOrderResponse(false, null, null, null, null, 0, "Unknown customer");
        assertFalse(response.isSuccess());
        assertNull(response.getOrderId());
        assertNull(response.getSubtotal());
        assertNull(response.getDiscount());
        assertNull(response.getTotal());
        assertEquals(0, response.getRedeemedPoints());
        assertEquals("Unknown customer", response.getMessage());
        assertThrows(NullPointerException.class,
            () -> new PlaceOrderResponse(false, null, null, null, null, 0, null));
    }

    @Test
    void isFinalAndContainsOnlyPrivateFinalImmutableValues() {
        assertTrue(Modifier.isFinal(PlaceOrderResponse.class.getModifiers()));
        for (var field : PlaceOrderResponse.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
            assertTrue(java.util.Set.of(boolean.class, int.class, UUID.class, BigDecimal.class, String.class)
                .contains(field.getType()));
        }
    }
}
