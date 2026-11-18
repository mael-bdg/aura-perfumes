package application;

import entities.FulfillmentType;
import entities.Order;
import entities.OrderItem;
import entities.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import repositories.DatabaseOrderRepository;

/** Demo session orders shared by U2 and U4, not a second repository. */
public final class DemoBoutiqueOrders {
    private DemoBoutiqueOrders() { }

    public static void populate(DatabaseOrderRepository orders) {
        String[] statuses = {"PENDING_CONFIRMATION", "CONFIRMED", "IN_PREPARATION"};
        for (int i = 0; i < statuses.length; i++) {
            int number = i + 2;
            UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc" + (i + 1));
            UUID itemId = UUID.fromString("dddddddd-dddd-dddd-dddd-ddddddddddd" + (i + 1));
            OrderItem item = new OrderItem(itemId, new UUID(0, 200), 1, new BigDecimal("24.50"));
            orders.save(new Order(id, "ORD-000" + number, DemoOrderData.CUSTOMER_ID,
                List.of(item), new BigDecimal("24.50"), BigDecimal.ZERO,
                new BigDecimal("24.50"), 0, OrderStatus.valueOf(statuses[i]),
                FulfillmentType.IN_STORE_PICKUP, null, null, null, null, 0));
        }
    }
}
