package datatransferobjects;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable U2 outcome using the UUID-corrected UML fields.
 * An absent order UUID means no persisted order is identified. Monetary amounts
 * may be null when validation failed before calculation, and consumers must label
 * those values as unavailable. A non-null message describes the operation outcome.
 */
public final class PlaceOrderResponse {
    private final boolean success;
    private final UUID orderId;
    private final BigDecimal subtotal;
    private final BigDecimal discount;
    private final BigDecimal total;
    private final int redeemedPoints;
    private final String message;

    /** Creates an outcome with optional identity and amounts, and a required message. */
    public PlaceOrderResponse(boolean success, UUID orderId, BigDecimal subtotal, BigDecimal discount,
                              BigDecimal total, int redeemedPoints, String message) {
        this.success = success;
        this.orderId = orderId;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.redeemedPoints = redeemedPoints;
        this.message = Objects.requireNonNull(message, "Order outcome message is required");
    }

    /** Returns whether the requested operation succeeded as described by the message. */
    public boolean isSuccess() {
        return success;
    }

    /** Returns the persisted order UUID when available. */
    public UUID getOrderId() {
        return orderId;
    }

    /** Returns the calculated subtotal, or null when unavailable. */
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    /** Returns the calculated discount, or null when unavailable. */
    public BigDecimal getDiscount() {
        return discount;
    }

    /** Returns the calculated total, or null when unavailable. */
    public BigDecimal getTotal() {
        return total;
    }

    /** Returns the points represented by this outcome, including projected preview redemption. */
    public int getRedeemedPoints() {
        return redeemedPoints;
    }

    /** Returns the non-null outcome message. */
    public String getMessage() {
        return message;
    }
}
