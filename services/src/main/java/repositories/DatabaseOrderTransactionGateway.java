package repositories;

import entities.LoyaltyAccount;
import entities.Order;
import entities.OrderItem;
import entities.OrderStatus;
import entities.Stock;
import gateways.OrderTransactionGateway;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Temporary in-memory transaction adapter. Acquires monitors in the fixed order
 * stock, loyalty, orders, including the same stock monitor used by U1 saves.
 * Validates and allocates all replacement state before publishing map references.
 * Publication performs only reference assignments while every reader/writer is
 * excluded by its repository monitor. No repository callback runs during publication.
 */
public final class DatabaseOrderTransactionGateway implements OrderTransactionGateway {
    private final DatabaseStockRepository stocks;
    private final DatabaseLoyaltyRepository loyalty;
    private final DatabaseOrderRepository orders;

    /** Coordinates exactly the repository instances supplied to application use cases. */
    public DatabaseOrderTransactionGateway(DatabaseStockRepository stocks, DatabaseLoyaltyRepository loyalty,
                                           DatabaseOrderRepository orders) {
        this.stocks = Objects.requireNonNull(stocks, "Stock repository is required");
        this.loyalty = Objects.requireNonNull(loyalty, "Loyalty repository is required");
        this.orders = Objects.requireNonNull(orders, "Order repository is required");
    }

    /** Commits all prepared deductions once, or returns false without publishing changes. */
    @Override
    public boolean commitOrder(Order order, List<Stock> proposedStocks, LoyaltyAccount account) {
        Objects.requireNonNull(order, "Order is required");
        Objects.requireNonNull(proposedStocks, "Stock snapshots are required");
        Order snapshot = DatabaseOrderRepository.copy(order, order.getVersion());
        Map<UUID, Stock> proposed = new HashMap<>();
        for (Stock stock : proposedStocks) {
            Objects.requireNonNull(stock, "Stock snapshot is required");
            Stock copy = new Stock(stock.getId(), stock.getProductId(), stock.getAvailableQuantity(), stock.getVersion());
            if (proposed.put(copy.getProductId(), copy) != null) {
                return false;
            }
        }
        LoyaltyAccount accountSnapshot = account == null ? null : DatabaseLoyaltyRepository.copy(account, account.getVersion());
        synchronized (stocks) {
            synchronized (loyalty) {
                synchronized (orders) {
                    return commitLocked(snapshot, proposed, accountSnapshot);
                }
            }
        }
    }

    private boolean commitLocked(Order order, Map<UUID, Stock> proposed, LoyaltyAccount account) {
        if (order.getStatus() != OrderStatus.PENDING_CONFIRMATION || order.getVersion() != 0) {
            return false;
        }
        Map<UUID, Order> nextOrders = orders.transactionSnapshot();
        if (nextOrders.containsKey(order.getOrderId())) {
            return false;
        }
        for (Order existing : nextOrders.values()) {
            if (existing.getOrderNumber().equals(order.getOrderNumber())) {
                return false;
            }
        }
        Map<UUID, Integer> quantities = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            int previous = quantities.getOrDefault(item.getProductId(), 0);
            if (item.getQuantity() > Integer.MAX_VALUE - previous) {
                return false;
            }
            quantities.put(item.getProductId(), previous + item.getQuantity());
        }
        if (!quantities.keySet().equals(proposed.keySet())) {
            return false;
        }
        Map<UUID, Stock> nextStocks = stocks.transactionSnapshot();
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Stock current = nextStocks.get(entry.getKey());
            Stock candidate = proposed.get(entry.getKey());
            if (current == null || !current.getId().equals(candidate.getId())
                    || current.getVersion() != candidate.getVersion() || current.getVersion() == Long.MAX_VALUE
                    || current.getAvailableQuantity() < entry.getValue()
                    || current.getAvailableQuantity() - entry.getValue() != candidate.getAvailableQuantity()) {
                return false;
            }
            nextStocks.put(entry.getKey(), new Stock(current.getId(), current.getProductId(),
                candidate.getAvailableQuantity(), current.getVersion() + 1));
        }
        Map<UUID, LoyaltyAccount> nextAccounts = loyalty.transactionSnapshot();
        if (order.getRedeemedPoints() == 0) {
            if (account != null) {
                return false;
            }
        } else {
            LoyaltyAccount current = nextAccounts.get(order.getCustomerId());
            if (account == null || current == null || !order.getCustomerId().equals(account.getCustomerId())
                    || !current.getId().equals(account.getId()) || current.getVersion() != account.getVersion()
                    || current.getVersion() == Long.MAX_VALUE || current.getPointsBalance() < order.getRedeemedPoints()
                    || current.getPointsBalance() - order.getRedeemedPoints() != account.getPointsBalance()) {
                return false;
            }
            nextAccounts.put(order.getCustomerId(), DatabaseLoyaltyRepository.copy(account, current.getVersion() + 1));
        }
        nextOrders.put(order.getOrderId(), DatabaseOrderRepository.copy(order, 1));
        // All validation, entity construction and map allocation have completed.
        stocks.publishTransaction(nextStocks);
        loyalty.publishTransaction(nextAccounts);
        orders.publishTransaction(nextOrders);
        return true;
    }
}
