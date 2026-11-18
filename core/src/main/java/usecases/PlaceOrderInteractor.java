package usecases;

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
import entities.Stock;
import gateways.LoyaltyRepository;
import gateways.OrderTransactionGateway;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * U2 business logic for A3 Steps 15–16. Customer and loyalty lookup functions
 * temporarily supply current entities, returning null for missing records.
 * Product quantities are keyed by product UUID, giving one requested quantity
 * per product. Expected validation failures throw IllegalArgumentException.
 * The formal handler supports atomic confirmation when supplied with a transaction
 * gateway and caller-provided confirmation context. The legacy constructor retains
 * validation-only behavior. Preview and plan preparation never persist state.
 */
public final class PlaceOrderInteractor implements PlaceOrderRequestHandler {
    private final Function<UUID, Customer> customerLookup;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final Function<UUID, LoyaltyAccount> loyaltyLookup;
    private final LoyaltyDiscountPolicy discountPolicy;
    private final OrderTransactionGateway transactionGateway;
    private final Function<PlaceOrderRequest, ConfirmationContext> confirmationContext;

    /** Creates the interactor with read-only lookups and an explicit discount policy. */
    public PlaceOrderInteractor(Function<UUID, Customer> customerLookup,
                                ProductRepository productRepository, StockRepository stockRepository,
                                Function<UUID, LoyaltyAccount> loyaltyLookup, LoyaltyDiscountPolicy discountPolicy) {
        this(customerLookup, productRepository, stockRepository, loyaltyLookup, discountPolicy, null, null);
    }

    /**
     * Enables atomic confirmation with formal gateways and an injected context resolver.
     * Customer lookup remains explicit because the UML defines no customer repository.
     * Context supplies order identity and fulfillment information absent from the DTO.
     */
    public PlaceOrderInteractor(Function<UUID, Customer> customerLookup,
                                ProductRepository productRepository, StockRepository stockRepository,
                                LoyaltyRepository loyaltyRepository, OrderTransactionGateway transactionGateway,
                                LoyaltyDiscountPolicy discountPolicy,
                                Function<PlaceOrderRequest, ConfirmationContext> confirmationContext) {
        this(customerLookup, productRepository, stockRepository,
            Objects.requireNonNull(loyaltyRepository, "Loyalty repository is required")::findByCustomerId,
            discountPolicy, Objects.requireNonNull(transactionGateway, "Transaction gateway is required"),
            Objects.requireNonNull(confirmationContext, "Confirmation context resolver is required"));
    }

    private PlaceOrderInteractor(Function<UUID, Customer> customerLookup,
                                 ProductRepository productRepository, StockRepository stockRepository,
                                 Function<UUID, LoyaltyAccount> loyaltyLookup, LoyaltyDiscountPolicy discountPolicy,
                                 OrderTransactionGateway transactionGateway,
                                 Function<PlaceOrderRequest, ConfirmationContext> confirmationContext) {
        this.customerLookup = Objects.requireNonNull(customerLookup, "Customer lookup is required");
        this.productRepository = Objects.requireNonNull(productRepository, "Product repository is required");
        this.stockRepository = Objects.requireNonNull(stockRepository, "Stock repository is required");
        this.loyaltyLookup = Objects.requireNonNull(loyaltyLookup, "Loyalty lookup is required");
        this.discountPolicy = Objects.requireNonNull(discountPolicy, "Discount policy is required");
        this.transactionGateway = transactionGateway;
        this.confirmationContext = confirmationContext;
    }

