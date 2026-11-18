package gateways;

import entities.Order;

import java.util.UUID;

public interface BoutiqueOrderRepository {

    // Find an existing order by its ID
    Order findById(UUID orderId);

    // Save an updated order only if its version has not changed
    boolean updateIfVersionMatches(
            Order order,
            long expectedVersion
    );
}

