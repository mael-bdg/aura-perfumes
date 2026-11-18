package gateways;

import entities.LoyaltyAccount;
import entities.Order;
import entities.Stock;
import java.util.List;

/** Atomic order creation, inventory deduction and optional points redemption. */
public interface OrderTransactionGateway {
    /**
     * Commits a new pending version-zero order and detached post-deduction snapshots.
     * Stock/account snapshots carry expected original versions. A null account is
     * required for zero redemption. Returns false for conflicts, insufficient
     * resources, duplicate orders or inconsistent deductions, without any writes.
     * Successful commits advance each modified entity's persisted version once.
     * Unexpected technical failures propagate. No automatic retries occur.
     */
    boolean commitOrder(Order order, List<Stock> stocks, LoyaltyAccount account);
}
