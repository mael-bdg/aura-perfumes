package usecases;

import datatransferobjects.OrderItemRequest;
import datatransferobjects.PlaceOrderRequest;
import datatransferobjects.PlaceOrderResponse;
import entities.Customer;
import entities.FulfillmentType;
import entities.LoyaltyAccount;
import entities.Order;
import entities.OrderStatus;
import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderInteractorTest {
    private static final UUID CUSTOMER_ID = new UUID(0, 1);
    private static final UUID PRODUCT_ID = new UUID(0, 2);
    private static final UUID SECOND_PRODUCT_ID = new UUID(0, 3);
    private static final UUID ORDER_ID = new UUID(0, 4);
    private final Map<UUID, Customer> customers = new HashMap<>(Map.of(CUSTOMER_ID,
        new Customer(CUSTOMER_ID, "Alex", "alex@example.com")));
    private final Map<UUID, Product> products = new HashMap<>(Map.of(PRODUCT_ID,
        product(PRODUCT_ID, "10.00"), SECOND_PRODUCT_ID, product(SECOND_PRODUCT_ID, "0.10")));
    private final Map<UUID, Stock> stocks = new HashMap<>(Map.of(PRODUCT_ID,
        new Stock(new UUID(0, 5), PRODUCT_ID, 10, 4), SECOND_PRODUCT_ID,
        new Stock(new UUID(0, 6), SECOND_PRODUCT_ID, 20, 7)));
    private final Map<UUID, LoyaltyAccount> accounts = new HashMap<>(Map.of(CUSTOMER_ID,
        new LoyaltyAccount(new UUID(0, 7), CUSTOMER_ID, 100, 3)));
    private int saveAttempts;
    private final StockRepository stockRepository = new StockRepository() {
        @Override
        public Stock findByProductId(UUID productId) {
            // Return the original deliberately to prove the interactor detaches it.
            return stocks.get(productId);
        }

        @Override
        public boolean saveIfVersionMatches(Stock stock) {
            saveAttempts++;
            fail("This stage must never persist stock");
            return false;
        }
    };
    // Explicit test-only conversion. The business rate remains undecided.
    private final PlaceOrderInteractor interactor = subject(points -> new BigDecimal("0.10").multiply(BigDecimal.valueOf(points)));

    @Test
    void previewsExactAmountsWithoutMutatingOriginalEntities() {
        PlaceOrderInteractor.Preview preview = interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 2), 30);
        assertMoney("20.00", preview.subtotal());
        assertMoney("3.00", preview.discount());
        assertMoney("17.00", preview.total());
        assertEquals(1, preview.items().size());
        assertEquals(PRODUCT_ID, preview.items().getFirst().getProductId());
        assertEquals(2, preview.items().getFirst().getQuantity());
        assertMoney("10.00", preview.items().getFirst().getUnitPrice());
        assertMoney("20.00", preview.items().getFirst().getLineTotal());
        assertOriginalState();
    }

    @Test
    void calculatesMultipleLinesWithoutLosingDecimalPrecision() {
        Map<UUID, Integer> request = new LinkedHashMap<>();
        request.put(PRODUCT_ID, 2);
        request.put(SECOND_PRODUCT_ID, 3);
        PlaceOrderInteractor.Preview preview = interactor.preview(CUSTOMER_ID, request, 2);
        assertMoney("20.30", preview.subtotal());
        assertMoney("0.20", preview.discount());
        assertMoney("20.10", preview.total());
        assertEquals(2, preview.items().size());
        assertMoney("0.30", preview.items().get(1).getLineTotal());
        request.clear();
        assertEquals(2, preview.items().size());
        assertThrows(UnsupportedOperationException.class, () -> preview.items().clear());
        assertOriginalState();
    }

    @Test
    void rejectsNullAndUnknownCustomer() {
        assertRejected(() -> interactor.preview(null, Map.of(PRODUCT_ID, 1), 0), "Customer ID");
        assertRejected(() -> interactor.preview(new UUID(0, 99), Map.of(PRODUCT_ID, 1), 0), "Unknown customer");
        assertOriginalState();
    }

    @Test
    void rejectsMissingEmptyAndInvalidProductIdentifiers() {
        assertRejected(() -> interactor.preview(CUSTOMER_ID, null, 0), "at least one");
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(), 0), "at least one");
        Map<UUID, Integer> invalidId = new HashMap<>();
        invalidId.put(null, 1);
        assertRejected(() -> interactor.preview(CUSTOMER_ID, invalidId, 0), "Product ID");
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(new UUID(0, 99), 1), 0), "Unknown product");
        assertOriginalState();
    }

    @Test
    void rejectsInvalidQuantityAndNegativeRedemption() {
        for (Integer quantity : new Integer[] {null, 0, -1, Integer.MIN_VALUE}) {
            Map<UUID, Integer> invalid = new HashMap<>();
            invalid.put(PRODUCT_ID, quantity);
            assertRejected(() -> interactor.preview(CUSTOMER_ID, invalid, 0), "quantity must be positive");
        }
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), -1), "points cannot be negative");
        assertOriginalState();
    }

    @Test
    void rejectsInsufficientStock() {
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 11), 0), "Insufficient stock");
        assertOriginalState();
    }

    @Test
    void rejectsMissingStock() {
        stocks.remove(PRODUCT_ID);
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 0), "Missing stock");
        assertNull(stocks.get(PRODUCT_ID));
        assertEquals(100, accounts.get(CUSTOMER_ID).getPointsBalance());
        assertEquals(0, saveAttempts);
    }

    @Test
    void rejectsInsufficientOrMissingLoyaltyPoints() {
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 10), 101), "Insufficient loyalty points");
        assertOriginalState();
        accounts.clear();
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 1), "Insufficient loyalty points");
        assertEquals(10, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(0, saveAttempts);
    }

    @Test
    void zeroRedemptionWorksWithoutAccountAndNeedsNoLoyaltyWrite() {
        accounts.clear();
        PlaceOrderInteractor.Preview preview = interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 0);
        assertMoney("0", preview.discount());
        assertMoney("10", preview.total());
        PlaceOrderInteractor.ConfirmationPlan plan = prepare(Map.of(PRODUCT_ID, 1), 0);
        assertNull(plan.account());
        assertEquals(9, plan.stocks().getFirst().getAvailableQuantity());
        assertEquals(10, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertTrue(accounts.isEmpty());
        assertEquals(0, saveAttempts);
    }

    @Test
    void rejectsDiscountAboveSubtotalAndAllowsExactFullDiscount() {
        assertRejected(() -> interactor.preview(CUSTOMER_ID, Map.of(SECOND_PRODUCT_ID, 1), 2), "cannot exceed subtotal");
        PlaceOrderInteractor.Preview preview = interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 100);
        assertMoney("10", preview.discount());
        assertMoney("0", preview.total());
        assertOriginalState();
    }

    @Test
    void preparesDetachedPendingOrderAndDeductionsWithoutCommitting() {
        interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 2), 30);
        PlaceOrderInteractor.ConfirmationPlan plan = prepare(Map.of(PRODUCT_ID, 2), 30);
        Order order = plan.order();
        assertEquals(ORDER_ID, order.getOrderId());
        assertEquals("ORDER-001", order.getOrderNumber());
        assertEquals(CUSTOMER_ID, order.getCustomerId());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertEquals(FulfillmentType.IN_STORE_PICKUP, order.getFulfillmentType());
        assertEquals("Gift wrap", order.getPackagingInstructions());
        assertNull(order.getDeliveryNotes());
        assertEquals(0, order.getVersion());
        assertEquals(30, order.getRedeemedPoints());
        assertMoney("20", order.getSubtotal());
        assertMoney("3", order.getDiscount());
        assertMoney("17", order.getTotal());
        Stock stock = plan.stocks().getFirst();
        assertEquals(stocks.get(PRODUCT_ID).getId(), stock.getId());
        assertEquals(PRODUCT_ID, stock.getProductId());
        assertEquals(8, stock.getAvailableQuantity());
        assertEquals(4, stock.getVersion());
        assertEquals(accounts.get(CUSTOMER_ID).getId(), plan.account().getId());
        assertEquals(70, plan.account().getPointsBalance());
        assertEquals(3, plan.account().getVersion());
        assertThrows(UnsupportedOperationException.class, () -> plan.stocks().clear());
        stock.decrease(1);
        plan.account().redeemPoints(1);
        assertOriginalState();
    }

    @Test
    void preparingMultipleProductsKeepsStockAndLineAssociations() {
        Map<UUID, Integer> request = new LinkedHashMap<>();
        request.put(SECOND_PRODUCT_ID, 3);
        request.put(PRODUCT_ID, 2);
        PlaceOrderInteractor.ConfirmationPlan plan = prepare(request, 0);
        assertEquals(List.of(SECOND_PRODUCT_ID, PRODUCT_ID), plan.stocks().stream().map(Stock::getProductId).toList());
        assertEquals(17, plan.stocks().get(0).getAvailableQuantity());
        assertEquals(8, plan.stocks().get(1).getAvailableQuantity());
        assertMoney("20.30", plan.order().getTotal());
        assertOriginalState();
    }

    @Test
    void confirmationRevalidatesStockAfterPreviewAndDoesNotRedeemPointsOnFailure() {
        interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 5), 10);
        stocks.get(PRODUCT_ID).decrease(6);
        assertRejected(() -> prepare(Map.of(PRODUCT_ID, 5), 10), "Insufficient stock");
        assertEquals(4, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(4, stocks.get(PRODUCT_ID).getVersion());
        assertEquals(100, accounts.get(CUSTOMER_ID).getPointsBalance());
        assertEquals(3, accounts.get(CUSTOMER_ID).getVersion());
        assertEquals(0, saveAttempts);
    }

    @Test
    void confirmationRevalidatesLoyaltyAfterPreviewWithoutDeductingStockOnFailure() {
        interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 50);
        accounts.get(CUSTOMER_ID).redeemPoints(60);
        assertRejected(() -> prepare(Map.of(PRODUCT_ID, 1), 50), "Insufficient loyalty points");
        assertEquals(40, accounts.get(CUSTOMER_ID).getPointsBalance());
        assertEquals(3, accounts.get(CUSTOMER_ID).getVersion());
        assertEquals(10, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(4, stocks.get(PRODUCT_ID).getVersion());
        assertEquals(0, saveAttempts);
    }

    @Test
    void confirmationRevalidatesCustomerProductsAndCurrentPrices() {
        interactor.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 10);
        Customer customer = customers.remove(CUSTOMER_ID);
        assertRejected(() -> prepare(Map.of(PRODUCT_ID, 1), 10), "Unknown customer");
        customers.put(CUSTOMER_ID, customer);
        products.remove(PRODUCT_ID);
        assertRejected(() -> prepare(Map.of(PRODUCT_ID, 1), 10), "Unknown product");
        products.put(PRODUCT_ID, product(PRODUCT_ID, "12.00"));
        PlaceOrderInteractor.ConfirmationPlan plan = prepare(Map.of(PRODUCT_ID, 1), 10);
        assertMoney("12", plan.order().getSubtotal());
        assertMoney("11", plan.order().getTotal());
        assertOriginalState();
    }

    @Test
    void laterLineFailureDoesNotPartiallyDeductEarlierStockOrPoints() {
        Map<UUID, Integer> request = new LinkedHashMap<>();
        request.put(PRODUCT_ID, 1);
        request.put(SECOND_PRODUCT_ID, 21);
        assertRejected(() -> prepare(request, 1), "Insufficient stock");
        assertOriginalState();
    }

    @Test
    void invalidOrderMetadataDoesNotDeductStockOrPoints() {
        assertThrows(IllegalArgumentException.class, () -> interactor.prepareConfirmation(ORDER_ID, " ", CUSTOMER_ID,
            Map.of(PRODUCT_ID, 1), 1, FulfillmentType.DELIVERY, null, null));
        assertOriginalState();
    }

    @Test
    void rejectsInvalidPolicyOutputWithoutMutation() {
        for (BigDecimal invalid : new BigDecimal[] {null, new BigDecimal("-0.01"), new BigDecimal("11")}) {
            PlaceOrderInteractor subject = subject(points -> invalid);
            assertThrows(IllegalArgumentException.class, () -> subject.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 1));
            assertOriginalState();
        }
    }

    @Test
    void rejectsMismatchedAccountAndStockAssociations() {
        LoyaltyAccount originalAccount = accounts.put(CUSTOMER_ID,
            new LoyaltyAccount(new UUID(0, 8), new UUID(0, 99), 100, 0));
        assertRejected(() -> prepare(Map.of(PRODUCT_ID, 1), 1), "does not belong");
        accounts.put(CUSTOMER_ID, originalAccount);
        stocks.put(PRODUCT_ID, new Stock(new UUID(0, 9), SECOND_PRODUCT_ID, 10, 4));
        assertRejected(() -> prepare(Map.of(PRODUCT_ID, 1), 1), "does not belong");
        assertEquals(100, originalAccount.getPointsBalance());
        assertEquals(10, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(0, saveAttempts);
    }

    @Test
    void propagatesUnexpectedLookupFailures() {
        IllegalStateException failure = new IllegalStateException("Catalog unavailable");
        PlaceOrderInteractor subject = new PlaceOrderInteractor(customers::get, id -> { throw failure; },
            stockRepository, accounts::get, points -> BigDecimal.ZERO);
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> subject.preview(CUSTOMER_ID, Map.of(PRODUCT_ID, 1), 0)));
        assertOriginalState();
    }

    @Test
    void inputBoundaryPreviewsExactAmountsWithoutMutationsOrOrderIdentity() {
        PlaceOrderRequestHandler handler = interactor;
        PlaceOrderRequest request = new PlaceOrderRequest(CUSTOMER_ID,
            List.of(new OrderItemRequest(PRODUCT_ID, 2), new OrderItemRequest(SECOND_PRODUCT_ID, 3)), 2, false);
        PlaceOrderResponse response = handler.handle(request);
        assertTrue(response.isSuccess());
        assertNull(response.getOrderId());
        assertMoney("20.30", response.getSubtotal());
        assertMoney("0.20", response.getDiscount());
        assertMoney("20.10", response.getTotal());
        assertEquals(2, response.getRedeemedPoints());
        assertEquals("Preview calculated", response.getMessage());
        assertOriginalState();
    }

    @Test
    void inputBoundaryConfirmationIsValidatedButNeverReportedAsCommitted() {
        PlaceOrderResponse response = interactor.handle(input(true, 30, new OrderItemRequest(PRODUCT_ID, 2)));
        assertFalse(response.isSuccess());
        assertNull(response.getOrderId());
        assertMoney("20", response.getSubtotal());
        assertMoney("3", response.getDiscount());
        assertMoney("17", response.getTotal());
        assertEquals(0, response.getRedeemedPoints());
        assertEquals("Confirmation validated, awaiting atomic commit", response.getMessage());
        assertOriginalState();
    }

    @Test
    void inputBoundaryRejectsNullRequestPredictably() {
        PlaceOrderResponse response = interactor.handle(null);
        assertValidationFailure(response, "request is required");
        assertOriginalState();
    }

    @Test
    void inputBoundaryReportsUnknownCustomerOrProductForBothOperations() {
        for (boolean confirmed : new boolean[] {false, true}) {
            PlaceOrderRequest unknownCustomer = new PlaceOrderRequest(new UUID(0, 99),
                List.of(new OrderItemRequest(PRODUCT_ID, 1)), 0, confirmed);
            assertValidationFailure(interactor.handle(unknownCustomer), "Unknown customer");
            assertValidationFailure(interactor.handle(input(confirmed, 0,
                new OrderItemRequest(new UUID(0, 99), 1))), "Unknown product");
        }
        assertOriginalState();
    }

    @Test
    void inputBoundaryReportsInsufficientStockPointsAndDiscountLimit() {
        for (boolean confirmed : new boolean[] {false, true}) {
            assertValidationFailure(interactor.handle(input(confirmed, 0,
                new OrderItemRequest(PRODUCT_ID, 11))), "Insufficient stock");
            assertValidationFailure(interactor.handle(input(confirmed, 101,
                new OrderItemRequest(PRODUCT_ID, 10))), "Insufficient loyalty points");
            assertValidationFailure(interactor.handle(input(confirmed, 2,
                new OrderItemRequest(SECOND_PRODUCT_ID, 1))), "cannot exceed subtotal");
        }
        assertOriginalState();
    }

    @Test
    void inputBoundaryCombinesRepeatedProductsBeforeStockValidation() {
        PlaceOrderResponse valid = interactor.handle(input(false, 0,
            new OrderItemRequest(PRODUCT_ID, 2), new OrderItemRequest(PRODUCT_ID, 3)));
        assertTrue(valid.isSuccess());
        assertMoney("50", valid.getSubtotal());
        assertMoney("50", valid.getTotal());
        assertValidationFailure(interactor.handle(input(true, 0,
            new OrderItemRequest(PRODUCT_ID, 6), new OrderItemRequest(PRODUCT_ID, 5))), "Insufficient stock");
        assertValidationFailure(interactor.handle(input(true, 0,
            new OrderItemRequest(PRODUCT_ID, Integer.MAX_VALUE), new OrderItemRequest(PRODUCT_ID, 1))), "integer range");
        assertOriginalState();
    }

    @Test
    void inputBoundaryRechecksStockOnConfirmationAfterPreview() {
        assertTrue(interactor.handle(input(false, 10, new OrderItemRequest(PRODUCT_ID, 5))).isSuccess());
        stocks.get(PRODUCT_ID).decrease(6);
        assertValidationFailure(interactor.handle(input(true, 10,
            new OrderItemRequest(PRODUCT_ID, 5))), "Insufficient stock");
        assertEquals(4, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(4, stocks.get(PRODUCT_ID).getVersion());
        assertEquals(100, accounts.get(CUSTOMER_ID).getPointsBalance());
        assertEquals(3, accounts.get(CUSTOMER_ID).getVersion());
        assertEquals(0, saveAttempts);
    }

    @Test
    void inputBoundaryRechecksPointsOnConfirmationAfterPreview() {
        assertTrue(interactor.handle(input(false, 50, new OrderItemRequest(PRODUCT_ID, 1))).isSuccess());
        accounts.get(CUSTOMER_ID).redeemPoints(60);
        assertValidationFailure(interactor.handle(input(true, 50,
            new OrderItemRequest(PRODUCT_ID, 1))), "Insufficient loyalty points");
        assertEquals(40, accounts.get(CUSTOMER_ID).getPointsBalance());
        assertEquals(3, accounts.get(CUSTOMER_ID).getVersion());
        assertEquals(10, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(4, stocks.get(PRODUCT_ID).getVersion());
        assertEquals(0, saveAttempts);
    }

    @Test
    void inputBoundaryPreservesZeroPointPreviewWithoutLoyaltyAccount() {
        accounts.clear();
        PlaceOrderResponse response = interactor.handle(input(false, 0, new OrderItemRequest(PRODUCT_ID, 1)));
        assertTrue(response.isSuccess());
        assertMoney("0", response.getDiscount());
        assertMoney("10", response.getTotal());
        assertEquals(0, response.getRedeemedPoints());
        assertEquals(10, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(4, stocks.get(PRODUCT_ID).getVersion());
        assertTrue(accounts.isEmpty());
        assertEquals(0, saveAttempts);
    }

    @Test
    void inputBoundaryPropagatesUnexpectedFailures() {
        IllegalStateException failure = new IllegalStateException("Catalog unavailable");
        PlaceOrderRequestHandler broken = new PlaceOrderInteractor(customers::get, id -> { throw failure; },
            stockRepository, accounts::get, points -> BigDecimal.ZERO);
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> broken.handle(input(false, 0, new OrderItemRequest(PRODUCT_ID, 1)))));
        assertOriginalState();
    }

    private static PlaceOrderRequest input(boolean confirmed, int points, OrderItemRequest... items) {
        return new PlaceOrderRequest(CUSTOMER_ID, List.of(items), points, confirmed);
    }

    private static void assertValidationFailure(PlaceOrderResponse response, String expectedMessage) {
        assertFalse(response.isSuccess());
        assertNull(response.getOrderId());
        assertNull(response.getSubtotal());
        assertNull(response.getDiscount());
        assertNull(response.getTotal());
        assertEquals(0, response.getRedeemedPoints());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains(expectedMessage), response.getMessage());
    }

    @Test
    void inputBoundaryPropagatesUnexpectedArgumentFailuresFromDependencies() {
        IllegalArgumentException failure = new IllegalArgumentException("Dependency programming error");
        StockRepository brokenStock = new StockRepository() {
            @Override
            public Stock findByProductId(UUID productId) {
                throw failure;
            }

            @Override
            public boolean saveIfVersionMatches(Stock stock) {
                fail("Validation must not save stock");
                return false;
            }
        };
        List<PlaceOrderInteractor> subjects = List.of(
            new PlaceOrderInteractor(id -> { throw failure; }, products::get,
                stockRepository, accounts::get, points -> BigDecimal.ZERO),
            new PlaceOrderInteractor(customers::get, id -> { throw failure; },
                stockRepository, accounts::get, points -> BigDecimal.ZERO),
            new PlaceOrderInteractor(customers::get, products::get,
                brokenStock, accounts::get, points -> BigDecimal.ZERO),
            new PlaceOrderInteractor(customers::get, products::get,
                stockRepository, id -> { throw failure; }, points -> BigDecimal.ZERO),
            subject(points -> { throw failure; }),
            new PlaceOrderInteractor(customers::get, products::get, stockRepository,
                new gateways.LoyaltyRepository() {
                    @Override
                    public LoyaltyAccount findByCustomerId(UUID customerId) {
                        return accounts.get(customerId);
                    }

                    @Override
                    public void save(LoyaltyAccount account) {
                        fail("Validation must not save loyalty");
                    }
                }, (order, changes, account) -> {
                    fail("Dependency failure must prevent commit");
                    return false;
                }, points -> { throw failure; },
                request -> new PlaceOrderInteractor.ConfirmationContext(ORDER_ID, "ORDER-001",
                    FulfillmentType.DELIVERY, null, null)));
        for (PlaceOrderInteractor subject : subjects) {
            for (boolean confirmed : new boolean[] {false, true}) {
                assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> subject.handle(input(confirmed, 0, new OrderItemRequest(PRODUCT_ID, 1)))));
                assertOriginalState();
            }
        }
    }

    private PlaceOrderInteractor subject(LoyaltyDiscountPolicy policy) {
        return new PlaceOrderInteractor(customers::get, products::get, stockRepository, accounts::get, policy);
    }

    private PlaceOrderInteractor.ConfirmationPlan prepare(Map<UUID, Integer> quantities, int points) {
        return interactor.prepareConfirmation(ORDER_ID, "ORDER-001", CUSTOMER_ID, quantities, points,
            FulfillmentType.IN_STORE_PICKUP, "Gift wrap", null);
    }

    private void assertOriginalState() {
        assertEquals(10, stocks.get(PRODUCT_ID).getAvailableQuantity());
        assertEquals(4, stocks.get(PRODUCT_ID).getVersion());
        assertEquals(20, stocks.get(SECOND_PRODUCT_ID).getAvailableQuantity());
        assertEquals(7, stocks.get(SECOND_PRODUCT_ID).getVersion());
        assertEquals(100, accounts.get(CUSTOMER_ID).getPointsBalance());
        assertEquals(3, accounts.get(CUSTOMER_ID).getVersion());
        assertEquals(0, saveAttempts);
    }

    private static Product product(UUID id, String price) {
        return new Product(id, "Aura", ProductCategory.PERFUME, new BigDecimal(price), null);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable action, String message) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, action);
        assertTrue(error.getMessage().contains(message), error.getMessage());
    }
}
