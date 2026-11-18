package repositories;

import entities.LoyaltyAccount;
import gateways.LoyaltyRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Temporary in-memory loyalty adapter using the UML Database name. */
public final class DatabaseLoyaltyRepository implements LoyaltyRepository {
    private Map<UUID, LoyaltyAccount> accounts = new HashMap<>();

    /** Registers an initial account snapshot with unique customer and account IDs. */
    public synchronized void addAccount(LoyaltyAccount account) {
        Objects.requireNonNull(account, "Loyalty account is required");
        if (accounts.containsKey(account.getCustomerId())) {
            throw new IllegalArgumentException("Loyalty account already exists for customer");
        }
        for (LoyaltyAccount existing : accounts.values()) {
            if (existing.getId().equals(account.getId())) {
                throw new IllegalArgumentException("Loyalty account ID already exists");
            }
        }
        accounts.put(account.getCustomerId(), copy(account, account.getVersion()));
    }

    /** Returns a defensive account snapshot, or null when absent. */
    @Override
    public synchronized LoyaltyAccount findByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "Customer ID is required");
        LoyaltyAccount account = accounts.get(customerId);
        return account == null ? null : copy(account, account.getVersion());
    }

    /** Saves an existing account only when identity and version match. */
    @Override
    public synchronized void save(LoyaltyAccount account) {
        Objects.requireNonNull(account, "Loyalty account is required");
        LoyaltyAccount snapshot = copy(account, account.getVersion());
        LoyaltyAccount current = accounts.get(snapshot.getCustomerId());
        if (current == null || !current.getId().equals(snapshot.getId())
                || current.getVersion() != snapshot.getVersion()) {
            throw new IllegalArgumentException("Loyalty account identity or version conflicts with stored state");
        }
        if (current.getVersion() == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Loyalty version cannot exceed Long.MAX_VALUE");
        }
        accounts.put(snapshot.getCustomerId(), copy(snapshot, current.getVersion() + 1));
    }

    synchronized Map<UUID, LoyaltyAccount> transactionSnapshot() {
        return new HashMap<>(accounts);
    }

    synchronized void publishTransaction(Map<UUID, LoyaltyAccount> replacement) {
        accounts = replacement;
    }

    static LoyaltyAccount copy(LoyaltyAccount account, long version) {
        return new LoyaltyAccount(account.getId(), account.getCustomerId(), account.getPointsBalance(), version);
    }
}
