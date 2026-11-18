package usecases;

import entities.Product;
import java.util.UUID;

/** Gateway for product lookup. */
public interface ProductRepository {
    /** Returns the product, or null when absent. Null identifiers are invalid. */
    Product findById(UUID productId);
}
