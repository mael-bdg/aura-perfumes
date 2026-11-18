package repositories;

import entities.Stock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import usecases.ProductRepository;
import usecases.StockRepository;

/**
 * Temporary in-memory stock adapter using the finalized UML Database name.
 * Synchronized saves atomically compare versions and replace defensive copies.
 */
public final class DatabaseStockRepository implements StockRepository {
    private final ProductRepository products;
    private Map<UUID, Stock> stocks = new HashMap<>();

    /** Creates storage associated with the supplied product catalog. */
    public DatabaseStockRepository(ProductRepository products) {
        this.products = Objects.requireNonNull(products, "Product repository is required");
    }

    /** Registers one stock record per existing product with a unique stock UUID. */
    public synchronized void addStock(Stock stock) {
        if (stock == null) {
            throw new IllegalArgumentException("Stock is required");
        }
        if (products.findById(stock.getProductId()) == null) {
            throw new IllegalArgumentException("Unknown product: " + stock.getProductId());
        }
        if (stocks.containsKey(stock.getProductId())) {
            throw new IllegalArgumentException("Stock already exists for product: " + stock.getProductId());
        }
        for (Stock existing : stocks.values()) {
            if (existing.getId().equals(stock.getId())) {
                throw new IllegalArgumentException("Stock ID already exists: " + stock.getId());
            }
        }
        stocks.put(stock.getProductId(), copy(stock));
    }

    /** Returns an independent stock snapshot, or null when absent. */
    @Override
    public synchronized Stock findByProductId(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required");
        }
        Stock stock = stocks.get(productId);
        return stock == null ? null : copy(stock);
    }

    /** Atomically saves a matching snapshot and advances only its stored version. */
    @Override
    public synchronized boolean saveIfVersionMatches(Stock stock) {
        if (stock == null) {
            throw new IllegalArgumentException("Stock is required");
        }
        Stock current = stocks.get(stock.getProductId());
        if (current == null) {
            return false;
        }
        if (!current.getId().equals(stock.getId())) {
            throw new IllegalArgumentException("Stock ID does not match the registered product stock");
        }
        if (current.getVersion() != stock.getVersion()) {
            return false;
        }
        if (current.getVersion() == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Stock version cannot be increased beyond Long.MAX_VALUE");
        }
        Stock updated = new Stock(current.getId(), current.getProductId(),
            stock.getAvailableQuantity(), current.getVersion() + 1);
        stocks.put(current.getProductId(), updated);
        return true;
    }

    /** Stages a root map while the transaction holds this repository's monitor. */
    synchronized Map<UUID, Stock> transactionSnapshot() {
        return new HashMap<>(stocks);
    }

    /** Publishes a fully staged root while all participating monitors are held. */
    synchronized void publishTransaction(Map<UUID, Stock> replacement) {
        stocks = replacement;
    }

    private static Stock copy(Stock stock) {
        return new Stock(stock.getId(), stock.getProductId(),
            stock.getAvailableQuantity(), stock.getVersion());
    }
}
