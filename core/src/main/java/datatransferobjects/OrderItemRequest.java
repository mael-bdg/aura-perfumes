package datatransferobjects;

import java.util.Objects;
import java.util.UUID;

/** Immutable requested order line. Prices are obtained from the product catalog. */
public final class OrderItemRequest {
    private final UUID productId;
    private final int quantity;

    /** Creates a requested line with a non-null product UUID and positive quantity. */
    public OrderItemRequest(UUID productId, int quantity) {
        this.productId = Objects.requireNonNull(productId, "Product ID is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Product quantity must be positive");
        }
        this.quantity = quantity;
    }

    /** Returns the requested product UUID. */
    public UUID getProductId() {
        return productId;
    }

    /** Returns the requested quantity. */
    public int getQuantity() {
        return quantity;
    }
}
