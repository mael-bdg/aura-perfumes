package presenter;

import controller.OrderOutputBoundary;
import datatransferobjects.PlaceOrderResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderPresenterTest {
    private static final UUID ORDER_ID = new UUID(0, 1);
    private final List<String> messages = new ArrayList<>();
    private final OrderOutputBoundary presenter = new PlaceOrderPresenter(messages::add);

    @Test
    void formatsSuccessfulPreviewAndDelegatesExactlyOnce() {
        presenter.present(new PlaceOrderResponse(true, null, new BigDecimal("20.30"),
            new BigDecimal("0.20"), new BigDecimal("20.10"), 2, "Preview calculated"));
        assertEquals(List.of(String.join(System.lineSeparator(), "SUCCESS", "Order UUID = Unavailable",
            "Subtotal = 20.30", "Discount = 0.20", "Total = 20.10", "Redeemed points = 2",
            "Message = Preview calculated")), messages);
        assertFalse(messages.getFirst().contains("committed"));
    }

    @Test
    void formatsCommittedOrderWithUuidAndActualOutcomeMessage() {
        presenter.present(new PlaceOrderResponse(true, ORDER_ID, BigDecimal.TEN,
            BigDecimal.ONE, new BigDecimal("9"), 10, "Order committed with status PENDING_CONFIRMATION"));
        assertEquals(List.of(String.join(System.lineSeparator(), "SUCCESS", "Order UUID = " + ORDER_ID,
            "Subtotal = 10.00", "Discount = 1.00", "Total = 9.00", "Redeemed points = 10",
            "Message = Order committed with status PENDING_CONFIRMATION")), messages);
    }

    @Test
    void formatsBusinessFailureWithUnavailableMonetaryValues() {
        presenter.present(new PlaceOrderResponse(false, null, null, null, null, 0, "Unknown customer"));
        assertEquals(List.of(String.join(System.lineSeparator(), "FAILURE", "Order UUID = Unavailable",
            "Subtotal = Unavailable", "Discount = Unavailable", "Total = Unavailable",
            "Redeemed points = 0", "Message = Unknown customer")), messages);
        assertFalse(messages.getFirst().contains("null"));
    }

    @Test
    void preservesAwaitingCommitMessageAndDoesNotAnnounceSuccess() {
        presenter.present(new PlaceOrderResponse(false, null, BigDecimal.TEN, BigDecimal.ONE,
            new BigDecimal("9"), 0, "Confirmation validated, awaiting atomic commit"));
        assertEquals(1, messages.size());
        String message = messages.getFirst();
        assertTrue(message.startsWith("FAILURE"));
        assertTrue(message.contains("Message = Confirmation validated, awaiting atomic commit"));
        assertFalse(message.contains("SUCCESS"));
        assertFalse(message.contains("Order committed"));
    }

    @Test
    void nonnullUuidOnFailureDoesNotImplyPersistenceSuccess() {
        presenter.present(new PlaceOrderResponse(false, ORDER_ID, BigDecimal.TEN, BigDecimal.ZERO,
            BigDecimal.TEN, 0, "Transaction rejected"));
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().startsWith("FAILURE"));
        assertTrue(messages.getFirst().contains("Order UUID = " + ORDER_ID));
        assertTrue(messages.getFirst().contains("Message = Transaction rejected"));
        assertFalse(messages.getFirst().contains("committed"));
        assertFalse(messages.getFirst().contains("SUCCESS"));
    }

    @Test
    void usesPlainDecimalNotationAndRetainsAdditionalPrecisionWithoutRounding() {
        presenter.present(new PlaceOrderResponse(true, null, new BigDecimal("1E+3"),
            new BigDecimal("0.0125"), new BigDecimal("999.9875"), 1, "Preview calculated"));
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("Subtotal = 1000.00"));
        assertTrue(messages.getFirst().contains("Discount = 0.0125"));
        assertTrue(messages.getFirst().contains("Total = 999.9875"));
        assertFalse(messages.getFirst().contains("E+"));
    }

    @Test
    void negativePlaceholdersAreRenderedAsUnavailable() {
        presenter.present(new PlaceOrderResponse(false, null, new BigDecimal("-1"),
            new BigDecimal("-1"), new BigDecimal("-1"), -1, "Amounts unavailable"));
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("Subtotal = Unavailable"));
        assertTrue(messages.getFirst().contains("Discount = Unavailable"));
        assertTrue(messages.getFirst().contains("Total = Unavailable"));
        assertTrue(messages.getFirst().contains("Redeemed points = Unavailable"));
        assertFalse(messages.getFirst().contains("-1"));
    }

    @Test
    void rejectsMissingResponseBeforeCallingViewAndRequiresViewBoundary() {
        assertThrows(NullPointerException.class, () -> presenter.present(null));
        assertTrue(messages.isEmpty());
        assertThrows(NullPointerException.class, () -> new PlaceOrderPresenter(null));
    }
}
