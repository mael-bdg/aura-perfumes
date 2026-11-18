package repositories;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguredLoyaltyDiscountPolicyTest {
    @Test
    void calculatesExactDiscountFromExplicitTestConfiguration() {
        ConfiguredLoyaltyDiscountPolicy policy = new ConfiguredLoyaltyDiscountPolicy(new BigDecimal("0.0125"));
        assertEquals(new BigDecimal("0.0375"), policy.calculateDiscount(3));
        assertEquals(new BigDecimal("26843545.5875"), policy.calculateDiscount(Integer.MAX_VALUE));
    }

    @Test
    void zeroPointsAndZeroConfiguredRateProduceZeroDiscount() {
        ConfiguredLoyaltyDiscountPolicy policy = new ConfiguredLoyaltyDiscountPolicy(new BigDecimal("0.10"));
        assertEquals(0, policy.calculateDiscount(0).signum());
        assertEquals(BigDecimal.ZERO, new ConfiguredLoyaltyDiscountPolicy(BigDecimal.ZERO).calculateDiscount(100));
    }

    @Test
    void rejectsMissingOrNegativeConfigurationAndNegativeRedemption() {
        assertThrows(NullPointerException.class, () -> new ConfiguredLoyaltyDiscountPolicy(null));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguredLoyaltyDiscountPolicy(new BigDecimal("-0.01")));
        ConfiguredLoyaltyDiscountPolicy policy = new ConfiguredLoyaltyDiscountPolicy(new BigDecimal("0.10"));
        assertThrows(IllegalArgumentException.class, () -> policy.calculateDiscount(-1));
    }
}