    /**
     * Handles the UML request. Preview success describes a
     * completed calculation, with projected redeemed points and no order UUID.
     * Configured confirmation reports success only after an atomic commit.
     * Legacy confirmation returns unsuccessful while awaiting an atomic gateway.
     * Validation failures have null monetary values and zero redeemed points.
     * Explicit business validation failures become responses. Unexpected
     * programming or infrastructure exceptions propagate to the caller.
     */
    @Override
    public PlaceOrderResponse handle(PlaceOrderRequest request) {
        if (request == null) {
            return validationFailure("Order request is required");
        }
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        try {
            for (OrderItemRequest item : request.getItems()) {
                int existing = quantities.getOrDefault(item.getProductId(), 0);
                if (item.getQuantity() > Integer.MAX_VALUE - existing) {
                    throw new BusinessValidationException("Combined product quantity exceeds the integer range");
                }
                quantities.put(item.getProductId(), existing + item.getQuantity());
            }
        } catch (BusinessValidationException exception) {
            return validationFailure(exception.getMessage());
        }
        if (request.isConfirmed() && transactionGateway != null) {
            return confirm(request, quantities);
        }
        try {
            Preview result = preview(request.getCustomerId(), quantities, request.getPointsToRedeem());
            if (request.isConfirmed()) {
                return new PlaceOrderResponse(false, null, result.subtotal(), result.discount(), result.total(),
                    0, "Confirmation validated, awaiting atomic commit");
            }
            return new PlaceOrderResponse(true, null, result.subtotal(), result.discount(), result.total(),
                request.getPointsToRedeem(), "Preview calculated");
        } catch (BusinessValidationException exception) {
            return validationFailure(exception.getMessage());
        }
    }

    private PlaceOrderResponse confirm(PlaceOrderRequest request, Map<UUID, Integer> quantities) {
        ConfirmationContext context = confirmationContext.apply(request);
        if (context == null) {
            return validationFailure("Confirmation context is required");
        }
        ConfirmationPlan plan;
        try {
            plan = prepareConfirmation(context.orderId(), context.orderNumber(),
                request.getCustomerId(), quantities, request.getPointsToRedeem(), context.fulfillmentType(),
                context.packagingInstructions(), context.deliveryNotes());
        } catch (BusinessValidationException exception) {
            return validationFailure(exception.getMessage());
        }
        Order order = plan.order();
        if (!transactionGateway.commitOrder(order, plan.stocks(), plan.account())) {
            return new PlaceOrderResponse(false, null, order.getSubtotal(), order.getDiscount(), order.getTotal(),
                0, "Order transaction rejected due to a conflict, duplicate order, or insufficient resources");
        }
        return new PlaceOrderResponse(true, order.getOrderId(), order.getSubtotal(), order.getDiscount(),
            order.getTotal(), order.getRedeemedPoints(), "Order committed with status PENDING_CONFIRMATION");
    }

    private static PlaceOrderResponse validationFailure(String message) {
        return new PlaceOrderResponse(false, null, null, null, null, 0, message);
    }

    /** Validates current data and returns exact amounts without changing any entity. */
    public Preview preview(UUID customerId, Map<UUID, Integer> quantities, int points) {
        return validate(customerId, quantities, points).preview();
    }

    /**
     * Reloads current data and prepares a PENDING_CONFIRMATION order at version zero.
     * Quantities and points are deducted only from detached copies, retaining their
     * original versions for a later atomic commit. A plan is not a reservation or
     * a committed order. The future transaction gateway must recheck all versions
     * and commit the order, stock and optional account together without partial writes.
     */
    public ConfirmationPlan prepareConfirmation(UUID orderId, String orderNumber, UUID customerId,
                                                Map<UUID, Integer> quantities, int points,
                                                FulfillmentType fulfillmentType, String packagingInstructions,
                                                String deliveryNotes) {
        ValidatedOrder validated = validate(customerId, quantities, points);
        Preview preview = validated.preview();
        Order order = new Order(orderId, orderNumber, customerId, preview.items(), preview.subtotal(),
            preview.discount(), preview.total(), points, OrderStatus.PENDING_CONFIRMATION,
            fulfillmentType, packagingInstructions, deliveryNotes, null, null, 0);
        for (int index = 0; index < preview.items().size(); index++) {
            validated.stocks().get(index).decrease(preview.items().get(index).getQuantity());
        }
        LoyaltyAccount account = validated.account();
        if (points > 0) {
            account.redeemPoints(points);
        }
        return new ConfirmationPlan(order, validated.stocks(), points > 0 ? account : null);
    }

