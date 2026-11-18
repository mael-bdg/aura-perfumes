package repositories;

import entities.LoyaltyAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseLoyaltyRepositoryTest {
    private static final UUID CUSTOMER = new UUID(0, 1);
    private static final UUID ACCOUNT = new UUID(0, 2);

    @Test
    void registrationLookupAndSaveUseDefensiveCopiesAndAdvanceVersionOnce() {
        DatabaseLoyaltyRepository repository = new DatabaseLoyaltyRepository();
        LoyaltyAccount supplied = new LoyaltyAccount(ACCOUNT, CUSTOMER, 100, 3);
        assertNull(repository.findByCustomerId(CUSTOMER));
        repository.addAccount(supplied);
        supplied.redeemPoints(1);
        LoyaltyAccount retrieved = repository.findByCustomerId(CUSTOMER);
        assertEquals(100, retrieved.getPointsBalance());
        retrieved.redeemPoints(20);
        assertEquals(100, repository.findByCustomerId(CUSTOMER).getPointsBalance());
        repository.save(retrieved);
        assertEquals(3, retrieved.getVersion());
        assertEquals(4, repository.findByCustomerId(CUSTOMER).getVersion());
        retrieved.redeemPoints(1);
        assertEquals(80, repository.findByCustomerId(CUSTOMER).getPointsBalance());
        assertThrows(IllegalArgumentException.class, () -> repository.save(retrieved));
        assertEquals(80, repository.findByCustomerId(CUSTOMER).getPointsBalance());
        assertEquals(4, repository.findByCustomerId(CUSTOMER).getVersion());
    }

    @Test
    void rejectsMissingChangedIdentityAndDuplicateAccounts() {
        DatabaseLoyaltyRepository repository = new DatabaseLoyaltyRepository();
        LoyaltyAccount account = new LoyaltyAccount(ACCOUNT, CUSTOMER, 100, 3);
        assertThrows(IllegalArgumentException.class, () -> repository.save(account));
        repository.addAccount(account);
        assertThrows(IllegalArgumentException.class, () -> repository.addAccount(account));
        assertThrows(IllegalArgumentException.class,
            () -> repository.addAccount(new LoyaltyAccount(ACCOUNT, new UUID(0, 99), 10, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> repository.save(new LoyaltyAccount(new UUID(0, 98), CUSTOMER, 50, 3)));
        assertEquals(100, repository.findByCustomerId(CUSTOMER).getPointsBalance());
        assertEquals(3, repository.findByCustomerId(CUSTOMER).getVersion());
    }

    @Test
    void rejectsVersionOverflowWithoutChangingAccount() {
        DatabaseLoyaltyRepository repository = new DatabaseLoyaltyRepository();
        repository.addAccount(new LoyaltyAccount(ACCOUNT, CUSTOMER, 100, Long.MAX_VALUE));
        LoyaltyAccount candidate = repository.findByCustomerId(CUSTOMER);
        candidate.redeemPoints(1);
        assertThrows(IllegalArgumentException.class, () -> repository.save(candidate));
        assertEquals(100, repository.findByCustomerId(CUSTOMER).getPointsBalance());
        assertEquals(Long.MAX_VALUE, repository.findByCustomerId(CUSTOMER).getVersion());
    }
}
