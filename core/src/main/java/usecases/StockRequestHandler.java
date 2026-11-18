package usecases;

import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;

/** Input boundary for U1 stock requests at A3 Step 17. */
public interface StockRequestHandler {
    /** Handles a request and returns its business outcome. */
    StockResponse handle(StockRequest request);
}