    private ValidatedOrder validate(UUID customerId, Map<UUID, Integer> quantities, int points) {
        if (customerId == null) {
            throw new BusinessValidationException("Customer ID is required");
        }
        Customer customer = customerLookup.apply(customerId);
        if (customer == null) {
            throw new BusinessValidationException("Unknown customer: " + customerId);
        }
        if (!customerId.equals(customer.getCustomerId())) {
            throw new BusinessValidationException("Customer lookup returned a different customer");
        }
        if (quantities == null || quantities.isEmpty()) {
            throw new BusinessValidationException("An order must contain at least one product");
        }
        if (points < 0) {
            throw new BusinessValidationException("Redeemed points cannot be negative");
        }
        Map<UUID, Integer> requested = new LinkedHashMap<>(quantities);
        List<OrderItem> items = new ArrayList<>();
        List<Stock> stocks = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> entry : requested.entrySet()) {
            UUID productId = entry.getKey();
            Integer quantity = entry.getValue();
            if (productId == null) {
                throw new BusinessValidationException("Product ID is required");
            }
            if (quantity == null || quantity <= 0) {
                throw new BusinessValidationException("Product quantity must be positive");
            }
            Product product = productRepository.findById(productId);
            if (product == null) {
                throw new BusinessValidationException("Unknown product: " + productId);
            }
            if (!productId.equals(product.getProductId())) {
                throw new BusinessValidationException("Product lookup returned a different product");
            }
            Stock storedStock = stockRepository.findByProductId(productId);
            if (storedStock == null) {
                throw new BusinessValidationException("Missing stock for product: " + productId);
            }
            if (!productId.equals(storedStock.getProductId())) {
                throw new BusinessValidationException("Stock does not belong to the requested product");
            }
            Stock stock = new Stock(storedStock.getId(), productId,
                storedStock.getAvailableQuantity(), storedStock.getVersion());
            if (quantity > stock.getAvailableQuantity()) {
                throw new BusinessValidationException("Insufficient stock for product: " + productId);
            }
            OrderItem item = new OrderItem(UUID.randomUUID(), productId, quantity, product.getUnitPrice());
            items.add(item);
            stocks.add(stock);
            subtotal = subtotal.add(item.getLineTotal());
        }
        LoyaltyAccount storedAccount = loyaltyLookup.apply(customerId);
        LoyaltyAccount account = null;
        if (storedAccount != null) {
            if (!customerId.equals(storedAccount.getCustomerId())) {
                throw new BusinessValidationException("Loyalty account does not belong to the customer");
            }
            account = new LoyaltyAccount(storedAccount.getId(), customerId,
                storedAccount.getPointsBalance(), storedAccount.getVersion());
        }
        if (points > 0 && (account == null || points > account.getPointsBalance())) {
            throw new BusinessValidationException("Insufficient loyalty points");
        }
        BigDecimal discount = discountPolicy.calculateDiscount(points);
        if (discount == null || discount.signum() < 0) {
            throw new BusinessValidationException("Loyalty discount must be nonnegative");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessValidationException("Loyalty discount cannot exceed subtotal");
        }
        Preview preview = new Preview(items, subtotal, discount, subtotal.subtract(discount));
        return new ValidatedOrder(preview, stocks, account);
    }

    /** Temporary calculation result using immutable line items and exact amounts. */
    public record Preview(List<OrderItem> items, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
        /** Copies the supplied line collection. */
        public Preview {
            items = List.copyOf(items);
        }
    }

    /**
     * Temporary, uncommitted operation plan. Entities are caller-owned detached
     * copies and may be changed by the caller. The stock list itself is immutable.
     * A null account means that no loyalty write is needed for zero redemption.
     */
    public record ConfirmationPlan(Order order, List<Stock> stocks, LoyaltyAccount account) {
        /** Copies the supplied stock collection. */
        public ConfirmationPlan {
            stocks = List.copyOf(stocks);
        }
    }

    /** Explicit caller-provided identity and fulfillment information for one confirmation. */
    public record ConfirmationContext(UUID orderId, String orderNumber, FulfillmentType fulfillmentType,
                                      String packagingInstructions, String deliveryNotes) {
        /** Validates required configuration without supplying business defaults. */
        public ConfirmationContext {
            Objects.requireNonNull(orderId, "Order ID is required");
            Objects.requireNonNull(orderNumber, "Order number is required");
            Objects.requireNonNull(fulfillmentType, "Fulfillment type is required");
            if (orderNumber.isBlank()) {
                throw new IllegalArgumentException("Order number cannot be blank");
            }
        }
    }

    /** Identifies expected request failures without masking dependency exceptions. */
    private static final class BusinessValidationException extends IllegalArgumentException {
        private BusinessValidationException(String message) {
            super(message);
        }
    }

    private record ValidatedOrder(Preview preview, List<Stock> stocks, LoyaltyAccount account) { }
}
