package presenter;

import controller.StockOutputBoundary;
import datatransferobjects.StockResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockPresenterTest {
    private final UUID productId = UUID.randomUUID();
    private final List<String> messages = new ArrayList<>();
    private final StockOutputBoundary presenter = new StockPresenter(messages::add);

    @Test
    void formatsSuccessfulResponseAndForwardsOneMessageToViewBoundary() {
        presenter.present(new StockResponse(true, productId, "Aura", 12, "Stock checked"));
        assertEquals(List.of(String.join(System.lineSeparator(), "SUCCESS", "Product name = Aura",
            "Product UUID = " + productId, "Available quantity = 12", "Message = Stock checked")), messages);
    }

    @Test
    void formatsRejectedAdjustmentWithKnownUnchangedQuantity() {
        presenter.present(new StockResponse(false, productId, "Aura", 10, "Insufficient stock"));
        assertEquals(List.of(String.join(System.lineSeparator(), "FAILURE", "Product name = Aura",
            "Product UUID = " + productId, "Available quantity = 10", "Message = Insufficient stock")), messages);
    }

    @Test
    void displaysUnknownProductNameAndQuantityAsUnavailable() {
        presenter.present(new StockResponse(false, productId, null, -1, "Unknown product"));
        String message = messages.getFirst();
        assertTrue(message.contains("Product name = Unavailable"));
        assertTrue(message.contains("Product UUID = " + productId));
        assertTrue(message.contains("Available quantity = Unavailable"));
        assertFalse(message.contains("null"));
        assertFalse(message.contains("Available quantity = -1"));
    }

    @Test
    void preservesKnownNameWhenQuantityIsUnavailable() {
        presenter.present(new StockResponse(false, productId, "Aura", -1, "Stock update conflicts"));
        assertTrue(messages.getFirst().contains("Product name = Aura"));
        assertTrue(messages.getFirst().contains("Available quantity = Unavailable"));
        assertTrue(messages.getFirst().contains("Message = Stock update conflicts"));
    }

    @Test
    void formatsZeroAsKnownStockAndUnavailableUuidExplicitly() {
        presenter.present(new StockResponse(true, null, "Sample", 0, "Completed"));
        assertTrue(messages.getFirst().contains("Available quantity = 0"));
        assertTrue(messages.getFirst().contains("Product UUID = Unavailable"));
    }

    @Test
    void rejectsNullResponseBeforeSendingAnythingToView() {
        assertThrows(NullPointerException.class, () -> presenter.present(null));
        assertTrue(messages.isEmpty());
        assertThrows(NullPointerException.class, () -> new StockPresenter(null));
    }
}
