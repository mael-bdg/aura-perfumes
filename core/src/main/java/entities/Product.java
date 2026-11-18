package entities;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** A product with its category, unit price, and optional fragrance profile. */
public class Product extends Entity {
    private final String name;
    private final ProductCategory category;
    private final BigDecimal unitPrice;
    private final FragranceDetails fragranceDetails;

    /**
     * Creates a product with the supplied entity identifier.
     *
     * @param id non-null product UUID
     * @param name non-null, non-blank product name
     * @param category non-null product category
     * @param unitPrice non-null, non-negative unit price
     * @param fragranceDetails fragrance profile, or null when absent
     */
    public Product(UUID id, String name, ProductCategory category,
                   BigDecimal unitPrice, FragranceDetails fragranceDetails) {
        super(id);
        this.name = Objects.requireNonNull(name, "Product name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank");
        }
        this.category = Objects.requireNonNull(category, "Product category cannot be null");
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price cannot be null");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }
        this.fragranceDetails = fragranceDetails;
    }

    /** Returns the inherited product UUID. */
    public UUID getProductId() {
        return getId();
    }

    /** Returns the product name. */
    public String getName() {
        return name;
    }

    /** Returns the product category. */
    public ProductCategory getCategory() {
        return category;
    }

    /** Returns the unit price. */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** Returns the fragrance profile, or null when absent. */
    public FragranceDetails getFragranceDetails() {
        return fragranceDetails;
    }
}
