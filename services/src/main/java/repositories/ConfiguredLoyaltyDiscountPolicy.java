package repositories;

import java.math.BigDecimal;
import java.util.Objects;
import usecases.LoyaltyDiscountPolicy;

/**
 * Exact points conversion using an explicitly supplied monetary amount per point.
 * The real business conversion rate remains to be agreed upon. No default rate
 * or rounding rule is assumed. The interactor enforces the subtotal limit.
 */
public final class ConfiguredLoyaltyDiscountPolicy implements LoyaltyDiscountPolicy {
    private final BigDecimal amountPerPoint;

    /** Creates a policy with a non-null, nonnegative monetary amount per point. */
    public ConfiguredLoyaltyDiscountPolicy(BigDecimal amountPerPoint) {
        this.amountPerPoint = Objects.requireNonNull(amountPerPoint, "Amount per point is required");
        if (amountPerPoint.signum() < 0) {
            throw new IllegalArgumentException("Amount per point cannot be negative");
        }
    }

    /** Calculates the exact discount without modifying points or rounding money. */
    @Override
    public BigDecimal calculateDiscount(int points) {
        if (points < 0) {
            throw new IllegalArgumentException("Redeemed points cannot be negative");
        }
        return amountPerPoint.multiply(BigDecimal.valueOf(points));
    }
}
