package usecases;

import entities.Stock;
import java.util.UUID;

/** Gateway for inventory snapshots and optimistic locking. */
public interface StockRepository {
    /** Returns an independent stock snapshot, or null when absent. Null IDs are invalid. */
    Stock findByProductId(UUID productId);

    /**
     * Atomically saves an existing record only when its version matches.
     * Returns false for a missing record or stale version without writing.
     * Successful saves advance the stored version once and leave the supplied
     * snapshot unchanged. Invalid identity, null input, or version overflow
     * throws IllegalArgumentException without writing.
     */
    boolean saveIfVersionMatches(Stock stock);
}
