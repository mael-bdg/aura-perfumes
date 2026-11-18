package gateways;

import entities.LoyaltyAccount;
import java.util.UUID;

/** Loyalty persistence gateway using the UML package and signatures. */
public interface LoyaltyRepository {
    /** Returns an independent account snapshot, or null when absent. */
    LoyaltyAccount findByCustomerId(UUID customerId);

    /**
     * Saves an existing matching-version account and advances its stored version.
     * Missing records, changed identity, stale versions and version overflow throw
     * IllegalArgumentException without writing or modifying the supplied snapshot.
     */
    void save(LoyaltyAccount account);
}
