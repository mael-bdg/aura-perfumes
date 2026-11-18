package entities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {
    private static final UUID ID = new UUID(0, 1);
    private static final UUID CUSTOMER_ID = new UUID(0, 2);
    private static final OrderItem ITEM = new OrderItem(new UUID(0, 3), new UUID(0, 4), 1, BigDecimal.TEN);
    private static final BigDecimal DISCOUNT = new BigDecimal("2.00");
    private static final BigDecimal TOTAL = new BigDecimal("8.00");

    @Test
    void restoresFullStateWithInheritedIdentityAndGetters() {
        Order order = new Order(ID, "ORDER-001", CUSTOMER_ID, List.of(ITEM), BigDecimal.TEN,
            DISCOUNT, TOTAL, 20, OrderStatus.PENDING_CONFIRMATION, FulfillmentType.DELIVERY,
            "Gift wrap", "Ring bell", null, null, 7);
        assertEquals(ID, order.getId());
        assertEquals(order.getId(), order.getOrderId());
        assertEquals("ORDER-001", order.getOrderNumber());
        assertEquals(CUSTOMER_ID, order.getCustomerId());
        assertEquals(List.of(ITEM), order.getItems());
        assertEquals(BigDecimal.TEN, order.getSubtotal());
        assertEquals(DISCOUNT, order.getDiscount());
        assertEquals(TOTAL, order.getTotal());
        assertEquals(20, order.getRedeemedPoints());
        assertEquals(FulfillmentType.DELIVERY, order.getFulfillmentType());
        assertEquals("Gift wrap", order.getPackagingInstructions());
        assertEquals("Ring bell", order.getDeliveryNotes());
        assertLifecycleState(order, OrderStatus.PENDING_CONFIRMATION, null, null, 7);
    }

    @Test
    void rejectsNullIdentifiersAndRequiredAttributes() {
        assertThrows(NullPointerException.class, () -> new Order(null, "001", CUSTOMER_ID, List.of(ITEM),
            BigDecimal.TEN, DISCOUNT, TOTAL, 0, OrderStatus.CONFIRMED, FulfillmentType.DELIVERY, null, null, null, null, 0));
        assertThrows(NullPointerException.class, () -> new Order(ID, null, CUSTOMER_ID, List.of(ITEM),
            BigDecimal.TEN, DISCOUNT, TOTAL, 0, OrderStatus.CONFIRMED, FulfillmentType.DELIVERY, null, null, null, null, 0));
        assertThrows(NullPointerException.class, () -> new Order(ID, "001", null, List.of(ITEM),
            BigDecimal.TEN, DISCOUNT, TOTAL, 0, OrderStatus.CONFIRMED, FulfillmentType.DELIVERY, null, null, null, null, 0));
        assertThrows(NullPointerException.class, () -> restored(null, FulfillmentType.DELIVERY));
        assertThrows(NullPointerException.class, () -> restored(OrderStatus.CONFIRMED, null));
        for (String blank : List.of("", " \t\n")) {
            assertThrows(IllegalArgumentException.class, () -> new Order(ID, blank, CUSTOMER_ID, List.of(ITEM),
                BigDecimal.TEN, DISCOUNT, TOTAL, 0, OrderStatus.CONFIRMED, FulfillmentType.DELIVERY, null, null, null, null, 0));
        }
    }

    @Test
    void rejectsMissingEmptyOrNullContainingItemLists() {
        assertThrows(NullPointerException.class,
            () -> amounts(null, BigDecimal.TEN, DISCOUNT, TOTAL, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> amounts(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        assertThrows(NullPointerException.class,
            () -> amounts(Arrays.asList(ITEM, null), BigDecimal.TEN, DISCOUNT, TOTAL, 0, 0));
    }

    @Test
    void rejectsNullOrNegativeMonetaryAmounts() {
        BigDecimal negative = new BigDecimal("-0.01");
        assertThrows(NullPointerException.class, () -> amounts(List.of(ITEM), null, DISCOUNT, TOTAL, 0, 0));
        assertThrows(NullPointerException.class, () -> amounts(List.of(ITEM), BigDecimal.TEN, null, TOTAL, 0, 0));
        assertThrows(NullPointerException.class, () -> amounts(List.of(ITEM), BigDecimal.TEN, DISCOUNT, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> amounts(List.of(ITEM), negative, DISCOUNT, TOTAL, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> amounts(List.of(ITEM), BigDecimal.TEN, negative, TOTAL, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> amounts(List.of(ITEM), BigDecimal.TEN, DISCOUNT, negative, 0, 0));
    }

    @Test
    void rejectsInconsistentSubtotalDiscountAndTotal() {
        assertThrows(IllegalArgumentException.class,
            () -> amounts(List.of(ITEM), new BigDecimal("11"), BigDecimal.ONE, BigDecimal.TEN, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> amounts(List.of(ITEM), BigDecimal.TEN, new BigDecimal("11"), BigDecimal.ZERO, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> amounts(List.of(ITEM), BigDecimal.TEN, DISCOUNT, new BigDecimal("8.01"), 0, 0));
    }

    @Test
    void acceptsEquivalentDecimalScalesFullDiscountAndZeroAmounts() {
        Order scaled = amounts(List.of(ITEM), new BigDecimal("10.000"), new BigDecimal("2.0"), TOTAL, 0, 0);
        assertEquals(0, scaled.getTotal().compareTo(new BigDecimal("8")));
        Order fullDiscount = amounts(List.of(ITEM), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, 100, 0);
        assertEquals(BigDecimal.ZERO, fullDiscount.getTotal());
        OrderItem freeItem = new OrderItem(new UUID(0, 5), ITEM.getProductId(), 2, BigDecimal.ZERO);
        Order freeOrder = amounts(List.of(freeItem), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        assertEquals(BigDecimal.ZERO, freeOrder.getSubtotal());
        assertEquals(0, freeOrder.getVersion());
    }

    @Test
    void sumsAllLineTotalsExactly() {
        OrderItem fraction = new OrderItem(new UUID(0, 5), new UUID(0, 6), 3, new BigDecimal("0.10"));
        Order order = amounts(List.of(ITEM, fraction), new BigDecimal("10.30"), new BigDecimal("0.30"), BigDecimal.TEN, 0, 0);
        assertEquals(new BigDecimal("10.30"), order.getSubtotal());
        assertEquals(BigDecimal.TEN, order.getTotal());
    }

    @Test
    void rejectsNegativePointsAndVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> amounts(List.of(ITEM), BigDecimal.TEN, DISCOUNT, TOTAL, -1, 0));
        assertThrows(IllegalArgumentException.class,
            () -> amounts(List.of(ITEM), BigDecimal.TEN, DISCOUNT, TOTAL, 0, -1));
    }

    @Test
    void copiesItemsAndExposesAnUnmodifiableList() {
        List<OrderItem> supplied = new ArrayList<>(List.of(ITEM));
        Order order = amounts(supplied, BigDecimal.TEN, DISCOUNT, TOTAL, 0, 0);
        supplied.clear();
        assertEquals(List.of(ITEM), order.getItems());
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().clear());
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().set(0, ITEM));
        assertEquals(BigDecimal.TEN, order.getSubtotal());
    }

    @Test
    void progressesConfirmedPickupOrderWithoutChangingVersion() {
        Order order = restored(OrderStatus.CONFIRMED, FulfillmentType.IN_STORE_PICKUP);
        order.markInPreparation();
        assertLifecycleState(order, OrderStatus.IN_PREPARATION, null, null, 7);
        order.markReadyForPickup("1234");
        assertLifecycleState(order, OrderStatus.READY_FOR_PICKUP, null, "1234", 7);
        assertEquals(TOTAL, order.getTotal());
        assertEquals(List.of(ITEM), order.getItems());
    }

    @Test
    void rejectsPreparationFromEveryUnconfirmedStatusWithoutChangingState() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status != OrderStatus.CONFIRMED) {
                Order order = restored(status, FulfillmentType.IN_STORE_PICKUP);
                String reason = order.getCancellationReason();
                String pin = order.getPickupPin();
                assertThrows(IllegalStateException.class, order::markInPreparation);
                assertLifecycleState(order, status, reason, pin, 7);
            }
        }
    }

    @Test
    void rejectsPickupReadinessFromWrongStatusOrDeliveryWithoutChangingState() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status != OrderStatus.IN_PREPARATION) {
                Order order = restored(status, FulfillmentType.IN_STORE_PICKUP);
                String reason = order.getCancellationReason();
                String pin = order.getPickupPin();
                assertThrows(IllegalStateException.class, () -> order.markReadyForPickup("1234"));
                assertLifecycleState(order, status, reason, pin, 7);
            }
        }
        Order delivery = restored(OrderStatus.CONFIRMED, FulfillmentType.DELIVERY);
        delivery.markInPreparation();
        assertThrows(IllegalStateException.class, () -> delivery.markReadyForPickup("1234"));
        assertLifecycleState(delivery, OrderStatus.IN_PREPARATION, null, null, 7);
    }

    @Test
    void rejectsMissingPickupPinWithoutPartiallyChangingOrder() {
        Order order = restored(OrderStatus.IN_PREPARATION, FulfillmentType.IN_STORE_PICKUP);
        for (String pin : new String[] {null, "", " \t"}) {
            assertThrows(IllegalArgumentException.class, () -> order.markReadyForPickup(pin));
            assertLifecycleState(order, OrderStatus.IN_PREPARATION, null, null, 7);
        }
    }

    @Test
    void cancelsOnlyPendingConfirmedAndPreparingOrdersWithoutChangingVersion() {
        for (OrderStatus status : new OrderStatus[] {OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION}) {
            for (FulfillmentType type : FulfillmentType.values()) {
                Order order = restored(status, type);
                order.cancel("Customer request");
                assertLifecycleState(order, OrderStatus.CANCELLED, "Customer request", null, 7);
                assertEquals(TOTAL, order.getTotal());
            }
        }
    }

    @Test
    void rejectsCancellationAfterReadyCancelledOrRefundedWithoutChangingState() {
        for (OrderStatus status : new OrderStatus[] {OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED, OrderStatus.REFUNDED}) {
            Order order = restored(status, FulfillmentType.IN_STORE_PICKUP);
            String reason = order.getCancellationReason();
            String pin = order.getPickupPin();
            assertThrows(IllegalStateException.class, () -> order.cancel("New reason"));
            assertLifecycleState(order, status, reason, pin, 7);
        }
    }

    @Test
    void rejectsMissingCancellationReasonWithoutPartiallyChangingOrder() {
        for (OrderStatus status : new OrderStatus[] {OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION}) {
            Order order = restored(status, FulfillmentType.DELIVERY);
            for (String reason : new String[] {null, "", " \t"}) {
                assertThrows(IllegalArgumentException.class, () -> order.cancel(reason));
                assertLifecycleState(order, status, null, null, 7);
            }
        }
    }

    @Test
    void rejectsInconsistentRestoredLifecycleState() {
        for (String missing : new String[] {null, "", " \t"}) {
            assertThrows(IllegalArgumentException.class, () -> new Order(ID, "001", CUSTOMER_ID, List.of(ITEM),
                BigDecimal.TEN, DISCOUNT, TOTAL, 0, OrderStatus.CANCELLED, FulfillmentType.DELIVERY, null, null, missing, null, 0));
            assertThrows(IllegalArgumentException.class, () -> new Order(ID, "001", CUSTOMER_ID, List.of(ITEM),
                BigDecimal.TEN, DISCOUNT, TOTAL, 0, OrderStatus.READY_FOR_PICKUP, FulfillmentType.IN_STORE_PICKUP, null, null, null, missing, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> restored(OrderStatus.READY_FOR_PICKUP, FulfillmentType.DELIVERY));
    }

    private static Order restored(OrderStatus status, FulfillmentType type) {
        return new Order(ID, "001", CUSTOMER_ID, List.of(ITEM), BigDecimal.TEN, DISCOUNT, TOTAL,
            20, status, type, null, null, status == OrderStatus.CANCELLED ? "Original reason" : null,
            status == OrderStatus.READY_FOR_PICKUP ? "5678" : null, 7);
    }

    private static Order amounts(List<OrderItem> items, BigDecimal subtotal, BigDecimal discount,
                                 BigDecimal total, int points, long version) {
        return new Order(ID, "001", CUSTOMER_ID, items, subtotal, discount, total, points,
            OrderStatus.PENDING_CONFIRMATION, FulfillmentType.DELIVERY, null, null, null, null, version);
    }

    private static void assertLifecycleState(Order order, OrderStatus status, String reason, String pin, long version) {
        assertEquals(status, order.getStatus());
        assertEquals(reason, order.getCancellationReason());
        assertEquals(pin, order.getPickupPin());
        assertEquals(version, order.getVersion());
    }
}
