package presenter;

import controller.StockOutputBoundary;
import datatransferobjects.StockResponse;
import java.util.Objects;

/** Formats U1 responses and forwards them to an injected view boundary. */
public final class StockPresenter implements StockOutputBoundary {
    private final StockViewBoundary view;

    /** Creates a presenter using the supplied view. */
    public StockPresenter(StockViewBoundary view) {
        this.view = Objects.requireNonNull(view, "Stock view is required");
    }

    /** Formats known values and labels unavailable response values explicitly. */
    @Override
    public void present(StockResponse response) {
        Objects.requireNonNull(response, "Stock response is required");
        String message = String.join(System.lineSeparator(),
            response.isSuccess() ? "SUCCESS" : "FAILURE",
            "Product name = " + Objects.toString(response.getProductName(), "Unavailable"),
            "Product UUID = " + Objects.toString(response.getProductId(), "Unavailable"),
            "Available quantity = " + (response.getAvailableQuantity() < 0 ? "Unavailable" : response.getAvailableQuantity()),
            "Message = " + Objects.toString(response.getMessage(), "Unavailable"));
        view.display(message);
    }
}
