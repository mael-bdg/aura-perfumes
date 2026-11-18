package entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderEnumsTest {
    @Test
    void containsExactlyTheUmlStatusesAndFulfillmentTypes() {
        assertArrayEquals(new String[] {"PENDING_CONFIRMATION", "CONFIRMED", "IN_PREPARATION",
            "READY_FOR_PICKUP", "CANCELLED", "REFUNDED"},
            java.util.Arrays.stream(OrderStatus.values()).map(Enum::name).toArray(String[]::new));
        assertArrayEquals(new String[] {"DELIVERY", "IN_STORE_PICKUP"},
            java.util.Arrays.stream(FulfillmentType.values()).map(Enum::name).toArray(String[]::new));
    }
}
