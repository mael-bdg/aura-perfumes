package gui;

import application.DemoInventory;
import application.DemoBoutiqueOrders;
import application.DemoOrderData;
import entities.Customer;
import controller.BoutiqueOrderController;
import gui.boutique.BoutiqueOrdersFXController;
import gui.consultations.ConsultationFXController;
import gateways.BoutiqueOrderRepository;
import repositories.DatabaseBoutiqueEmployeeRepository;
import gui.BoutiqueDisplayDirectory;
import usecases.ProcessBoutiqueOrderInteractor;
import database.SQLiteDatabaseInitializer;
import database.SQLiteTestDataSeeder;
import gui.navigation.ApplicationShellController;
import gui.orders.OrderWorkspace;
import gui.orders.PlaceOrderFXController;
import gui.orders.RepositoryOrderDataQuery;
import gui.orders.RequestScopedOrderAdapter;
import gui.stock.StockDashboardModel;
import gui.stock.StockDashboardService;
import gui.stock.StockFXController;
import inventory.RepositoryInventoryQuery;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import repositories.DatabaseLoyaltyRepository;
import repositories.DatabaseOrderRepository;
import repositories.DatabaseOrderTransactionGateway;
import usecases.LoyaltyDiscountPolicy;
import usecases.SynchronizeStockInteractor;

/** Owns the GUI session's shared repositories, U1 boundary, and background worker. */
public final class GuiCompositionRoot implements AutoCloseable {
    /** Temporary presentation default; configure with -Daura.stock.lowThreshold=N. */
    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;
    private final ExecutorService worker;
    private final BoutiqueOrderRepository boutiqueOrders;
    private final DatabaseOrderRepository sessionOrders;
    private final DatabaseBoutiqueEmployeeRepository boutiqueEmployees = new DatabaseBoutiqueEmployeeRepository();
    private final BoutiqueDisplayDirectory displayDirectory = new BoutiqueDisplayDirectory();
    private final StockDashboardService stockDashboard;
    private final StockDashboardModel stockModel;
    private final OrderWorkspace orderWorkspace;

    /** Uses caller-owned session repositories so other use cases can share their state. */
    public GuiCompositionRoot(DatabaseProductRepository products, DatabaseStockRepository stocks,
                              ExecutorService worker, int lowStockThreshold) {
        this(products, stocks, new DatabaseLoyaltyRepository(), new DatabaseOrderRepository(),
            List.of(), DemoOrderData.discountPolicy(), worker, lowStockThreshold);
    }

    /** Shares the same application participants with inventory reads, U1 and the U2 transaction. */
    public GuiCompositionRoot(DatabaseProductRepository products, DatabaseStockRepository stocks,
                              DatabaseLoyaltyRepository loyalty, DatabaseOrderRepository orders,
                              List<Customer> customers, LoyaltyDiscountPolicy discountPolicy,
                              ExecutorService worker, int lowStockThreshold) {
        this.worker = worker;
        boutiqueOrders = orders;
        sessionOrders = orders;
        try {
            SQLiteDatabaseInitializer.initialize();
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to initialize boutique order storage", exception);
        }
        stockDashboard = new StockDashboardService(new RepositoryInventoryQuery(products, stocks),
            new SynchronizeStockInteractor(products, stocks), worker);
        stockModel = new StockDashboardModel(lowStockThreshold);
        Map<UUID, Customer> customerLookup = customers.stream().collect(Collectors.toUnmodifiableMap(
            Customer::getCustomerId, Function.identity()));
        var transaction = new DatabaseOrderTransactionGateway(stocks, loyalty, orders);
        var adapter = new RequestScopedOrderAdapter(customerLookup::get, products, stocks, loyalty,
            transaction, discountPolicy, UUID::randomUUID);
        orderWorkspace = new OrderWorkspace(new RepositoryOrderDataQuery(products, stocks, loyalty, customers),
            adapter, worker);
    }

    /** Reuses the existing console demonstration dataset, initialized once per GUI session. */
    public static GuiCompositionRoot demonstration() {
        DatabaseProductRepository products = new DatabaseProductRepository();
        DatabaseStockRepository stocks = new DatabaseStockRepository(products);
        DemoInventory.populate(products, stocks, UUID.randomUUID(), new UUID(0, 200));
        DatabaseLoyaltyRepository loyalty = new DatabaseLoyaltyRepository();
        DemoOrderData.populate(loyalty);
        DatabaseOrderRepository orders = new DatabaseOrderRepository();
        DemoBoutiqueOrders.populate(orders);
        ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("aura-inventory").factory());
        GuiCompositionRoot root = new GuiCompositionRoot(products, stocks, loyalty, orders,
            List.of(DemoOrderData.customer()), DemoOrderData.discountPolicy(), worker,
            Integer.getInteger("aura.stock.lowThreshold", DEFAULT_LOW_STOCK_THRESHOLD));
        try {
            // Seed the existing employee before U4 can be opened, independently of visiting U3.
            SQLiteTestDataSeeder.seed();
        } catch (java.sql.SQLException exception) {
            root.close();
            throw new IllegalStateException("Unable to initialize demonstration data", exception);
        }
        return root;
    }

    /** Supplies injected controllers to every FXMLLoader in this GUI session. */
    public Object createController(Class<?> type) {
        if (type == BoutiqueOrdersFXController.class) {
            BoutiqueOrdersFXController view = new BoutiqueOrdersFXController(boutiqueOrders::findById, worker);
            view.setReferenceLookup(value -> {
                try { return UUID.fromString(value.trim()); }
                catch (IllegalArgumentException exception) { return sessionOrders.findIdByOrderNumber(value); }
            }, displayDirectory::employeeId,
                displayDirectory::employeeName, displayDirectory::employeeCode);
            view.setController(new BoutiqueOrderController(
                new ProcessBoutiqueOrderInteractor(boutiqueOrders, boutiqueEmployees), view));
            return view;
        }
        if (type == ConsultationFXController.class) {
            ConsultationFXController view = new ConsultationFXController(worker);
            view.setAssociateNameLookup(displayDirectory::associateName);
            return view;
        }
        if (type == StockFXController.class) return new StockFXController(stockDashboard, stockModel);
        if (type == PlaceOrderFXController.class) return new PlaceOrderFXController(orderWorkspace);
        if (type == ApplicationShellController.class) return new ApplicationShellController(this::createController);
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create GUI controller " + type.getName(), exception);
        }
    }

    /** Stops accepting background work when the application closes. */
    @Override
    public void close() { worker.shutdown(); }
}
