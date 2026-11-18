import application.DemoInventory;
import application.DemoOrderData;
import controller.StockController;
import controller.StockOutputBoundary;
import controller.PlaceOrderController;
import controller.OrderOutputBoundary;
import entities.Customer;
import entities.FulfillmentType;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import java.util.function.Supplier;
import presenter.PlaceOrderPresenter;
import presenter.StockPresenter;
import presenter.StockViewBoundary;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import repositories.DatabaseLoyaltyRepository;
import repositories.DatabaseOrderRepository;
import repositories.DatabaseOrderTransactionGateway;
import usecases.PlaceOrderInteractor;
import usecases.StockRequestHandler;
import usecases.SynchronizeStockInteractor;
import view.ConsoleStockView;
import view.ConsoleOrderView;

/** Application composition root and console navigation for A3 Step 25. */
public final class AuraPerfumesApplication {
    /** Demonstration configuration only. The production conversion rate is undecided. */
    static final BigDecimal DEMO_AMOUNT_PER_POINT = DemoOrderData.AMOUNT_PER_POINT;
    static final UUID DEMO_CUSTOMER_ID = DemoOrderData.CUSTOMER_ID;
    static final UUID DEMO_CANDLE_ID = new UUID(0, 200);
    private final Scanner input;
    private final PrintStream output;
    private final UUID sampleProductId;
    private final StockController stockController;
    private final PlaceOrderController orderController;
    private final DatabaseLoyaltyRepository loyalty;

    /** Assembles a session using caller-owned streams that remain open. */
    public AuraPerfumesApplication(Scanner input, PrintStream output) {
        this(input, output, UUID.randomUUID());
    }

    /** Assembles the same session with a predictable sample UUID for integration tests. */
    AuraPerfumesApplication(Scanner input, PrintStream output, UUID sampleProductId) {
        this(input, output, sampleProductId, UUID::randomUUID, new DatabaseProductRepository());
    }

    private AuraPerfumesApplication(Scanner input, PrintStream output, UUID sampleProductId,
                                    Supplier<UUID> orderIds, DatabaseProductRepository products) {
        this(input, output, sampleProductId, orderIds, products, new DatabaseStockRepository(products),
            new DatabaseLoyaltyRepository(), new DatabaseOrderRepository());
    }

    /** Initializes demo data into caller-supplied empty repositories for integration tests. */
    AuraPerfumesApplication(Scanner input, PrintStream output, UUID sampleProductId, Supplier<UUID> orderIds,
                            DatabaseProductRepository products, DatabaseStockRepository stocks,
                            DatabaseLoyaltyRepository loyalty, DatabaseOrderRepository orders) {
        this.input = Objects.requireNonNull(input, "Application input is required");
        this.output = Objects.requireNonNull(output, "Application output is required");
        this.sampleProductId = Objects.requireNonNull(sampleProductId, "Sample product UUID is required");

        Objects.requireNonNull(orderIds, "Order ID source is required");
        Objects.requireNonNull(products, "Product repository is required");
        Objects.requireNonNull(stocks, "Stock repository is required");
        this.loyalty = Objects.requireNonNull(loyalty, "Loyalty repository is required");
        Objects.requireNonNull(orders, "Order repository is required");
        DemoInventory.populate(products, stocks, sampleProductId, DEMO_CANDLE_ID);
        Customer customer = DemoOrderData.customer();
        DemoOrderData.populate(loyalty);

        StockRequestHandler handler = new SynchronizeStockInteractor(products, stocks);
        StockViewBoundary view = new ConsoleStockView(output);
        StockOutputBoundary presenter = new StockPresenter(view);
        stockController = new StockController(handler, presenter, input, output);

        DatabaseOrderTransactionGateway transaction = new DatabaseOrderTransactionGateway(stocks, loyalty, orders);
        PlaceOrderInteractor orderHandler = new PlaceOrderInteractor(
            id -> DEMO_CUSTOMER_ID.equals(id) ? customer : null, products, stocks, loyalty, transaction,
            DemoOrderData.discountPolicy(), request -> selectConfirmationContext(orderIds));
        OrderOutputBoundary orderPresenter = new PlaceOrderPresenter(new ConsoleOrderView(output));
        orderController = new PlaceOrderController(orderHandler, orderPresenter, input, output);
    }

    /** Runs both submenus against the same session repositories and Scanner. */
    public void run() {
        output.println("Sample product UUID = " + sampleProductId);
        output.println("Sample product name = Aura, initial quantity = 10");
        output.println("Demonstration data only");
        output.println("Sample customer UUID = " + DEMO_CUSTOMER_ID);
        output.println("Initial loyalty points = 100");
        output.println("Second sample product UUID = " + DEMO_CANDLE_ID);
        output.println("Second sample product name = Aura Candle, unit price = 24.50, initial quantity = 20");
        output.println("Aura unit price = 79.90");
        output.println("Demo-only amount per loyalty point = " + DEMO_AMOUNT_PER_POINT.toPlainString()
            + " (business rate undecided)");
        while (true) {
            output.println("=== Aura Perfumes ===\n1. Stock Management (U1)\n2. Place Order and Redeem Points (U2)\n0. Exit");
            output.flush();
            if (!input.hasNextLine()) {
                output.println("Application closed.");
                return;
            }
            switch (input.nextLine().strip()) {
                case "1" -> stockController.run();
                case "2" -> {
                    output.println("Current demo loyalty points = " + loyalty.findByCustomerId(DEMO_CUSTOMER_ID).getPointsBalance());
                    orderController.run();
                }
                case "0" -> {
                    output.println("Application closed.");
                    return;
                }
                default -> output.println("Invalid main-menu choice. Enter 1 for stock, 2 for orders, or 0 to exit.");
            }
        }
    }

    /**
     * Application-boundary interaction invoked only for confirmation. Fulfillment
     * comes from this explicit console choice. Optional instructions remain absent.
     * Demo order numbers use DEMO- followed by the generated order UUID.
     */
    private PlaceOrderInteractor.ConfirmationContext selectConfirmationContext(Supplier<UUID> orderIds) {
        while (true) {
            output.println("Select fulfillment for this confirmation\n1. Delivery\n2. In-store pickup");
            output.flush();
            if (!input.hasNextLine()) {
                return null;
            }
            FulfillmentType fulfillmentType = switch (input.nextLine().strip()) {
                case "1" -> FulfillmentType.DELIVERY;
                case "2" -> FulfillmentType.IN_STORE_PICKUP;
                default -> null;
            };
            if (fulfillmentType != null) {
                UUID orderId = Objects.requireNonNull(orderIds.get(), "Generated order ID is required");
                return new PlaceOrderInteractor.ConfirmationContext(orderId, "DEMO-" + orderId,
                    fulfillmentType, null, null);
            }
            output.println("Invalid fulfillment choice. Enter 1 for delivery or 2 for in-store pickup.");
        }
    }

    /** Starts the application with a single Scanner shared by all active menus. */
    public static void main(String[] args) {
        new AuraPerfumesApplication(new Scanner(System.in), System.out).run();
    }
}
