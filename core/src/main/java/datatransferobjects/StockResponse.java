package datatransferobjects;

import java.util.UUID;

/**
 * Immutable outcome of a U1 stock operation.
 * Handler failures use null for an unavailable product name and -1 for an
 * unavailable quantity. A null request also produces a null product UUID.
 * A rejected adjustment reports the known, unchanged quantity when available.
 * Version conflicts report -1 because the retrieved quantity may be stale.
 */
public final class StockResponse {
    private final boolean success;
    private final UUID productId;
    private final String productName;
    private final int availableQuantity;
    private final String message;

    /** Creates a response containing the supplied outcome and available context. */
    public StockResponse(boolean success, UUID productId, String productName,
                         int availableQuantity, String message) {
        this.success = success;
        this.productId = productId;
        this.productName = productName;
        this.availableQuantity = availableQuantity;
        this.message = message;
    }

    /** Returns whether the operation succeeded. */
    public boolean isSuccess() {
        return success;
    }

    /** Returns the product UUID, or null for a null request. */
    public UUID getProductId() {
        return productId;
    }

    /** Returns the product name, or null when unavailable. */
    public String getProductName() {
        return productName;
    }

    /** Returns the available quantity, or -1 when unavailable. */
    public int getAvailableQuantity() {
        return availableQuantity;
    }

    /** Returns the operation outcome message. */
    public String getMessage() {
        return message;
    }
}
