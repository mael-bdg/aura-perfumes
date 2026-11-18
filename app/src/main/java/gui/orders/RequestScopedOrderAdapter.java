package gui.orders;

import datatransferobjects.PlaceOrderRequest;
import datatransferobjects.PlaceOrderResponse;
import entities.Customer;
import entities.FulfillmentType;
import gateways.LoyaltyRepository;
import gateways.OrderTransactionGateway;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import usecases.LoyaltyDiscountPolicy;
import usecases.PlaceOrderInteractor;
import usecases.PlaceOrderRequestHandler;
import usecases.ProductRepository;
import usecases.StockRepository;

/** Binds each U2 invocation to its immutable fulfillment choice and existing transaction gateway. */
public final class RequestScopedOrderAdapter implements OrderRequestAdapter {
    private final Function<UUID, Customer> customers;
    private final ProductRepository products;
    private final StockRepository stocks;
    private final LoyaltyRepository loyalty;
    private final OrderTransactionGateway transactions;
    private final LoyaltyDiscountPolicy policy;
    private final Supplier<UUID> orderIds;

    public RequestScopedOrderAdapter(Function<UUID, Customer> customers, ProductRepository products,
                                    StockRepository stocks, LoyaltyRepository loyalty,
                                    OrderTransactionGateway transactions, LoyaltyDiscountPolicy policy,
                                    Supplier<UUID> orderIds) {
        this.customers = Objects.requireNonNull(customers);
        this.products = Objects.requireNonNull(products);
        this.stocks = Objects.requireNonNull(stocks);
        this.loyalty = Objects.requireNonNull(loyalty);
        this.transactions = Objects.requireNonNull(transactions);
        this.policy = Objects.requireNonNull(policy);
        this.orderIds = Objects.requireNonNull(orderIds);
    }

    @Override
    public PlaceOrderResponse handle(PlaceOrderRequest request, FulfillmentType fulfillment) {
        Objects.requireNonNull(fulfillment, "Fulfillment choice is required");
        PlaceOrderRequestHandler handler = new PlaceOrderInteractor(customers, products, stocks, loyalty,
            transactions, policy, confirmation -> {
                UUID id = Objects.requireNonNull(orderIds.get(), "Order identity is required");
                return new PlaceOrderInteractor.ConfirmationContext(id, "DEMO-" + id, fulfillment, null, null);
            });
        return handler.handle(request);
    }
}
