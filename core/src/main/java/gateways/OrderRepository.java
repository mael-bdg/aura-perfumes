package gateways;

import entities.Order;
import java.util.UUID;

/** Order persistence gateway using the UML package and signatures. */
public interface OrderRepository {
    /** Returns an independent order snapshot, or null when absent. */
    Order findById(UUID orderId);

    /**
     * Inserts a new version-zero order or updates a matching-version snapshot.
     * Advances the persisted version once. Invalid identity, duplicate numbers,
     * or version conflicts throw IllegalArgumentException without writing.
     */
    void save(Order order);
}
