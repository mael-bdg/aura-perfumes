import entities.FulfillmentType;
import entities.Order;
import entities.OrderStatus;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import repositories.DatabaseLoyaltyRepository;
import repositories.DatabaseOrderRepository;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;
import static org.junit.jupiter.api.Assertions.*;

class AuraPerfumesApplicationU2Test {
    private static final UUID PRODUCT = new UUID(0, 1);
    private static final UUID CUSTOMER = AuraPerfumesApplication.DEMO_CUSTOMER_ID;
    private static final UUID CANDLE = AuraPerfumesApplication.DEMO_CANDLE_ID;
    private static final UUID FIRST_ORDER = new UUID(0, 1001);
    private static final UUID SECOND_ORDER = new UUID(0, 1002);
    private final DatabaseProductRepository products = new DatabaseProductRepository();
    private final DatabaseStockRepository stocks = new DatabaseStockRepository(products);
    private final DatabaseLoyaltyRepository loyalty = new DatabaseLoyaltyRepository();
    private final DatabaseOrderRepository orders = new DatabaseOrderRepository();
    private final AtomicInteger orderSequence = new AtomicInteger(1000);
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    @Test
    void displaysDemoIdentifiersInitialValuesAndUnapprovedRateClearly() {
        run("2", "0", "0");
        assertTrue(text().contains("Sample product UUID = " + PRODUCT));
        assertTrue(text().contains("Sample customer UUID = " + CUSTOMER));
        assertTrue(text().contains("Second sample product UUID = " + CANDLE));
        assertTrue(text().contains("initial quantity = 10"));
        assertTrue(text().contains("initial quantity = 20"));
        assertTrue(text().contains("Initial loyalty points = 100"));
        assertTrue(text().contains("Demo-only amount per loyalty point = 0.10 (business rate undecided)"));
        assertTrue(text().contains("2. Place Order and Redeem Points (U2)"));
        assertTrue(text().contains("1. Preview order"));
        assertInitialState();
    }

    @Test
    void previewDoesNotWriteOrRequestFulfillment() {
        run("2", "1", customer(), product(), "2", "", "30", "0", "0");
        assertTrue(text().contains("Message = Preview calculated"));
        assertTrue(text().contains("Subtotal = 159.80"));
        assertTrue(text().contains("Discount = 3.00"));
        assertTrue(text().contains("Total = 156.80"));
        assertFalse(text().contains("Select fulfillment"));
        assertEquals(1000, orderSequence.get());
        assertInitialState();
    }

