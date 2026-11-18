package application;

import entities.Customer;
import entities.LoyaltyAccount;
import java.math.BigDecimal;
import java.util.UUID;
import repositories.ConfiguredLoyaltyDiscountPolicy;
import repositories.DatabaseLoyaltyRepository;
import usecases.LoyaltyDiscountPolicy;

/** Existing console demonstration customer and explicitly unapproved business rate. */
public final class DemoOrderData {
    public static final UUID CUSTOMER_ID = new UUID(0, 100);
    public static final BigDecimal AMOUNT_PER_POINT = new BigDecimal("0.10");

    private DemoOrderData() { }

    public static Customer customer() {
        return new Customer(CUSTOMER_ID, "Demo Customer", "demo@example.com");
    }

    public static void populate(DatabaseLoyaltyRepository loyalty) {
        loyalty.addAccount(new LoyaltyAccount(new UUID(0, 101), CUSTOMER_ID, 100, 0));
    }

    public static LoyaltyDiscountPolicy discountPolicy() {
        return new ConfiguredLoyaltyDiscountPolicy(AMOUNT_PER_POINT);
    }
}
