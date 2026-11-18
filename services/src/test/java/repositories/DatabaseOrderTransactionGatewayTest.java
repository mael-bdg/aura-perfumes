package repositories;

import datatransferobjects.OrderItemRequest;
import datatransferobjects.PlaceOrderRequest;
import datatransferobjects.PlaceOrderResponse;
import entities.Customer;
import entities.FulfillmentType;
import entities.LoyaltyAccount;
import entities.Order;
import entities.OrderItem;
import entities.OrderStatus;
import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import usecases.PlaceOrderInteractor;
import usecases.SynchronizeStockInteractor;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseOrderTransactionGatewayTest {
    private static final UUID CUSTOMER = new UUID(0, 1);
    private static final UUID PRODUCT = new UUID(0, 2);
    private static final UUID SECOND_PRODUCT = new UUID(0, 3);
    private static final UUID FIRST_ORDER = new UUID(0, 4);
    private static final UUID SECOND_ORDER = new UUID(0, 5);
    private final Customer customer = new Customer(CUSTOMER, "Alex", "alex@example.com");
    private DatabaseProductRepository products;
    private DatabaseStockRepository stocks;
    private DatabaseLoyaltyRepository loyalty;
    private DatabaseOrderRepository orders;
    private DatabaseOrderTransactionGateway gateway;
    private PlaceOrderInteractor preparation;
    private final ConfiguredLoyaltyDiscountPolicy policy = new ConfiguredLoyaltyDiscountPolicy(new BigDecimal("0.10"));

    @BeforeEach
    void setUp() {
        products = new DatabaseProductRepository();
        products.addProduct(new Product(PRODUCT, "Aura", ProductCategory.PERFUME, BigDecimal.TEN, null));
        products.addProduct(new Product(SECOND_PRODUCT, "Candle", ProductCategory.SCENTED_CANDLE, BigDecimal.TEN, null));
        stocks = new DatabaseStockRepository(products);
        stocks.addStock(new Stock(new UUID(0, 6), PRODUCT, 10, 4));
        stocks.addStock(new Stock(new UUID(0, 7), SECOND_PRODUCT, 10, 7));
        loyalty = new DatabaseLoyaltyRepository();
        loyalty.addAccount(new LoyaltyAccount(new UUID(0, 8), CUSTOMER, 100, 3));
        orders = new DatabaseOrderRepository();
        gateway = new DatabaseOrderTransactionGateway(stocks, loyalty, orders);
        preparation = new PlaceOrderInteractor(id -> id.equals(CUSTOMER) ? customer : null,
            products, stocks, loyalty::findByCustomerId, policy);
    }

    @Test
    void atomicallyCreatesOrderAndDeductsMultipleProductsAndPoints() {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        quantities.put(PRODUCT, 2);
        quantities.put(SECOND_PRODUCT, 3);
        var plan = plan(FIRST_ORDER, "001", quantities, 30);
        assertTrue(commit(plan));
        assertStock(PRODUCT, 8, 5);
        assertStock(SECOND_PRODUCT, 7, 8);
        assertAccount(70, 4);
        Order stored = orders.findById(FIRST_ORDER);
        assertEquals(1, stored.getVersion());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stored.getStatus());
        assertEquals(0, stored.getTotal().compareTo(new BigDecimal("47")));
        assertEquals(0, plan.order().getVersion());
        assertEquals(4, plan.stocks().getFirst().getVersion());
        assertEquals(3, plan.account().getVersion());
        plan.order().cancel("External change");
        plan.stocks().getFirst().decrease(1);
        plan.account().redeemPoints(1);
        assertEquals(OrderStatus.PENDING_CONFIRMATION, orders.findById(FIRST_ORDER).getStatus());
        assertStock(PRODUCT, 8, 5);
        assertAccount(70, 4);
    }

    @Test
    void zeroRedemptionLeavesLoyaltyUnchangedAndAlsoWorksWithoutAccount() {
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 0);
        assertNull(plan.account());
        assertTrue(commit(plan));
        assertAccount(100, 3);
        DatabaseLoyaltyRepository empty = new DatabaseLoyaltyRepository();
        var another = plan(SECOND_ORDER, "002", Map.of(PRODUCT, 1), 0);
        assertTrue(new DatabaseOrderTransactionGateway(stocks, empty, orders)
            .commitOrder(another.order(), another.stocks(), null));
        assertNull(empty.findByCustomerId(CUSTOMER));
        assertStock(PRODUCT, 8, 6);
    }

    @Test
    void staleStockVersionPreservesAllOtherData() {
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 2, SECOND_PRODUCT, 2), 10);
        Stock newer = stocks.findByProductId(SECOND_PRODUCT);
        newer.decrease(1);
        assertTrue(stocks.saveIfVersionMatches(newer));
        assertFalse(commit(plan));
        assertStock(PRODUCT, 10, 4);
        assertStock(SECOND_PRODUCT, 9, 8);
        assertAccount(100, 3);
        assertNull(orders.findById(FIRST_ORDER));
    }

    @Test
    void staleLoyaltyVersionPreservesAllStockAndOrders() {
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 2), 30);
        LoyaltyAccount newer = loyalty.findByCustomerId(CUSTOMER);
        newer.redeemPoints(5);
        loyalty.save(newer);
        assertFalse(commit(plan));
        assertStock(PRODUCT, 10, 4);
        assertAccount(95, 4);
        assertNull(orders.findById(FIRST_ORDER));
    }

    @Test
    void rejectsDuplicateOrderIdsAndNumbersWithoutAdditionalDeductions() {
        assertTrue(commit(plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 10)));
        assertFalse(commit(plan(FIRST_ORDER, "different-number", Map.of(PRODUCT, 1), 10)));
        assertFalse(commit(plan(SECOND_ORDER, "001", Map.of(PRODUCT, 1), 10)));
        assertStock(PRODUCT, 9, 5);
        assertAccount(90, 4);
        assertEquals("001", orders.findById(FIRST_ORDER).getOrderNumber());
        assertEquals(1, orders.findById(FIRST_ORDER).getVersion());
        assertNull(orders.findById(SECOND_ORDER));
    }

    @Test
    void rejectsInsufficientStockAndForgedDeductionWithoutWrites() {
        Order tooMany = order(FIRST_ORDER, "001", PRODUCT, 11, 0);
        Stock current = stocks.findByProductId(PRODUCT);
        Stock candidate = new Stock(current.getId(), PRODUCT, 0, current.getVersion());
        assertFalse(gateway.commitOrder(tooMany, List.of(candidate), null));
        var valid = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 2), 0);
        assertFalse(gateway.commitOrder(valid.order(), List.of(current), null));
        assertOriginal();
    }

    @Test
    void rejectsInsufficientPointsAndForgedRedemptionWithoutWrites() {
        Order tooManyPoints = order(FIRST_ORDER, "001", PRODUCT, 1, 101);
        Stock current = stocks.findByProductId(PRODUCT);
        current.decrease(1);
        LoyaltyAccount account = loyalty.findByCustomerId(CUSTOMER);
        LoyaltyAccount candidate = new LoyaltyAccount(account.getId(), CUSTOMER, 0, account.getVersion());
        assertFalse(gateway.commitOrder(tooManyPoints, List.of(current), candidate));
        var valid = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 10);
        assertFalse(gateway.commitOrder(valid.order(), valid.stocks(), account));
        assertOriginal();
    }

    @Test
    void rejectsMissingOrMismatchedSnapshotsWithoutWrites() {
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 10);
        assertFalse(gateway.commitOrder(plan.order(), List.of(), plan.account()));
        assertFalse(gateway.commitOrder(plan.order(), plan.stocks(), null));
        assertFalse(gateway.commitOrder(plan.order(), List.of(plan.stocks().getFirst(), plan.stocks().getFirst()), plan.account()));
        Stock wrongIdentity = new Stock(new UUID(0, 99), PRODUCT, 9, 4);
        assertFalse(gateway.commitOrder(plan.order(), List.of(wrongIdentity), plan.account()));
        LoyaltyAccount wrongCustomer = new LoyaltyAccount(plan.account().getId(), new UUID(0, 99), 90, 3);
        assertFalse(gateway.commitOrder(plan.order(), plan.stocks(), wrongCustomer));
        assertOriginal();
    }

    @Test
    void versionExhaustionRejectsWholeTransaction() {
        DatabaseStockRepository exhaustedStock = new DatabaseStockRepository(products);
        exhaustedStock.addStock(new Stock(new UUID(0, 6), PRODUCT, 10, Long.MAX_VALUE));
        Stock candidate = new Stock(new UUID(0, 6), PRODUCT, 9, Long.MAX_VALUE);
        assertFalse(new DatabaseOrderTransactionGateway(exhaustedStock, loyalty, orders)
            .commitOrder(order(FIRST_ORDER, "001", PRODUCT, 1, 0), List.of(candidate), null));
        assertEquals(10, exhaustedStock.findByProductId(PRODUCT).getAvailableQuantity());
        assertEquals(Long.MAX_VALUE, exhaustedStock.findByProductId(PRODUCT).getVersion());
        DatabaseLoyaltyRepository exhaustedLoyalty = new DatabaseLoyaltyRepository();
        exhaustedLoyalty.addAccount(new LoyaltyAccount(new UUID(0, 8), CUSTOMER, 100, Long.MAX_VALUE));
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 10);
        assertFalse(new DatabaseOrderTransactionGateway(stocks, exhaustedLoyalty, orders).commitOrder(plan.order(), plan.stocks(),
            new LoyaltyAccount(new UUID(0, 8), CUSTOMER, 90, Long.MAX_VALUE)));
        assertEquals(100, exhaustedLoyalty.findByCustomerId(CUSTOMER).getPointsBalance());
        assertEquals(Long.MAX_VALUE, exhaustedLoyalty.findByCustomerId(CUSTOMER).getVersion());
        assertOriginal();
    }

    @Test
    void concurrentOrdersCannotOversellSharedStock() throws Exception {
        var first = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 7), 0);
        var second = plan(SECOND_ORDER, "002", Map.of(PRODUCT, 7), 0);
        boolean firstWon = race(() -> commit(first), () -> commit(second));
        assertStock(PRODUCT, 3, 5);
        assertAccount(100, 3);
        assertNotNull(orders.findById(firstWon ? FIRST_ORDER : SECOND_ORDER));
        assertNull(orders.findById(firstWon ? SECOND_ORDER : FIRST_ORDER));
    }

    @Test
    void concurrentRedemptionsForDifferentProductsCannotOverspendPoints() throws Exception {
        var first = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 60);
        var second = plan(SECOND_ORDER, "002", Map.of(SECOND_PRODUCT, 1), 60);
        boolean firstWon = race(() -> commit(first), () -> commit(second));
        assertAccount(40, 4);
        assertStock(PRODUCT, firstWon ? 9 : 10, firstWon ? 5 : 4);
        assertStock(SECOND_PRODUCT, firstWon ? 10 : 9, firstWon ? 7 : 8);
        assertNotNull(orders.findById(firstWon ? FIRST_ORDER : SECOND_ORDER));
        assertNull(orders.findById(firstWon ? SECOND_ORDER : FIRST_ORDER));
    }

    @Test
    void u1SaveAndU2TransactionShareVersionProtection() throws Exception {
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 7), 0);
        Stock u1Snapshot = stocks.findByProductId(PRODUCT);
        u1Snapshot.decrease(2);
        boolean u1Won = race(() -> stocks.saveIfVersionMatches(u1Snapshot), () -> commit(plan));
        assertStock(PRODUCT, u1Won ? 8 : 3, 5);
        assertEquals(u1Won, orders.findById(FIRST_ORDER) == null);
        SynchronizeStockInteractor u1 = new SynchronizeStockInteractor(products, stocks);
        int before = u1.checkStock(PRODUCT);
        assertEquals(before + 1, u1.increaseStock(PRODUCT, 1));
        assertStock(PRODUCT, before + 1, 6);
    }

    @Test
    void formalHandlerPreviewsReadOnlyThenReturnsCommittedOrderIdentity() {
        PlaceOrderInteractor subject = configured(gateway);
        PlaceOrderRequest preview = new PlaceOrderRequest(CUSTOMER, List.of(new OrderItemRequest(PRODUCT, 2)), 30, false);
        assertTrue(subject.handle(preview).isSuccess());
        assertOriginal();
        PlaceOrderResponse response = subject.handle(new PlaceOrderRequest(CUSTOMER, preview.getItems(), 30, true));
        assertTrue(response.isSuccess());
        assertEquals(FIRST_ORDER, response.getOrderId());
        assertEquals(30, response.getRedeemedPoints());
        assertEquals(0, response.getTotal().compareTo(new BigDecimal("17")));
        assertTrue(response.getMessage().contains("committed"));
        assertEquals(FulfillmentType.DELIVERY, orders.findById(FIRST_ORDER).getFulfillmentType());
        assertEquals("Gift wrap", orders.findById(FIRST_ORDER).getPackagingInstructions());
        assertStock(PRODUCT, 8, 5);
        assertAccount(70, 4);
    }

    @Test
    void formalHandlerReturnsFailureForConflictWithoutRetryOrFabricatedIdentity() {
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        PlaceOrderInteractor subject = configured((order, changes, account) -> {
            attempts.incrementAndGet();
            Stock newer = stocks.findByProductId(PRODUCT);
            newer.decrease(1);
            assertTrue(stocks.saveIfVersionMatches(newer));
            return gateway.commitOrder(order, changes, account);
        });
        PlaceOrderResponse response = subject.handle(request(2, 30));
        assertFalse(response.isSuccess());
        assertNull(response.getOrderId());
        assertEquals(0, response.getRedeemedPoints());
        assertEquals(1, attempts.get());
        assertTrue(response.getMessage().contains("conflict"));
        assertStock(PRODUCT, 9, 5);
        assertAccount(100, 3);
        assertNull(orders.findById(FIRST_ORDER));
    }

    @Test
    void unexpectedGatewayFailurePropagatesAndLeavesStateUnchanged() {
        for (RuntimeException failure : new RuntimeException[] {
                new IllegalStateException("Storage unavailable"), new IllegalArgumentException("Unexpected adapter failure")}) {
            PlaceOrderInteractor subject = configured((order, changes, account) -> { throw failure; });
            assertSame(failure, assertThrows(failure.getClass(), () -> subject.handle(request(1, 10))));
            assertOriginal();
        }
    }

    @Test
    void missingPersistedResourcesRejectTransactionWithoutCreatingRecords() {
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 10);
        DatabaseStockRepository emptyStocks = new DatabaseStockRepository(products);
        assertFalse(new DatabaseOrderTransactionGateway(emptyStocks, loyalty, orders)
            .commitOrder(plan.order(), plan.stocks(), plan.account()));
        assertNull(emptyStocks.findByProductId(PRODUCT));
        DatabaseLoyaltyRepository emptyAccounts = new DatabaseLoyaltyRepository();
        assertFalse(new DatabaseOrderTransactionGateway(stocks, emptyAccounts, orders)
            .commitOrder(plan.order(), plan.stocks(), plan.account()));
        assertNull(emptyAccounts.findByCustomerId(CUSTOMER));
        assertOriginal();
    }

    @Test
    void unexpectedSnapshotFailurePropagatesBeforeAnyPublication() {
        var plan = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 1), 10);
        IllegalStateException failure = new IllegalStateException("Invalid external snapshot");
        Stock broken = new Stock(new UUID(0, 6), PRODUCT, 9, 4) {
            @Override
            public long getVersion() {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> gateway.commitOrder(plan.order(), List.of(broken), plan.account())));
        assertOriginal();
    }

    @Test
    void missingConfirmationContextProducesFailureWithoutInventingFulfillment() {
        PlaceOrderInteractor subject = new PlaceOrderInteractor(id -> customer, products, stocks, loyalty, gateway, policy, request -> null);
        PlaceOrderResponse response = subject.handle(request(1, 0));
        assertFalse(response.isSuccess());
        assertNull(response.getOrderId());
        assertTrue(response.getMessage().contains("context is required"));
        assertOriginal();
    }

    @Test
    void separateGatewaysWithOppositeProductOrderShareAtomicVersionProtection() throws Exception {
        Map<UUID, Integer> forward = new LinkedHashMap<>();
        forward.put(PRODUCT, 2);
        forward.put(SECOND_PRODUCT, 3);
        Map<UUID, Integer> reverse = new LinkedHashMap<>();
        reverse.put(SECOND_PRODUCT, 3);
        reverse.put(PRODUCT, 2);
        var first = plan(FIRST_ORDER, "001", forward, 10);
        var second = plan(SECOND_ORDER, "002", reverse, 10);
        var otherGateway = new DatabaseOrderTransactionGateway(stocks, loyalty, orders);
        boolean firstWon = race(() -> commit(first),
            () -> otherGateway.commitOrder(second.order(), second.stocks(), second.account()));
        assertStock(PRODUCT, 8, 5);
        assertStock(SECOND_PRODUCT, 7, 8);
        assertAccount(90, 4);
        assertNotNull(orders.findById(firstWon ? FIRST_ORDER : SECOND_ORDER));
        assertNull(orders.findById(firstWon ? SECOND_ORDER : FIRST_ORDER));
    }

    @Test
    void disjointConcurrentOrdersBothPublishWithoutLosingState() throws Exception {
        var first = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 2), 0);
        var second = plan(SECOND_ORDER, "002", Map.of(SECOND_PRODUCT, 3), 0);
        race(() -> commit(first), () -> commit(second), true);
        assertStock(PRODUCT, 8, 5);
        assertStock(SECOND_PRODUCT, 7, 8);
        assertAccount(100, 3);
        assertEquals(1, orders.findById(FIRST_ORDER).getVersion());
        assertEquals(1, orders.findById(SECOND_ORDER).getVersion());
    }

    @Test
    void standaloneLoyaltySaveAndConfirmationCannotOverwriteEachOther() throws Exception {
        var prepared = plan(FIRST_ORDER, "001", Map.of(PRODUCT, 2), 60);
        LoyaltyAccount competing = loyalty.findByCustomerId(CUSTOMER);
        competing.redeemPoints(60);
        boolean orderWon = race(() -> commit(prepared), () -> {
            try {
                loyalty.save(competing);
                return true;
            } catch (IllegalArgumentException staleVersion) {
                return false;
            }
        });
        assertAccount(40, 4);
        assertStock(PRODUCT, orderWon ? 8 : 10, orderWon ? 5 : 4);
        assertStock(SECOND_PRODUCT, 10, 7);
        assertEquals(orderWon, orders.findById(FIRST_ORDER) != null);
    }

    private PlaceOrderInteractor configured(gateways.OrderTransactionGateway transaction) {
        return new PlaceOrderInteractor(id -> id.equals(CUSTOMER) ? customer : null, products, stocks, loyalty,
            transaction, policy, request -> new PlaceOrderInteractor.ConfirmationContext(FIRST_ORDER, "001",
                FulfillmentType.DELIVERY, "Gift wrap", null));
    }

    private PlaceOrderInteractor.ConfirmationPlan plan(UUID id, String number, Map<UUID, Integer> quantities, int points) {
        return preparation.prepareConfirmation(id, number, CUSTOMER, quantities, points, FulfillmentType.DELIVERY, null, null);
    }

    private boolean commit(PlaceOrderInteractor.ConfirmationPlan plan) {
        return gateway.commitOrder(plan.order(), plan.stocks(), plan.account());
    }

    private static PlaceOrderRequest request(int quantity, int points) {
        return new PlaceOrderRequest(CUSTOMER, List.of(new OrderItemRequest(PRODUCT, quantity)), points, true);
    }

    private static Order order(UUID id, String number, UUID productId, int quantity, int points) {
        OrderItem item = new OrderItem(new UUID(0, 9), productId, quantity, BigDecimal.TEN);
        return new Order(id, number, CUSTOMER, List.of(item), item.getLineTotal(), BigDecimal.ZERO,
            item.getLineTotal(), points, OrderStatus.PENDING_CONFIRMATION, FulfillmentType.DELIVERY, null, null, null, null, 0);
    }

    private void assertOriginal() {
        assertStock(PRODUCT, 10, 4);
        assertStock(SECOND_PRODUCT, 10, 7);
        assertAccount(100, 3);
        assertNull(orders.findById(FIRST_ORDER));
        assertNull(orders.findById(SECOND_ORDER));
    }

    private void assertStock(UUID id, int quantity, long version) {
        Stock stock = stocks.findByProductId(id);
        assertEquals(quantity, stock.getAvailableQuantity());
        assertEquals(version, stock.getVersion());
    }

    private void assertAccount(int points, long version) {
        LoyaltyAccount account = loyalty.findByCustomerId(CUSTOMER);
        assertEquals(points, account.getPointsBalance());
        assertEquals(version, account.getVersion());
    }

    private static boolean race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        return race(first, second, false);
    }

    private static boolean race(Callable<Boolean> first, Callable<Boolean> second, boolean bothSucceed) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return first.call();
            });
            var secondResult = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return second.call();
            });
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            boolean firstWon = firstResult.get(5, TimeUnit.SECONDS);
            boolean secondWon = secondResult.get(5, TimeUnit.SECONDS);
            if (bothSucceed) {
                assertTrue(firstWon);
                assertTrue(secondWon);
            } else {
                assertNotEquals(firstWon, secondWon);
            }
            return firstWon;
        }
    }
}
