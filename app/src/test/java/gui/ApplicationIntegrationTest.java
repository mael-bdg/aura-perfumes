package gui;

import application.DemoInventory;
import application.DemoOrderData;
import database.SQLiteConnection;
import database.SQLiteDatabaseInitializer;
import datatransferobjects.*;
import entities.*;
import gateways.BoutiqueOrderRepository;
import gui.boutique.BoutiqueOrdersFXController;
import gui.orders.OrderWorkspace;
import gui.orders.PlaceOrderFXController;
import gui.stock.StockDashboardModel;
import gui.stock.StockDashboardService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repositories.*;
import usecases.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cross-use-case regressions using the actual composition root, adapters and transaction. */
class ApplicationIntegrationTest {
    private static final UUID PRODUCT = new UUID(0, 1);
    private static final UUID CANDLE = new UUID(0, 2);
    private static final UUID EMPLOYEE = new UUID(0, 3);
    @TempDir Path directory;
    private GuiCompositionRoot root;
    private DatabaseStockRepository stocks;
    private DatabaseLoyaltyRepository loyalty;
    private DatabaseOrderRepository orders;
    private OrderWorkspace workspace;
    private StockDashboardService stockDashboard;
    private BoutiqueOrderRepository boutique;

    @BeforeEach void initialize() throws Exception {
        SQLiteConnection.setDatabaseUrl("jdbc:sqlite:" + directory.resolve("integration.db"));
        SQLiteDatabaseInitializer.initialize();
        try (var connection = SQLiteConnection.getConnection(); var insert = connection.prepareStatement(
                "INSERT INTO boutique_employees(id, name, role) VALUES (?, 'Test associate', 'SALES_ASSOCIATE')")) {
            insert.setString(1, EMPLOYEE.toString());
            insert.executeUpdate();
        }
        var products = new DatabaseProductRepository();
        stocks = new DatabaseStockRepository(products);
        loyalty = new DatabaseLoyaltyRepository();
        orders = new DatabaseOrderRepository();
        DemoInventory.populate(products, stocks, PRODUCT, CANDLE);
        DemoOrderData.populate(loyalty);
        root = new GuiCompositionRoot(products, stocks, loyalty, orders,
            List.of(DemoOrderData.customer()), DemoOrderData.discountPolicy(),
            Executors.newSingleThreadExecutor(), 5);
        workspace = field(root, "orderWorkspace", OrderWorkspace.class);
        stockDashboard = field(root, "stockDashboard", StockDashboardService.class);
        boutique = field(root, "boutiqueOrders", BoutiqueOrderRepository.class);
        workspace.refresh().get(5, TimeUnit.SECONDS);
    }

    @AfterEach void close() {
        if (root != null) root.close();
        SQLiteConnection.resetDatabaseUrl();
    }