    @Test
    void confirmationCommitsMultipleProductsPointsAndExplicitPickupTogether() {
        run("2", "2", customer(), product(), "2", CANDLE.toString(), "3", "", "30", "2", "0",
            "2", "0", "0");
        Order stored = orders.findById(FIRST_ORDER);
        assertNotNull(stored);
        assertEquals("DEMO-" + FIRST_ORDER, stored.getOrderNumber());
        assertEquals(FulfillmentType.IN_STORE_PICKUP, stored.getFulfillmentType());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stored.getStatus());
        assertEquals(1, stored.getVersion());
        assertNull(stored.getPackagingInstructions());
        assertNull(stored.getDeliveryNotes());
        assertNull(stored.getPickupPin());
        assertEquals(0, stored.getTotal().compareTo(new BigDecimal("230.30")));
        assertStock(PRODUCT, 8, 1);
        assertStock(CANDLE, 17, 1);
        assertAccount(70, 1);
        assertTrue(text().contains("Order UUID = " + FIRST_ORDER));
        assertTrue(text().contains("Order committed with status PENDING_CONFIRMATION"));
        assertTrue(text().contains("Current demo loyalty points = 70"));
    }

    @Test
    void sharedRepositoryInstancesServeU1U2AndTransactionAcrossReentry() {
        run("1", "2", product(), "1", "4",
            "2", "1", customer(), product(), "11", "", "0",
            "2", customer(), product(), "11", "", "0", "1", "0",
            "1", "1", product(), "4", "0");
        assertTrue(text().contains("Available quantity = 11"));
        assertTrue(text().contains("Subtotal = 878.90"));
        assertTrue(text().contains("Available quantity = 0"));
        assertStock(PRODUCT, 0, 2);
        assertAccount(100, 0);
        assertEquals(11, orders.findById(FIRST_ORDER).getItems().getFirst().getQuantity());
    }

    @Test
    void completeWorkflowUsesUpdatedStockAndPointsForSubsequentRequests() {
        run("1", "1", product(), "4",
            "2", "1", customer(), product(), "2", "", "30",
            "2", customer(), product(), "2", "", "30", "1", "0",
            "1", "1", product(), "4",
            "2", "1", customer(), product(), "1", "", "70",
            "1", customer(), product(), "9", "", "0",
            "1", customer(), product(), "1", "", "71", "0", "0");
        assertTrue(text().contains("Available quantity = 10"));
        assertTrue(text().contains("Available quantity = 8"));
        assertTrue(text().contains("Current demo loyalty points = 70"));
        assertTrue(text().contains("Total = 72.90"));
        assertTrue(text().contains("Insufficient stock"));
        assertTrue(text().contains("Insufficient loyalty points"));
        assertStock(PRODUCT, 8, 1);
        assertAccount(70, 1);
        assertNotNull(orders.findById(FIRST_ORDER));
        assertNull(orders.findById(SECOND_ORDER));
        assertTrue(text().endsWith("Application closed." + System.lineSeparator()));
    }

    @Test
    void insufficientStockConfirmationLeavesAllRepositoriesUnchanged() {
        run("2", "2", customer(), product(), "11", "", "10", "1", "0", "0");
        assertTrue(text().contains("Insufficient stock"));
        assertFalse(text().contains("Order committed"));
        assertInitialState();
    }

    @Test
    void insufficientPointsConfirmationLeavesAllRepositoriesUnchanged() {
        run("2", "2", customer(), product(), "2", "", "101", "2", "0", "0");
        assertTrue(text().contains("Insufficient loyalty points"));
        assertFalse(text().contains("Order committed"));
        assertInitialState();
    }

    @Test
    void failedConfirmationsAfterSuccessPreservePreviouslyCommittedState() {
        run("2", "2", customer(), product(), "2", "", "30", "1",
            "2", customer(), product(), "9", "", "0", "1",
            "2", customer(), product(), "1", "", "71", "2", "0", "0");
        assertStock(PRODUCT, 8, 1);
        assertAccount(70, 1);
        assertNotNull(orders.findById(FIRST_ORDER));
        assertNull(orders.findById(SECOND_ORDER));
        assertNull(orders.findById(new UUID(0, 1003)));
        assertEquals(1, text().lines().filter(line -> line.contains("Message = Order committed")).count());
    }

    @Test
    void eachConfirmationUsesItsExplicitFulfillmentAndUniqueIdentity() {
        run("2", "2", customer(), product(), "1", "", "10", "1",
            "2", customer(), product(), "1", "", "10", "2", "0", "0");
        Order first = orders.findById(FIRST_ORDER);
        Order second = orders.findById(SECOND_ORDER);
        assertEquals(FulfillmentType.DELIVERY, first.getFulfillmentType());
        assertEquals(FulfillmentType.IN_STORE_PICKUP, second.getFulfillmentType());
        assertNotEquals(first.getOrderId(), second.getOrderId());
        assertNotEquals(first.getOrderNumber(), second.getOrderNumber());
        assertStock(PRODUCT, 8, 2);
        assertAccount(80, 2);
    }

    @Test
    void invalidFulfillmentChoiceRequiresExplicitValidChoiceBeforeCommit() {
        run("2", "2", customer(), product(), "1", "", "0", "bad", "", "2", "0", "0");
        assertEquals(2, text().lines().filter(line -> line.startsWith("Invalid fulfillment choice")).count());
        assertEquals(FulfillmentType.IN_STORE_PICKUP, orders.findById(FIRST_ORDER).getFulfillmentType());
        assertStock(PRODUCT, 9, 1);
        assertAccount(100, 0);
    }

    @Test
    void endOfInputDuringFulfillmentClosesCleanlyWithoutCommitting() {
        run("2", "2", customer(), product(), "1", "", "10");
        assertTrue(text().contains("Select fulfillment for this confirmation"));
        assertTrue(text().endsWith("Application closed." + System.lineSeparator()));
        assertEquals(1000, orderSequence.get());
        assertInitialState();
    }

    @Test
    void invalidU2InputAllowsMainMenuReturnAndU1StillWorks() {
        run("bad-menu", "2", "1", "bad-uuid", "0", "1", "1", product(), "4", "0");
        assertTrue(text().contains("Invalid main-menu choice"));
        assertTrue(text().contains("Invalid customer UUID"));
        assertTrue(text().contains("Available quantity = 10"));
        assertInitialState();
    }

    @Test
    void bothSubmenusLeaveSharedScannerOpenForParentNavigation() {
        Scanner shared = new Scanner(lines("2", "0", "1", "4", "0", "remaining"));
        application(shared).run();
        assertEquals("remaining", shared.nextLine());
        assertTrue(text().contains("1. Preview order"));
        assertTrue(text().contains("1. CHECK stock"));
        assertInitialState();
    }

    private AuraPerfumesApplication application(Scanner input) {
        return new AuraPerfumesApplication(input, output, PRODUCT, () -> new UUID(0, orderSequence.incrementAndGet()),
            products, stocks, loyalty, orders);
    }

    private void run(String... commands) {
        application(new Scanner(lines(commands))).run();
    }

    private void assertInitialState() {
        assertStock(PRODUCT, 10, 0);
        assertStock(CANDLE, 20, 0);
        assertAccount(100, 0);
        assertNull(orders.findById(FIRST_ORDER));
        assertNull(orders.findById(SECOND_ORDER));
    }

    private void assertStock(UUID id, int quantity, long version) {
        assertEquals(quantity, stocks.findByProductId(id).getAvailableQuantity());
        assertEquals(version, stocks.findByProductId(id).getVersion());
    }

    private void assertAccount(int points, long version) {
        assertEquals(points, loyalty.findByCustomerId(CUSTOMER).getPointsBalance());
        assertEquals(version, loyalty.findByCustomerId(CUSTOMER).getVersion());
    }

    private static String customer() {
        return CUSTOMER.toString();
    }

    private static String product() {
        return PRODUCT.toString();
    }

    private static String lines(String... commands) {
        return String.join("\n", commands) + "\n";
    }

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
