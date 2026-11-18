package datatransferobjects;

import java.util.Objects;
import java.util.UUID;

/** Immutable input for a U1 stock operation. */
public final class StockRequest {
    private final UUID productId;
    private final StockOperation operation;
    private final int quantity;

    /**
     * Creates a request with non-null product and operation identifiers.
     * Quantity validation belongs to the business logic. CHECK ignores quantity.
     */
    public StockRequest(UUID productId, StockOperation operation, int quantity) {
        this.productId = Objects.requireNonNull(productId, "Product ID is required");
        this.operation = Objects.requireNonNull(operation, "Stock operation is required");
        this.quantity = quantity;
    }

    /** Returns the requested product UUID. */
    public UUID getProductId() {
        return productId;
    }

    /** Returns the stock operation. */
    public StockOperation getOperation() {
        return operation;
    }

    /** Returns the adjustment quantity, ignored for CHECK. */
    public int getQuantity() {
        return quantity;
    }
}
