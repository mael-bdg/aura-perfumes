package repositories;

import entities.Order;
import gateways.OrderRepository;
import gateways.BoutiqueOrderRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Temporary in-memory order adapter using the UML Database name. */
public final class DatabaseOrderRepository implements OrderRepository, BoutiqueOrderRepository {
    private Map<UUID, Order> orders = new HashMap<>();

    /** Returns a defensive order snapshot, or null when absent. */
    @Override
    public synchronized Order findById(UUID orderId) {
        Objects.requireNonNull(orderId, "Order ID is required");
        Order order = orders.get(orderId);
        return order == null ? null : copy(order, order.getVersion());
    }

    /** Resolves a customer-facing order number to its internal UUID. */
    public synchronized UUID findIdByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return null;
        }

        String reference = orderNumber.trim();

        return orders.values().stream()
            .filter(order -> order.getOrderNumber().equals(reference))
            .map(Order::getOrderId)
            .findFirst()
            .orElse(null);
    }

    /** Inserts or version-checks an order update, preserving unique order numbers. */
    @Override
    public synchronized void save(Order order) {
        Objects.requireNonNull(order, "Order is required");
        Order snapshot = copy(order, order.getVersion());
        Order current = orders.get(snapshot.getOrderId());
        if (current == null) {
            if (snapshot.getVersion() != 0) {
                throw new IllegalArgumentException("New orders must have version zero");
            }
        } else if (current.getVersion() != snapshot.getVersion()
                || !current.getOrderNumber().equals(snapshot.getOrderNumber())
                || !current.getCustomerId().equals(snapshot.getCustomerId())) {
            throw new IllegalArgumentException("Order identity or version conflicts with stored state");
        }
        for (Order existing : orders.values()) {
            if (!existing.getOrderId().equals(snapshot.getOrderId())
                    && existing.getOrderNumber().equals(snapshot.getOrderNumber())) {
                throw new IllegalArgumentException("Order number already exists");
            }
        }
        if (snapshot.getVersion() == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Order version cannot exceed Long.MAX_VALUE");
        }
        Order updated = copy(snapshot, snapshot.getVersion() + 1);
        orders.put(updated.getOrderId(), updated);
    }

    /**
     * U4 updates the same session storage published atomically by U2. The shared
     * repository monitor excludes transaction publication and competing updates.
     * Missing records, stale versions and exhausted versions leave storage unchanged.
     */
    @Override
    public synchronized boolean updateIfVersionMatches(Order order, long expectedVersion) {
        Objects.requireNonNull(order, "Order is required");
        Order current = orders.get(order.getOrderId());
        if (current == null || expectedVersion < 0 || current.getVersion() != expectedVersion
                || order.getVersion() != expectedVersion || expectedVersion == Long.MAX_VALUE) {
            return false;
        }
        save(order);
        return true;
    }

    synchronized Map<UUID, Order> transactionSnapshot() {
        return new HashMap<>(orders);
    }

    synchronized void publishTransaction(Map<UUID, Order> replacement) {
        orders = replacement;
    }

    static Order copy(Order order, long version) {
        return new Order(order.getOrderId(), order.getOrderNumber(), order.getCustomerId(), order.getItems(),
            order.getSubtotal(), order.getDiscount(), order.getTotal(), order.getRedeemedPoints(),
            order.getStatus(), order.getFulfillmentType(), order.getPackagingInstructions(),
            order.getDeliveryNotes(), order.getCancellationReason(), order.getPickupPin(), version);
    }
}
