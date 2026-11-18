package controller;

import datatransferobjects.PlaceOrderResponse;

/** Output boundary for U2 order results. */
public interface OrderOutputBoundary {
    /** Presents the supplied order response. */
    void present(PlaceOrderResponse response);
}
