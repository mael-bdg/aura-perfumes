package entities;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LoyaltyAccountTest {
    private static final UUID ACCOUNT_ID = new UUID(0, 1);
    private static final UUID CUSTOMER_ID = new UUID(0, 2);

    @Test
    void createsAccountWithInheritedIdentityAndCustomerAssociation() {
        LoyaltyAccount account = new LoyaltyAccount(ACCOUNT_ID, CUSTOMER_ID, 100, 4);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(CUSTOMER_ID, account.getCustomerId());
        assertEquals(100, account.getPointsBalance());
        assertEquals(4, account.getVersion());
        LoyaltyAccount empty = new LoyaltyAccount(ACCOUNT_ID, CUSTOMER_ID, 0, 0);
        assertEquals(0, empty.getPointsBalance());
        assertEquals(0, empty.getVersion());
    }

    @Test
    void rejectsInvalidInitialState() {
        assertThrows(NullPointerException.class, () -> new LoyaltyAccount(null, CUSTOMER_ID, 0, 0));
        assertThrows(NullPointerException.class, () -> new LoyaltyAccount(ACCOUNT_ID, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LoyaltyAccount(ACCOUNT_ID, CUSTOMER_ID, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LoyaltyAccount(ACCOUNT_ID, CUSTOMER_ID, 0, -1));
    }

    @Test
    void redeemsPartialAndFullBalanceWithoutChangingVersion() {
        LoyaltyAccount account = new LoyaltyAccount(ACCOUNT_ID, CUSTOMER_ID, 100, Long.MAX_VALUE);
        account.redeemPoints(30);
        assertEquals(70, account.getPointsBalance());
        assertEquals(Long.MAX_VALUE, account.getVersion());
        account.redeemPoints(70);
        assertEquals(0, account.getPointsBalance());
        assertEquals(Long.MAX_VALUE, account.getVersion());
    }

    @Test
    void rejectsInvalidRedemptionsWithoutChangingBalanceOrVersion() {
        LoyaltyAccount account = new LoyaltyAccount(ACCOUNT_ID, CUSTOMER_ID, 100, 4);
        for (int points : new int[] {0, -1, Integer.MIN_VALUE, 101, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> account.redeemPoints(points));
            assertEquals(100, account.getPointsBalance());
            assertEquals(4, account.getVersion());
        }
        LoyaltyAccount empty = new LoyaltyAccount(ACCOUNT_ID, CUSTOMER_ID, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> empty.redeemPoints(1));
        assertEquals(0, empty.getPointsBalance());
        assertEquals(0, empty.getVersion());
    }
}
