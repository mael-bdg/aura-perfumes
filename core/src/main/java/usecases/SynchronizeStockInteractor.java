package usecases;

import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import entities.Product;
import entities.Stock;
import java.util.Objects;
import java.util.UUID;

/** U1 business operations using repository gateways for A3 Steps 20–21. */
public final class SynchronizeStockInteractor implements StockRequestHandler {
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    /** Creates an interactor with product and stock gateways. */
    public SynchronizeStockInteractor(ProductRepository productRepository, StockRepository stockRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "Product repository is required");
        this.stockRepository = Objects.requireNonNull(stockRepository, "Stock repository is required");
    }

    /**
     * Returns business outcomes while propagating unexpected technical failures.
     * Unavailable names are null and unavailable quantities are -1. A conflict
     * also reports -1 because the retrieved snapshot may already be stale.
     * Conflicts are reported without automatically retrying the adjustment.
     */
    @Override
    public StockResponse handle(StockRequest request) {
        if (request == null) {
            return new StockResponse(false, null, null, -1, "Stock request is required");
        }
        String productName = null;
        int availableQuantity = -1;
        try {
            productName = requireProduct(request.getProductId()).getName();
            Stock stock = requireStock(request.getProductId());
            availableQuantity = stock.getAvailableQuantity();
            if (!adjustAndSave(stock, request.getOperation(), request.getQuantity())) {
                return new StockResponse(false, request.getProductId(), productName, -1,
                    "Stock update conflicts with the stored version or the record no longer exists");
            }
            return new StockResponse(true, request.getProductId(), productName,
                stock.getAvailableQuantity(), "Stock operation succeeded: " + request.getOperation());
        } catch (IllegalArgumentException exception) {
            return new StockResponse(false, request.getProductId(), productName,
                availableQuantity, exception.getMessage());
        }
    }

    /** Returns current stock without changing quantity or version. */
    public int checkStock(UUID productId) {
        requireProduct(productId);
        return requireStock(productId).getAvailableQuantity();
    }

    /** Adds a positive quantity and saves once, rejecting conflicts without retry. */
    public int increaseStock(UUID productId, int quantity) {
        return adjust(productId, StockOperation.INCREASE, quantity);
    }

    /** Removes a positive quantity and saves once, rejecting conflicts without retry. */
    public int decreaseStock(UUID productId, int quantity) {
        return adjust(productId, StockOperation.DECREASE, quantity);
    }

    private int adjust(UUID productId, StockOperation operation, int quantity) {
        requireProduct(productId);
        Stock stock = requireStock(productId);
        if (!adjustAndSave(stock, operation, quantity)) {
            throw new IllegalArgumentException("Stock update conflicts with the stored version or the record no longer exists");
        }
        return stock.getAvailableQuantity();
    }

    private boolean adjustAndSave(Stock stock, StockOperation operation, int quantity) {
        switch (operation) {
            case CHECK -> { return true; }
            case INCREASE -> stock.increase(quantity);
            case DECREASE -> stock.decrease(quantity);
        }
        return stockRepository.saveIfVersionMatches(stock);
    }

    private Product requireProduct(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required");
        }
        Product product = productRepository.findById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        return product;
    }

    private Stock requireStock(UUID productId) {
        Stock stock = stockRepository.findByProductId(productId);
        if (stock == null) {
            throw new IllegalArgumentException("Missing stock for product: " + productId);
        }
        return stock;
    }
}
