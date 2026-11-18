package inventory;

import entities.ProductCategory;
import java.util.UUID;

/** Detached inventory read model. Quantity -1 denotes a missing stock record. */
public record InventoryRow(UUID productId, String name, ProductCategory category, int quantity) { }
