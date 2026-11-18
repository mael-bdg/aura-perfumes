package inventory;

import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryInventoryQueryTest {
    @Test
    void mapsCatalogAndMissingStockIntoDetachedSortedReadModels() {
        var products = new DatabaseProductRepository();
        var stocks = new DatabaseStockRepository(products);
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        products.addProduct(new Product(second, "Zebra", ProductCategory.GIFT_BOX, BigDecimal.ONE, null));
        products.addProduct(new Product(first, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null));
        stocks.addStock(new Stock(new UUID(0, 3), first, 10, 4));
        var query = new RepositoryInventoryQuery(products, stocks);
        var rows = query.load();
        assertEquals(new InventoryRow(first, "Aura", ProductCategory.PERFUME, 10), rows.getFirst());
        assertEquals(new InventoryRow(second, "Zebra", ProductCategory.GIFT_BOX, -1), rows.getLast());
        assertThrows(UnsupportedOperationException.class, rows::clear);
        Stock changed = stocks.findByProductId(first);
        changed.decrease(3);
        assertTrue(stocks.saveIfVersionMatches(changed));
        assertEquals(10, rows.getFirst().quantity());
        assertEquals(7, query.load().getFirst().quantity());
        assertEquals(5, stocks.findByProductId(first).getVersion());
    }

    @Test
    void catalogSnapshotIsReadOnlyAndDoesNotHideNewSessionProducts() {
        var products = new DatabaseProductRepository();
        var snapshot = products.findAll();
        assertTrue(snapshot.isEmpty());
        products.addProduct(new Product(new UUID(0, 1), "Aura", ProductCategory.PERFUME, BigDecimal.ONE, null));
        assertTrue(snapshot.isEmpty());
        assertEquals(1, products.findAll().size());
        assertThrows(UnsupportedOperationException.class, () -> products.findAll().clear());
    }

    @Test
    void u2TransactionAndGuiU1ReadAndModifyTheSameStockInstance() {
        UUID productId = new UUID(0, 1);
        UUID customerId = new UUID(0, 2);
        var products = new DatabaseProductRepository();
        var stocks = new DatabaseStockRepository(products);
        products.addProduct(new Product(productId, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null));
        stocks.addStock(new Stock(new UUID(0, 3), productId, 10, 0));
        var loyalty = new repositories.DatabaseLoyaltyRepository();
        var orders = new repositories.DatabaseOrderRepository();
        var placement = new usecases.PlaceOrderInteractor(
            id -> new entities.Customer(customerId, "Customer", "customer@example.com"),
            products, stocks, id -> null, points -> BigDecimal.ZERO);
        var plan = placement.prepareConfirmation(new UUID(0, 4), "TEST-001", customerId,
            java.util.Map.of(productId, 2), 0, entities.FulfillmentType.DELIVERY, null, null);
        var transaction = new repositories.DatabaseOrderTransactionGateway(stocks, loyalty, orders);
        assertTrue(transaction.commitOrder(plan.order(), plan.stocks(), plan.account()));
        var service = new gui.stock.StockDashboardService(new RepositoryInventoryQuery(products, stocks),
            new usecases.SynchronizeStockInteractor(products, stocks), Runnable::run);
        assertEquals(8, service.refresh().join().getFirst().quantity());
        assertTrue(service.execute(new datatransferobjects.StockRequest(productId,
            datatransferobjects.StockOperation.INCREASE, 1)).join().response().isSuccess());
        assertEquals(9, stocks.findByProductId(productId).getAvailableQuantity());
        assertEquals(2, stocks.findByProductId(productId).getVersion());
        assertEquals(1, orders.findById(plan.order().getOrderId()).getVersion());
    }
}
