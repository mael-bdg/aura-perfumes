package gui.orders;

import datatransferobjects.PlaceOrderRequest;
import datatransferobjects.PlaceOrderResponse;
import entities.FulfillmentType;

/** Application adapter supplies request-scoped fulfillment while preserving the UML DTO. */
@FunctionalInterface
public interface OrderRequestAdapter {
    PlaceOrderResponse handle(PlaceOrderRequest request, FulfillmentType fulfillment);
}
