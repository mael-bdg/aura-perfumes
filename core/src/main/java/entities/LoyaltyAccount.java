package entities;

import java.util.Objects;
import java.util.UUID;

/** Customer points balance with a repository-managed optimistic-lock version. */
public final class LoyaltyAccount extends Entity {
    private final UUID customerId;
    private int pointsBalance;
    private final long version;

    /** Creates an account with non-null UUIDs, a nonnegative balance and version. */
    public LoyaltyAccount(UUID id, UUID customerId, int pointsBalance, long version) {
        super(id);
        this.customerId = Objects.requireNonNull(customerId, "Customer ID cannot be null");
        if (pointsBalance < 0) {
            throw new IllegalArgumentException("Points balance cannot be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }
        this.pointsBalance = pointsBalance;
        this.version = version;
    }

    /** Returns the associated customer UUID. */
    public UUID getCustomerId() {
        return customerId;
    }

    /** Returns the available points. */
    public int getPointsBalance() {
        return pointsBalance;
    }

    /** Returns the repository version, unchanged by redemption. */
    public long getVersion() {
        return version;
    }

    /**
     * Redeems a positive amount without changing the repository version.
     *
     * @throws IllegalArgumentException if points are nonpositive or exceed the balance
     */
    public void redeemPoints(int points) {
        if (points <= 0 || points > pointsBalance) {
            throw new IllegalArgumentException("Redemption must be positive and cannot exceed the points balance");
        }
        pointsBalance -= points;
    }
}
