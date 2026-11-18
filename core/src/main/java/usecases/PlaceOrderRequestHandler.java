package usecases;

import datatransferobjects.PlaceOrderRequest;
import datatransferobjects.PlaceOrderResponse;

/** Formal U2 input boundary from the UUID-corrected UML. */
public interface PlaceOrderRequestHandler {
    /** Handles a preview or confirmation request and returns its outcome. */
    PlaceOrderResponse handle(PlaceOrderRequest request);
}
