package entities;

import java.util.Objects;
import java.util.UUID;

/** Shared product inventory with a repository-managed optimistic-lock version. */
public class Stock extends Entity {
    private final UUID productId;
    private int availableQuantity;
    private final long version;

    /**
     * Creates a stock record from its persisted state.
     *
     * @param id non-null stock UUID
     * @param productId non-null product UUID
     * @param availableQuantity non-negative initial quantity
     * @param version non-negative repository version
     */
    public Stock(UUID id, UUID productId, int availableQuantity, long version) {
        super(id);
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("Available quantity cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }
        this.availableQuantity = availableQuantity;
        this.version = version;
    }

    /** Returns the associated product UUID. */
    public UUID getProductId() {
        return productId;
    }

    /** Returns the available quantity. */
    public int getAvailableQuantity() {
        return availableQuantity;
    }

    /** Returns the repository version, unchanged by quantity adjustments. */
    public long getVersion() {
        return version;
    }

    /**
     * Adds a positive quantity without changing the repository version.
     *
     * @throws IllegalArgumentException if quantity is non-positive or would overflow
     */
    public void increase(int quantity) {
        if (quantity <= 0 || quantity > Integer.MAX_VALUE - availableQuantity) {
            throw new IllegalArgumentException("Increase must be positive and fit in an integer");
        }
        availableQuantity += quantity;
    }

    /**
     * Removes a positive quantity without changing the repository version.
     *
     * @throws IllegalArgumentException if quantity is non-positive or exceeds stock
     */
    public void decrease(int quantity) {
        if (quantity <= 0 || quantity > availableQuantity) {
            throw new IllegalArgumentException("Decrease must be positive and cannot exceed stock");
        }
        availableQuantity -= quantity;
    }
}
