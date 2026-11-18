package repositories;

import entities.Product;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import usecases.ProductRepository;

/** Temporary in-memory product adapter using the finalized UML Database name. */
public final class DatabaseProductRepository implements ProductRepository {
    private final Map<UUID, Product> products = new HashMap<>();

    /** Registers a product, rejecting null products and duplicate identifiers. */
    public synchronized void addProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product is required");
        }
        if (products.containsKey(product.getProductId())) {
            throw new IllegalArgumentException("Product already exists: " + product.getProductId());
        }
        products.put(product.getProductId(), product);
    }

    /** Returns a product, or null when absent. */
    @Override
    public synchronized Product findById(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required");
        }
        return products.get(productId);
    }

    /** Returns a read-only snapshot of this session's catalog. */
    public synchronized List<Product> findAll() {
        return List.copyOf(products.values());
    }
}
