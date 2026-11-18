package entities;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Order state shared by placement and fulfillment, with repository-managed versioning. */
public final class Order extends Entity {
    private final String orderNumber;
    private final UUID customerId;
    private final List<OrderItem> items;
    private final BigDecimal subtotal;
    private final BigDecimal discount;
    private final BigDecimal total;
    private final int redeemedPoints;
    private OrderStatus status;
    private final FulfillmentType fulfillmentType;
    private final String packagingInstructions;
    private final String deliveryNotes;
    private String cancellationReason;
    private String pickupPin;
    private final long version;

    /**
     * Creates or restores a complete order state. Callers creating U2 orders supply
     * PENDING_CONFIRMATION. Monetary comparisons ignore decimal scale, subtotal
     * must equal the sum of line totals, and total must equal subtotal minus discount.
     * Packaging instructions and delivery notes are optional. CANCELLED requires
     * a nonblank reason. READY_FOR_PICKUP requires pickup fulfillment and a nonblank pin.
     * Lifecycle methods leave the supplied repository version unchanged.
     */
    public Order(UUID id, String orderNumber, UUID customerId, List<OrderItem> items,
                 BigDecimal subtotal, BigDecimal discount, BigDecimal total, int redeemedPoints,
                 OrderStatus status, FulfillmentType fulfillmentType, String packagingInstructions,
                 String deliveryNotes, String cancellationReason, String pickupPin, long version) {
        super(id);
        this.orderNumber = Objects.requireNonNull(orderNumber, "Order number cannot be null");
        if (orderNumber.isBlank()) {
            throw new IllegalArgumentException("Order number cannot be blank");
        }
        this.customerId = Objects.requireNonNull(customerId, "Customer ID cannot be null");
        this.items = List.copyOf(items);
        if (this.items.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }
        this.subtotal = nonNegativeAmount(subtotal, "Subtotal");
        this.discount = nonNegativeAmount(discount, "Discount");
        this.total = nonNegativeAmount(total, "Total");
        BigDecimal lineTotal = BigDecimal.ZERO;
        for (OrderItem item : this.items) {
            lineTotal = lineTotal.add(item.getLineTotal());
        }
        if (subtotal.compareTo(lineTotal) != 0) {
            throw new IllegalArgumentException("Subtotal must equal the sum of line totals");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed subtotal");
        }
        if (total.compareTo(subtotal.subtract(discount)) != 0) {
            throw new IllegalArgumentException("Total must equal subtotal minus discount");
        }
        if (redeemedPoints < 0) {
            throw new IllegalArgumentException("Redeemed points cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }
        this.status = Objects.requireNonNull(status, "Order status cannot be null");
        this.fulfillmentType = Objects.requireNonNull(fulfillmentType, "Fulfillment type cannot be null");
        if (status == OrderStatus.CANCELLED) {
            requireText(cancellationReason, "Cancellation reason");
        }
        if (status == OrderStatus.READY_FOR_PICKUP) {
            if (fulfillmentType != FulfillmentType.IN_STORE_PICKUP) {
                throw new IllegalArgumentException("Ready-for-pickup orders require pickup fulfillment");
            }
            requireText(pickupPin, "Pickup pin");
        }
        this.redeemedPoints = redeemedPoints;
        this.packagingInstructions = packagingInstructions;
        this.deliveryNotes = deliveryNotes;
        this.cancellationReason = cancellationReason;
        this.pickupPin = pickupPin;
        this.version = version;
    }

    /** Moves a confirmed order into preparation without changing its version. */
    public void markInPreparation() {
        if (status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed orders can enter preparation");
        }
        status = OrderStatus.IN_PREPARATION;
    }


    /**
     * Confirms an order that is pending confirmation.
     * The repository version remains unchanged.
     */
    public void confirm() {
        if (status != OrderStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException(
                "Only pending orders can be confirmed"
            );
        }
        status = OrderStatus.CONFIRMED;
    }

    /** Marks a pickup order in preparation as ready, with a nonblank pin. */
    public void markReadyForPickup(String pin) {
        if (status != OrderStatus.IN_PREPARATION || fulfillmentType != FulfillmentType.IN_STORE_PICKUP) {
            throw new IllegalStateException("Only pickup orders in preparation can become ready for pickup");
        }
        requireText(pin, "Pickup pin");
        pickupPin = pin;
        status = OrderStatus.READY_FOR_PICKUP;
    }

    /** Cancels a pending, confirmed or preparing order with a nonblank reason. */
    public void cancel(String reason) {
        if (status != OrderStatus.PENDING_CONFIRMATION && status != OrderStatus.CONFIRMED
                && status != OrderStatus.IN_PREPARATION) {
            throw new IllegalStateException("Only pending, confirmed or preparing orders can be cancelled");
        }
        requireText(reason, "Cancellation reason");
        cancellationReason = reason;
        status = OrderStatus.CANCELLED;
    }

    private static BigDecimal nonNegativeAmount(BigDecimal amount, String name) {
        Objects.requireNonNull(amount, name + " cannot be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return amount;
    }

    private static void requireText(String text, String name) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be provided and cannot be blank");
        }
    }

    /** Returns the inherited order UUID. */
    public UUID getOrderId() {
        return getId();
    }

    /** Returns the order number. */
    public String getOrderNumber() {
        return orderNumber;
    }

    /** Returns the associated customer UUID. */
    public UUID getCustomerId() {
        return customerId;
    }

    /** Returns the immutable collection of immutable order lines. */
    public List<OrderItem> getItems() {
        return items;
    }

    /** Returns the subtotal before discount. */
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    /** Returns the discount amount. */
    public BigDecimal getDiscount() {
        return discount;
    }

    /** Returns the total after discount. */
    public BigDecimal getTotal() {
        return total;
    }

    /** Returns the redeemed points. */
    public int getRedeemedPoints() {
        return redeemedPoints;
    }

    /** Returns the current order status. */
    public OrderStatus getStatus() {
        return status;
    }

    /** Returns the fulfillment type. */
    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    /** Returns optional packaging instructions. */
    public String getPackagingInstructions() {
        return packagingInstructions;
    }

    /** Returns optional delivery notes. */
    public String getDeliveryNotes() {
        return deliveryNotes;
    }

    /** Returns the cancellation reason when supplied. */
    public String getCancellationReason() {
        return cancellationReason;
    }

    /** Returns the pickup pin when supplied. */
    public String getPickupPin() {
        return pickupPin;
    }

    /** Returns the repository version, unchanged by lifecycle operations. */
    public long getVersion() {
        return version;
    }
}
