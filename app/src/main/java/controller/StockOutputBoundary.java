package controller;

import datatransferobjects.StockResponse;

/** Output boundary for U1 stock operation results. */
public interface StockOutputBoundary {
    /** Presents a stock response. */
    void present(StockResponse response);
}
