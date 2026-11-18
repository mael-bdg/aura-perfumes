package datatransferobjects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderRequestTest {
    private static final UUID CUSTOMER_ID = new UUID(0, 1);
    private static final OrderItemRequest ITEM = new OrderItemRequest(new UUID(0, 2), 3);

    @Test
    void preservesUmlFieldsAndCopiesItemList() {
        List<OrderItemRequest> items = new ArrayList<>(List.of(ITEM));
        PlaceOrderRequest request = new PlaceOrderRequest(CUSTOMER_ID, items, 20, true);
        items.clear();
        assertEquals(CUSTOMER_ID, request.getCustomerId());
        assertEquals(List.of(ITEM), request.getItems());
        assertEquals(20, request.getPointsToRedeem());
        assertTrue(request.isConfirmed());
        assertThrows(UnsupportedOperationException.class, () -> request.getItems().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.getItems().set(0, ITEM));
        PlaceOrderRequest preview = new PlaceOrderRequest(CUSTOMER_ID, List.of(ITEM), 0, false);
        assertFalse(preview.isConfirmed());
        assertEquals(0, preview.getPointsToRedeem());
    }

    @Test
    void rejectsNullRequiredValuesAndEmptyItems() {
        assertThrows(NullPointerException.class, () -> new PlaceOrderRequest(null, List.of(ITEM), 0, false));
        assertThrows(NullPointerException.class, () -> new PlaceOrderRequest(CUSTOMER_ID, null, 0, false));
        assertThrows(NullPointerException.class,
            () -> new PlaceOrderRequest(CUSTOMER_ID, Arrays.asList(ITEM, null), 0, false));
        assertThrows(IllegalArgumentException.class, () -> new PlaceOrderRequest(CUSTOMER_ID, List.of(), 0, false));
    }

    @Test
    void rejectsNegativePoints() {
        assertThrows(IllegalArgumentException.class,
            () -> new PlaceOrderRequest(CUSTOMER_ID, List.of(ITEM), -1, true));
    }
}
