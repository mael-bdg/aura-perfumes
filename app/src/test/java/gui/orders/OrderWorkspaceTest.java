package gui.orders;

import application.DemoInventory;
import application.DemoOrderData;
import datatransferobjects.PlaceOrderResponse;
import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import entities.FulfillmentType;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import repositories.DatabaseLoyaltyRepository;
import repositories.DatabaseOrderRepository;
import repositories.DatabaseOrderTransactionGateway;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import usecases.SynchronizeStockInteractor;
import static org.junit.jupiter.api.Assertions.*;

class OrderWorkspaceTest {
    private static final UUID PRODUCT = new UUID(0, 1);
    private static final UUID CANDLE = new UUID(0, 2);

    @Test
    void mapsActualCatalogAndLoyaltyWithoutLeakingMutableEntities() {
        Fixture fixture = new Fixture(Runnable::run);
        var before = fixture.workspace.refresh().join().data();
        assertEquals(new BigDecimal("79.90"), before.products().getFirst().unitPrice());
        assertEquals(10, before.products().getFirst().available());
        assertEquals(100, before.customers().getFirst().points());
        fixture.u1.handle(new StockRequest(PRODUCT, StockOperation.DECREASE, 3));
        var account = fixture.loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID);
        account.redeemPoints(5);
        fixture.loyalty.save(account);
        var after = fixture.query.load();
        assertEquals(7, after.products().getFirst().available());
        assertEquals(95, after.customers().getFirst().points());
        assertEquals(10, before.products().getFirst().available());
        assertEquals(100, before.customers().getFirst().points());
        assertThrows(UnsupportedOperationException.class, before.products()::clear);
    }

    @Test
    void previewsThroughU2WithExactDiscountAndNoPersistentSideEffects() {
        Fixture fixture = ready();
        fixture.workspace.setPointsText("30");
        var state = fixture.workspace.preview().join();
        assertTrue(state.preview().response().isSuccess());
        assertEquals(new BigDecimal("159.80"), state.preview().response().getSubtotal());
        assertEquals(0, new BigDecimal("3.00").compareTo(state.preview().response().getDiscount()));
        assertEquals(0, new BigDecimal("156.80").compareTo(state.preview().response().getTotal()));
        fixture.assertOriginal();
        assertEquals(0, fixture.identities.get());
        assertTrue(state.canConfirm());
    }

    @Test
    void confirmationPersistsPickupAndSynchronizesU1AndLoyaltyThenClearsCart() {
        Fixture fixture = ready();
        fixture.workspace.selectFulfillment(FulfillmentType.IN_STORE_PICKUP);
        fixture.workspace.setPointsText("30");
        fixture.workspace.preview().join();
        var state = fixture.workspace.confirm().join();
        UUID id = state.receipt().response().getOrderId();
        assertTrue(state.outcome().isSuccess());
        assertEquals(FulfillmentType.IN_STORE_PICKUP, fixture.orders.findById(id).getFulfillmentType());
        assertEquals(1, fixture.orders.findById(id).getVersion());
        assertEquals(8, fixture.u1.handle(new StockRequest(PRODUCT, StockOperation.CHECK, 0)).getAvailableQuantity());
        assertEquals(70, fixture.loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getPointsBalance());
        assertEquals(1, fixture.stocks.findByProductId(PRODUCT).getVersion());
        assertEquals(1, fixture.loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getVersion());
        assertTrue(state.cart().isEmpty());
        assertEquals("0", state.pointsText());
        assertEquals(8, state.data().products().getFirst().available());
        assertEquals(70, state.data().customers().getFirst().points());
        assertFalse(state.canConfirm());
    }

    @Test
    void deliveryAndPickupAreBoundIndependentlyToTheirActualPersistedOrders() {
        Fixture fixture = ready();
        fixture.workspace.preview().join();
        var first = fixture.workspace.confirm().join().receipt();
        fixture.workspace.add(PRODUCT, 1);
        fixture.workspace.selectFulfillment(FulfillmentType.IN_STORE_PICKUP);
        fixture.workspace.preview().join();
        var second = fixture.workspace.confirm().join().receipt();
        assertNotEquals(first.response().getOrderId(), second.response().getOrderId());
        assertEquals(FulfillmentType.DELIVERY, fixture.orders.findById(first.response().getOrderId()).getFulfillmentType());
        assertEquals(FulfillmentType.IN_STORE_PICKUP, fixture.orders.findById(second.response().getOrderId()).getFulfillmentType());
    }

    @Test
    void stockChangedByU1AfterPreviewCausesFailureWithoutPartialWrites() {
        Fixture fixture = ready();
        fixture.workspace.setPointsText("30");
        fixture.workspace.preview().join();
        fixture.u1.handle(new StockRequest(PRODUCT, StockOperation.DECREASE, 9));
        var state = fixture.workspace.confirm().join();
        assertFalse(state.outcome().isSuccess());
        assertTrue(state.outcome().getMessage().contains("Insufficient stock"));
        assertEquals(2, state.cart().getFirst().quantity());
        assertEquals("30", state.pointsText());
        assertEquals(1, fixture.stocks.findByProductId(PRODUCT).getAvailableQuantity());
        assertEquals(100, fixture.loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getPointsBalance());
        assertNull(fixture.orders.findById(new UUID(1, 1)));
        assertNull(state.receipt());
        assertNull(state.preview());
    }

    @Test
    void pointsChangedAfterPreviewAreRevalidatedWithoutStockDeduction() {
        Fixture fixture = ready();
        fixture.workspace.setPointsText("30");
        fixture.workspace.preview().join();
        var account = fixture.loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID);
        account.redeemPoints(90);
        fixture.loyalty.save(account);
        var state = fixture.workspace.confirm().join();
        assertFalse(state.outcome().isSuccess());
        assertTrue(state.outcome().getMessage().contains("Insufficient loyalty points"));
        assertEquals(10, fixture.stocks.findByProductId(PRODUCT).getAvailableQuantity());
        assertEquals(0, fixture.stocks.findByProductId(PRODUCT).getVersion());
        assertEquals(10, state.data().customers().getFirst().points());
        assertEquals(2, state.cart().getFirst().quantity());
    }

    @Test
    void businessFailuresForUnknownCustomerAndExcessivePointsRemainExplicit() {
        Fixture fixture = ready();
        fixture.workspace.selectCustomer(new UUID(9, 9));
        assertTrue(fixture.workspace.preview().join().outcome().getMessage().contains("Unknown customer"));
        fixture.workspace.selectCustomer(DemoOrderData.CUSTOMER_ID);
        fixture.workspace.setPointsText("101");
        assertTrue(fixture.workspace.preview().join().outcome().getMessage().contains("Insufficient loyalty points"));
        fixture.assertOriginal();
    }

    @Test
    void invalidLocalInputsNeverInvokeTheUseCase() {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.workspace.refresh().join();
        assertFalse(fixture.workspace.snapshot().canPreview());
        assertThrows(CompletionException.class, () -> fixture.workspace.preview().join());
        fixture.workspace.add(PRODUCT, 1);
        fixture.workspace.selectCustomer(DemoOrderData.CUSTOMER_ID);
        assertFalse(fixture.workspace.snapshot().canPreview());
        fixture.workspace.selectFulfillment(FulfillmentType.DELIVERY);
        for (String invalid : List.of("-1", "abc", "", "2147483648")) {
            fixture.workspace.setPointsText(invalid);
            assertFalse(fixture.workspace.snapshot().canPreview());
            assertThrows(CompletionException.class, () -> fixture.workspace.preview().join());
        }
        assertEquals(0, fixture.calls.get());
        fixture.assertOriginal();
    }

    @Test
    void everyRelevantInputChangeInvalidatesTheAuthoritativePreview() {
        List<Consumer<OrderWorkspace>> edits = List.of(
            workspace -> workspace.add(CANDLE, 1),
            workspace -> workspace.changeQuantity(PRODUCT, 3),
            workspace -> workspace.selectCustomer(new UUID(8, 8)),
            workspace -> workspace.setPointsText("1"),
            workspace -> workspace.selectFulfillment(FulfillmentType.IN_STORE_PICKUP),
            OrderWorkspace::invalidatePreview);
        for (Consumer<OrderWorkspace> edit : edits) {
            Fixture fixture = ready();
            fixture.workspace.preview().join();
            edit.accept(fixture.workspace);
            assertNull(fixture.workspace.snapshot().preview());
            assertFalse(fixture.workspace.snapshot().canConfirm());
            assertThrows(CompletionException.class, () -> fixture.workspace.confirm().join());
            assertEquals(0, fixture.identities.get());
        }
    }

    @Test
    void staleBackgroundPreviewCannotOverwriteMoreRecentCartInput() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        Fixture fixture = new Fixture(tasks::add);
        fixture.workspace.refresh();
        tasks.remove().run();
        fixture.selectInputs();
        var preview = fixture.workspace.preview();
        fixture.workspace.changeQuantity(PRODUCT, 3);
        tasks.remove().run();
        assertNull(preview.join().preview());
        assertEquals(3, fixture.workspace.snapshot().cart().getFirst().quantity());
        assertFalse(fixture.workspace.snapshot().canConfirm());
        fixture.assertOriginal();
    }

    @Test
    void duplicateConfirmationIsRejectedBeforeSchedulingAndCannotClearANewerCart() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        Fixture fixture = new Fixture(tasks::add);
        fixture.workspace.refresh(); tasks.remove().run();
        fixture.selectInputs();
        fixture.workspace.preview(); tasks.remove().run();
        var first = fixture.workspace.confirm();
        assertThrows(CompletionException.class, () -> fixture.workspace.confirm().join());
        assertThrows(IllegalStateException.class, () -> fixture.workspace.add(CANDLE, 1));
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertTrue(first.join().cart().isEmpty());
        assertEquals(1, fixture.identities.get());
        assertEquals(8, fixture.stocks.findByProductId(PRODUCT).getAvailableQuantity());
    }

    @Test
    void detachedScreenObserversReleaseWhileCartAndInputsRemainInTheSession() {
        Fixture fixture = ready();
        AtomicInteger notifications = new AtomicInteger();
        Runnable unsubscribe = fixture.workspace.listen(notifications::incrementAndGet);
        fixture.workspace.setPointsText("30");
        unsubscribe.run();
        int previous = notifications.get();
        fixture.workspace.selectFulfillment(FulfillmentType.IN_STORE_PICKUP);
        assertEquals(previous, notifications.get());
        var state = fixture.workspace.snapshot();
        assertEquals(2, state.cart().getFirst().quantity());
        assertEquals("30", state.pointsText());
        assertEquals(DemoOrderData.CUSTOMER_ID, state.customerId());
        assertEquals(FulfillmentType.IN_STORE_PICKUP, state.fulfillment());
        fixture.workspace.refresh().join();
        assertEquals(state.cart(), fixture.workspace.snapshot().cart());
    }

    @Test
    void changedInventoryOnReloadRequiresANewPreview() {
        Fixture fixture = ready();
        fixture.workspace.preview().join();
        fixture.u1.handle(new StockRequest(PRODUCT, StockOperation.INCREASE, 1));
        fixture.workspace.refresh().join();
        assertNull(fixture.workspace.snapshot().preview());
        assertFalse(fixture.workspace.snapshot().canConfirm());
    }

    @Test
    void aCommittedOrderRemainsAcknowledgedWhenDataRefreshFails() {
        Fixture fixture = new Fixture(Runnable::run);
        AtomicInteger reads = new AtomicInteger();
        OrderWorkspace workspace = new OrderWorkspace(() -> {
            if (reads.incrementAndGet() > 1) throw new IllegalStateException("Read unavailable");
            return fixture.query.load();
        }, fixture.adapter, Runnable::run);
        workspace.refresh().join();
        workspace.add(PRODUCT, 2);
        workspace.selectCustomer(DemoOrderData.CUSTOMER_ID);
        workspace.selectFulfillment(FulfillmentType.DELIVERY);
        workspace.preview().join();
        var state = workspace.confirm().join();
        assertTrue(state.outcome().isSuccess());
        assertNotNull(fixture.orders.findById(state.receipt().response().getOrderId()));
        assertTrue(state.cart().isEmpty());
        assertFalse(state.loaded());
        assertFalse(state.canConfirm());
        assertEquals(8, fixture.stocks.findByProductId(PRODUCT).getAvailableQuantity());
    }

    @Test
    void unexpectedUseCaseFailuresPropagateAndKeepTheCartForRecovery() {
        Fixture fixture = new Fixture(Runnable::run);
        IllegalStateException failure = new IllegalStateException("Unexpected use-case failure");
        OrderWorkspace workspace = new OrderWorkspace(fixture.query, (request, fulfillment) -> { throw failure; }, Runnable::run);
        workspace.refresh().join();
        workspace.add(PRODUCT, 1);
        workspace.selectCustomer(DemoOrderData.CUSTOMER_ID);
        workspace.selectFulfillment(FulfillmentType.DELIVERY);
        assertSame(failure, assertThrows(CompletionException.class, () -> workspace.preview().join()).getCause());
        assertEquals(1, workspace.snapshot().cart().size());
        assertNull(workspace.snapshot().preview());
        assertFalse(workspace.snapshot().busy());
        fixture.assertOriginal();
    }

    private static Fixture ready() {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.workspace.refresh().join();
        fixture.selectInputs();
        return fixture;
    }

    private static final class Fixture {
        final DatabaseProductRepository products = new DatabaseProductRepository();
        final DatabaseStockRepository stocks = new DatabaseStockRepository(products);
        final DatabaseLoyaltyRepository loyalty = new DatabaseLoyaltyRepository();
        final DatabaseOrderRepository orders = new DatabaseOrderRepository();
        final SynchronizeStockInteractor u1 = new SynchronizeStockInteractor(products, stocks);
        final AtomicInteger identities = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        final RepositoryOrderDataQuery query;
        final RequestScopedOrderAdapter adapter;
        final OrderWorkspace workspace;

        Fixture(Executor worker) {
            DemoInventory.populate(products, stocks, PRODUCT, CANDLE);
            DemoOrderData.populate(loyalty);
            var customer = DemoOrderData.customer();
            query = new RepositoryOrderDataQuery(products, stocks, loyalty, List.of(customer));
            adapter = new RequestScopedOrderAdapter(id -> DemoOrderData.CUSTOMER_ID.equals(id) ? customer : null,
                products, stocks, loyalty, new DatabaseOrderTransactionGateway(stocks, loyalty, orders),
                DemoOrderData.discountPolicy(), () -> new UUID(1, identities.incrementAndGet()));
            workspace = new OrderWorkspace(query, (request, fulfillment) -> {
                calls.incrementAndGet();
                return adapter.handle(request, fulfillment);
            }, worker);
        }

        void selectInputs() {
            workspace.add(PRODUCT, 2);
            workspace.selectCustomer(DemoOrderData.CUSTOMER_ID);
            workspace.selectFulfillment(FulfillmentType.DELIVERY);
        }

        void assertOriginal() {
            assertEquals(10, stocks.findByProductId(PRODUCT).getAvailableQuantity());
            assertEquals(0, stocks.findByProductId(PRODUCT).getVersion());
            assertEquals(100, loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getPointsBalance());
            assertEquals(0, loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getVersion());
        }
    }
}