    @Test void successfulOrderFlowsFromU2ThroughU4WithOneSharedVersionedStore() throws Exception {
        assertSame(orders, boutique);
        ready(2, 30);
        var preview = workspace.preview().get(5, TimeUnit.SECONDS);
        assertTrue(preview.preview().response().isSuccess());
        assertEquals(10, stocks.findByProductId(PRODUCT).getAvailableQuantity());
        assertEquals(0, stocks.findByProductId(PRODUCT).getVersion());
        assertEquals(100, loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getPointsBalance());
        var committed = workspace.confirm().get(5, TimeUnit.SECONDS).receipt().response();
        assertTrue(committed.isSuccess());
        Order order = boutique.findById(committed.getOrderId());
        assertEquals(committed.getOrderId(), order.getOrderId());
        assertEquals(DemoOrderData.CUSTOMER_ID, order.getCustomerId());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertEquals(1, order.getVersion());
        assertEquals(2, order.getItems().getFirst().getQuantity());
        assertEquals(PRODUCT, order.getItems().getFirst().getProductId());
        assertEquals(new BigDecimal("159.80"), order.getSubtotal());
        assertEquals(0, new BigDecimal("3.00").compareTo(order.getDiscount()));
        assertEquals(0, new BigDecimal("156.80").compareTo(order.getTotal()));
        assertEquals(8, stocks.findByProductId(PRODUCT).getAvailableQuantity());
        assertEquals(70, loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getPointsBalance());
        for (OrderStatus target : List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION, OrderStatus.READY_FOR_PICKUP)) {
            var result = processing().handle(new ProcessBoutiqueOrderRequest(order.getOrderId(), EMPLOYEE, target, null, "1234"));
            assertTrue(result.isSuccess(), result.getMessage());
        }
        var stored = orders.findById(order.getOrderId());
        assertEquals(OrderStatus.READY_FOR_PICKUP, stored.getStatus());
        assertEquals("1234", stored.getPickupPin());
        assertEquals(4, stored.getVersion());
        var invalid = processing().handle(new ProcessBoutiqueOrderRequest(order.getOrderId(), EMPLOYEE,
            OrderStatus.IN_PREPARATION, null, null));
        assertFalse(invalid.isSuccess());
        assertEquals(4, orders.findById(order.getOrderId()).getVersion());
        assertEquals(1, stocks.findByProductId(PRODUCT).getVersion());
        assertEquals(1, loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getVersion());
    }

    @Test void confirmationRevalidatesAndRejectsStockChangesWithoutPartialWrites() throws Exception {
        ready(2, 30);
        workspace.preview().get(5, TimeUnit.SECONDS);
        assertTrue(stockDashboard.execute(new StockRequest(PRODUCT, StockOperation.DECREASE, 9))
            .get(5, TimeUnit.SECONDS).response().isSuccess());
        var failure = workspace.confirm().get(5, TimeUnit.SECONDS);
        assertFalse(failure.outcome().isSuccess());
        assertTrue(field(orders, "orders", java.util.Map.class).isEmpty());
        assertNull(failure.outcome().getOrderId());
        assertEquals(1, stocks.findByProductId(PRODUCT).getAvailableQuantity());
        assertEquals(1, stocks.findByProductId(PRODUCT).getVersion());
        assertEquals(100, loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getPointsBalance());
        assertEquals(0, loyalty.findByCustomerId(DemoOrderData.CUSTOMER_ID).getVersion());
        assertEquals(1, failure.cart().size());
        // No order is published in either interface when the confirmation fails.
        assertNull(failure.receipt());
    }

    @Test void u1UpdatesAreVisibleToU2AndSubsequentOrderRefreshesSummaryAndLowStockBand() throws Exception {
        var model = new StockDashboardModel(5);
        long initialUnits = model.summarize(stockDashboard.refresh().get(5, TimeUnit.SECONDS)).units();
        stockDashboard.execute(new StockRequest(PRODUCT, StockOperation.DECREASE, 4)).get(5, TimeUnit.SECONDS);
        var loaded = workspace.refresh().get(5, TimeUnit.SECONDS);
        assertEquals(6, loaded.data().products().getFirst().available());
        ready(2, 0);
        workspace.preview().get(5, TimeUnit.SECONDS);
        workspace.confirm().get(5, TimeUnit.SECONDS);
        var rows = stockDashboard.refresh().get(5, TimeUnit.SECONDS);
        assertEquals(4, rows.stream().filter(row -> row.productId().equals(PRODUCT)).findFirst().orElseThrow().quantity());
        assertEquals(initialUnits - 6, model.summarize(rows).units());
        assertEquals(1, model.summarize(rows).lowStockProducts());
        var rejected = stockDashboard.execute(new StockRequest(PRODUCT, StockOperation.DECREASE, 5)).get(5, TimeUnit.SECONDS);
        assertFalse(rejected.response().isSuccess());
        assertEquals(4, stocks.findByProductId(PRODUCT).getAvailableQuantity());
    }

    @Test void newControllersAfterNavigationUseSameOrdersAndWorkspaceButNewSessionStartsEmpty() throws Exception {
        ready(1, 0);
        workspace.preview().get(5, TimeUnit.SECONDS);
        UUID id = workspace.confirm().get(5, TimeUnit.SECONDS).receipt().response().getOrderId();
        var u2 = root.createController(PlaceOrderFXController.class);
        assertSame(workspace, field(u2, "workspace", OrderWorkspace.class));
        var u4 = root.createController(BoutiqueOrdersFXController.class);
        @SuppressWarnings("unchecked")
        Function<UUID, Order> lookup = field(u4, "orderLookup", Function.class);
        assertEquals(id, lookup.apply(id).getOrderId());
        assertEquals(9, stocks.findByProductId(PRODUCT).getAvailableQuantity());
        assertNull(new DatabaseOrderRepository().findById(id), "Orders are explicitly session-scoped, not SQLite orders");
    }

    @Test void consultationBooksOnceAndSurvivesRepositoryRecreationWithoutDuplicateNotifications() throws Exception {
        UUID associate = new UUID(0, 20);
        UUID slot = new UUID(0, 21);
        LocalDateTime start = LocalDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);
        try (var connection = SQLiteConnection.getConnection()) {
            try (var insert = connection.prepareStatement("INSERT INTO sales_associates(id, name) VALUES (?, 'Associate')")) {
                insert.setString(1, associate.toString()); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement(
                    "INSERT INTO consultation_slots(id, associate_id, start_time, duration_minutes) VALUES (?, ?, ?, 30)")) {
                insert.setString(1, slot.toString()); insert.setString(2, associate.toString());
                insert.setString(3, start.toString()); insert.executeUpdate();
            }
        }
        var schedules = new DatabaseAssociateScheduleRepository();
        var bookings = new DatabaseConsultationBookingRepository();
        var availability = new CheckConsultationAvailabilityInteractor(schedules);
        var request = new ConsultationAvailabilityRequest(start.toLocalDate(), associate);
        assertEquals(1, availability.handle(request).getAvailableSlots().size());
        var notifications = new AtomicInteger();
        var booking = new BookConsultationInteractor(schedules, bookings, saved -> { notifications.incrementAndGet(); return true; });
        var input = new BookConsultationRequest("Test Customer", "test@example.com", "Floral", start, associate, false);
        var success = booking.handle(input);
        assertTrue(success.isSuccess(), success.getMessage());
        assertEquals(slot, new DatabaseConsultationBookingRepository().findById(success.getBookingId()).getSlot().getId());
        assertTrue(availability.handle(request).getAvailableSlots().isEmpty());
        assertFalse(booking.handle(input).isSuccess());
        assertEquals(1, notifications.get());
        try (var connection = SQLiteConnection.getConnection(); var query = connection.createStatement();
             var count = query.executeQuery("SELECT COUNT(*) FROM scent_consultations")) {
            assertTrue(count.next()); assertEquals(1, count.getInt(1));
        }
    }

    private void ready(int quantity, int points) {
        workspace.add(PRODUCT, quantity);
        workspace.selectCustomer(DemoOrderData.CUSTOMER_ID);
        workspace.selectFulfillment(FulfillmentType.IN_STORE_PICKUP);
        workspace.setPointsText(Integer.toString(points));
    }

    private ProcessBoutiqueOrderInteractor processing() {
        return new ProcessBoutiqueOrderInteractor(boutique, new DatabaseBoutiqueEmployeeRepository());
    }

    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(object));
    }
}
