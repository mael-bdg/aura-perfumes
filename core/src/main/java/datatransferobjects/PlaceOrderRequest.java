package datatransferobjects;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable U2 input using the exact UUID-corrected UML fields. */
public final class PlaceOrderRequest {
    private final UUID customerId;
    private final List<OrderItemRequest> items;
    private final int pointsToRedeem;
    private final boolean confirmed;

    /**
     * Creates a preview or confirmation request with required customer and items.
     * Null lines, empty item lists and negative redemption are rejected.
     * The UML does not include fulfillment or instruction fields at this boundary.
     */
    public PlaceOrderRequest(UUID customerId, List<OrderItemRequest> items, int pointsToRedeem, boolean confirmed) {
        this.customerId = Objects.requireNonNull(customerId, "Customer ID is required");
        this.items = List.copyOf(items);
        if (this.items.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one product");
        }
        if (pointsToRedeem < 0) {
            throw new IllegalArgumentException("Redeemed points cannot be negative");
        }
        this.pointsToRedeem = pointsToRedeem;
        this.confirmed = confirmed;
    }

    /** Returns the customer UUID. */
    public UUID getCustomerId() {
        return customerId;
    }

    /** Returns the immutable requested lines. */
    public List<OrderItemRequest> getItems() {
        return items;
    }

    /** Returns the requested points redemption. */
    public int getPointsToRedeem() {
        return pointsToRedeem;
    }

    /** Returns whether confirmation was requested instead of preview. */
    public boolean isConfirmed() {
        return confirmed;
    }
}
