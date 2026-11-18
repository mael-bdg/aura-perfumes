package usecases;

import java.math.BigDecimal;

/** Converts redeemed points to money without changing a loyalty account. */
public interface LoyaltyDiscountPolicy {
    /**
     * Calculates a nonnegative discount for nonnegative points.
     * The caller must check that the discount does not exceed its order subtotal.
     */
    BigDecimal calculateDiscount(int points);
}
