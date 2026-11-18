package presenter;

import controller.OrderOutputBoundary;
import datatransferobjects.PlaceOrderResponse;
import java.math.BigDecimal;
import java.util.Objects;

/** Formats U2 outcomes and delegates rendering through the view boundary. */
public final class PlaceOrderPresenter implements OrderOutputBoundary {
    private final OrderViewBoundary view;

    /** Creates a presenter using the supplied view boundary. */
    public PlaceOrderPresenter(OrderViewBoundary view) {
        this.view = Objects.requireNonNull(view, "Order view is required");
    }

    /**
     * Presents success and the handler's outcome message without inferring a commit
     * from an order UUID. Preview, committed and awaiting-commit messages are
     * preserved. Monetary output uses plain decimal notation, at least two decimal
     * places and any additional supplied precision, without rounding.
     */
    @Override
    public void present(PlaceOrderResponse response) {
        Objects.requireNonNull(response, "Order response is required");
        String message = String.join(System.lineSeparator(),
            response.isSuccess() ? "SUCCESS" : "FAILURE",
            "Order UUID = " + Objects.toString(response.getOrderId(), "Unavailable"),
            "Subtotal = " + formatAmount(response.getSubtotal()),
            "Discount = " + formatAmount(response.getDiscount()),
            "Total = " + formatAmount(response.getTotal()),
            "Redeemed points = " + (response.getRedeemedPoints() < 0 ? "Unavailable" : response.getRedeemedPoints()),
            "Message = " + response.getMessage());
        view.display(message);
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            return "Unavailable";
        }
        return amount.scale() < 2 ? amount.setScale(2).toPlainString() : amount.toPlainString();
    }
}
