package datatransferobjects;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderItemRequestTest {
    private static final UUID PRODUCT_ID = new UUID(0, 1);

    @Test
    void preservesProductIdentifierAndQuantity() {
        OrderItemRequest request = new OrderItemRequest(PRODUCT_ID, Integer.MAX_VALUE);
        assertEquals(PRODUCT_ID, request.getProductId());
        assertEquals(Integer.MAX_VALUE, request.getQuantity());
    }

    @Test
    void rejectsNullIdentifierAndNonpositiveQuantities() {
        assertThrows(NullPointerException.class, () -> new OrderItemRequest(null, 1));
        for (int quantity : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new OrderItemRequest(PRODUCT_ID, quantity));
        }
    }
}
