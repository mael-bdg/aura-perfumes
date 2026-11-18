package entities;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Immutable order line with inherited identity and a captured unit price. */
public final class OrderItem extends Entity {
    private final UUID productId;
    private final int quantity;
    private final BigDecimal unitPrice;

    /** Creates a line with non-null identifiers, positive quantity and nonnegative price. */
    public OrderItem(UUID id, UUID productId, int quantity, BigDecimal unitPrice) {
        super(id);
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price cannot be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order item quantity must be positive");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }
        this.quantity = quantity;
    }

    /** Returns the associated product UUID. */
    public UUID getProductId() {
        return productId;
    }

    /** Returns the ordered quantity. */
    public int getQuantity() {
        return quantity;
    }

    /** Returns the captured unit price. */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** Calculates the exact line total without rounding or floating-point arithmetic. */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
