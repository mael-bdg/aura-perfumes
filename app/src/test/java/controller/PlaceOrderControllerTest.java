package controller;

import datatransferobjects.OrderItemRequest;
import datatransferobjects.PlaceOrderRequest;
import datatransferobjects.PlaceOrderResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import presenter.PlaceOrderPresenter;
import view.ConsoleOrderView;
import usecases.PlaceOrderRequestHandler;
import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderControllerTest {
    private static final UUID CUSTOMER_ID = new UUID(0, 1);
    private static final UUID PRODUCT_ID = new UUID(0, 2);
    private static final UUID SECOND_PRODUCT_ID = new UUID(0, 3);
    private final List<PlaceOrderRequest> requests = new ArrayList<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    private PlaceOrderResponse response = new PlaceOrderResponse(true, null, new BigDecimal("20.30"),
        new BigDecimal("0.20"), new BigDecimal("20.10"), 2, "Preview calculated");
    private final PlaceOrderRequestHandler handler = request -> {
        requests.add(request);
        return response;
    };

    @Test
    void publicUmlMethodsBuildPreviewAndConfirmationRequests() {
        PlaceOrderController controller = controller("");
        List<OrderItemRequest> items = List.of(new OrderItemRequest(PRODUCT_ID, 2));
        controller.previewOrder(CUSTOMER_ID, items, 3);
        controller.confirmOrder(CUSTOMER_ID, items, 4);
        assertEquals(2, requests.size());
        assertRequest(0, false, 3, List.of(PRODUCT_ID), List.of(2));
        assertRequest(1, true, 4, List.of(PRODUCT_ID), List.of(2));
    }

    @Test
    void submenuBuildsPreviewWithMultipleItems() {
        controller(lines("1", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), "2",
            SECOND_PRODUCT_ID.toString(), "3", "", "2", "0")).run();
        assertEquals(1, requests.size());
        assertRequest(0, false, 2, List.of(PRODUCT_ID, SECOND_PRODUCT_ID), List.of(2, 3));
        assertTrue(text().contains("Subtotal = 20.30"));
        assertTrue(text().contains("Discount = 0.20"));
        assertTrue(text().contains("Total = 20.10"));
        assertTrue(text().contains("Redeemed points = 2"));
        assertTrue(text().contains("Message = Preview calculated"));
    }

    @Test
    void submenuBuildsConfirmationAndDisplaysAwaitingCommitAccurately() {
        response = new PlaceOrderResponse(false, null, BigDecimal.TEN, BigDecimal.ONE,
            new BigDecimal("9"), 0, "Confirmation validated, awaiting atomic commit");
        controller(lines("2", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), "1", "", "10", "0")).run();
        assertEquals(1, requests.size());
        assertRequest(0, true, 10, List.of(PRODUCT_ID), List.of(1));
        assertTrue(text().contains("FAILURE"));
        assertTrue(text().contains("Confirmation validated, awaiting atomic commit"));
        assertFalse(text().contains("SUCCESS"));
        assertFalse(text().contains("Order placed"));
        assertFalse(text().contains("Order confirmed"));
        assertFalse(text().contains("Order saved"));
    }

    @Test
    void rejectsInvalidOrEmptyCustomerUuidAndRecovers() {
        for (String invalid : List.of("", "invalid", "1-1-1-1-1")) {
            controller(lines("1", invalid, "0")).run();
        }
        assertTrue(requests.isEmpty());
        assertTrue(text().contains("Invalid customer UUID"));
        controller(validPreview()).run();
        assertEquals(1, requests.size());
    }

    @Test
    void rejectsInvalidProductUuidAndRecovers() {
        controller(lines("1", CUSTOMER_ID.toString(), "invalid", "0")).run();
        assertTrue(requests.isEmpty());
        assertTrue(text().contains("Invalid product UUID"));
        controller(validPreview()).run();
        assertEquals(1, requests.size());
    }

    @Test
    void rejectsEmptyItemListThroughDtoValidation() {
        controller(lines("1", CUSTOMER_ID.toString(), "", "0", "0")).run();
        assertTrue(requests.isEmpty());
        assertTrue(text().contains("at least one product"));
        assertTrue(text().contains("Returning to main menu."));
    }

    @Test
    void rejectsNonpositiveAndMalformedQuantities() {
        for (String invalid : List.of("0", "-1", "abc", "1.5", "2147483648", "")) {
            controller(lines("1", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), invalid, "0")).run();
        }
        assertTrue(requests.isEmpty());
        assertTrue(text().contains("Product quantity must be positive"));
        assertTrue(text().contains("Quantity must be a whole number"));
    }

    @Test
    void rejectsNegativeAndMalformedLoyaltyPoints() {
        for (String invalid : List.of("-1", "abc", "1.5", "2147483648", "")) {
            controller(lines("2", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), "1", "", invalid, "0")).run();
        }
        assertTrue(requests.isEmpty());
        assertTrue(text().contains("Redeemed points cannot be negative"));
        assertTrue(text().contains("Loyalty points must be a whole number"));
    }

    @Test
    void rejectsInvalidPublicMethodArgumentsWithoutCallingHandler() {
        PlaceOrderController controller = controller("");
        List<OrderItemRequest> items = List.of(new OrderItemRequest(PRODUCT_ID, 1));
        controller.previewOrder(null, items, 0);
        controller.confirmOrder(CUSTOMER_ID, null, 0);
        controller.previewOrder(CUSTOMER_ID, List.of(), 0);
        controller.confirmOrder(CUSTOMER_ID, items, -1);
        assertTrue(requests.isEmpty());
        assertTrue(text().contains("FAILURE"));
    }

    @Test
    void invalidMenuChoicesAllowLaterValidPreviewInSameLoop() {
        controller(lines("bad", "99", "", "1", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), "1", "", "0", "0")).run();
        assertEquals(1, requests.size());
        assertEquals(3, text().lines().filter(line -> line.contains("Invalid menu choice")).count());
        assertTrue(text().contains("SUCCESS"));
    }

    @Test
    void businessFailureDisplaysNullAmountsSafelyAndReturnsToMenu() {
        response = new PlaceOrderResponse(false, null, null, null, null, 0, "Insufficient loyalty points");
        controller(validPreview()).run();
        assertTrue(text().contains("FAILURE"));
        assertTrue(text().contains("Subtotal = Unavailable"));
        assertTrue(text().contains("Discount = Unavailable"));
        assertTrue(text().contains("Total = Unavailable"));
        assertTrue(text().contains("Message = Insufficient loyalty points"));
        assertFalse(text().contains("null"));
        assertTrue(text().endsWith("Returning to main menu." + System.lineSeparator()));
    }

    @Test
    void returnLeavesParentInputAndOutputOpen() {
        Scanner sharedInput = new Scanner(lines("0", "parent-command"));
        new PlaceOrderController(handler, presenter(), sharedInput, output).run();
        assertEquals("parent-command", sharedInput.nextLine());
        assertTrue(requests.isEmpty());
        output.println("Stream still open");
        assertTrue(text().contains("Stream still open"));
    }

    @Test
    void endOfInputAtEveryPromptReturnsWithoutSubmittingPartialRequest() {
        for (String input : List.of("", lines("1"), lines("1", CUSTOMER_ID.toString()),
            lines("1", CUSTOMER_ID.toString(), PRODUCT_ID.toString()),
            lines("1", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), "1"),
            lines("1", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), "1", ""))) {
            assertDoesNotThrow(() -> controller(input).run());
        }
        assertTrue(requests.isEmpty());
    }

    @Test
    void parsingAcceptsWhitespaceAndUppercaseUuid() {
        controller(lines(" 1 ", " " + CUSTOMER_ID + " ", " " + PRODUCT_ID.toString().toUpperCase(java.util.Locale.ROOT) + " ",
            " 2 ", "", " 0 ", " 0 ")).run();
        assertEquals(1, requests.size());
        assertRequest(0, false, 0, List.of(PRODUCT_ID), List.of(2));
    }

    @Test
    void handlerFailuresAreNotMisreportedAsConsoleInputErrors() {
        IllegalArgumentException failure = new IllegalArgumentException("Unexpected handler failure");
        PlaceOrderRequestHandler broken = request -> { throw failure; };
        PlaceOrderController controller = new PlaceOrderController(broken, presenter(), new Scanner(validPreview()), output);
        assertSame(failure, assertThrows(IllegalArgumentException.class, controller::run));
        assertSame(failure, assertThrows(IllegalArgumentException.class,
            () -> controller.previewOrder(CUSTOMER_ID, List.of(new OrderItemRequest(PRODUCT_ID, 1)), 0)));
    }

    @Test
    void publicMethodsCallHandlerAndIndependentOutputBoundaryExactlyOncePerOperation() {
        List<PlaceOrderResponse> presented = new ArrayList<>();
        PlaceOrderController controller = new PlaceOrderController(handler, presented::add, new Scanner(""), output);
        List<OrderItemRequest> items = List.of(new OrderItemRequest(PRODUCT_ID, 1));
        controller.previewOrder(CUSTOMER_ID, items, 2);
        assertEquals(1, requests.size());
        assertEquals(1, presented.size());
        assertSame(response, presented.getFirst());
        assertRequest(0, false, 2, List.of(PRODUCT_ID), List.of(1));
        response = new PlaceOrderResponse(false, null, null, null, null, 0, "Insufficient stock");
        controller.confirmOrder(CUSTOMER_ID, items, 2);
        assertEquals(2, requests.size());
        assertEquals(2, presented.size());
        assertSame(response, presented.get(1));
        assertRequest(1, true, 2, List.of(PRODUCT_ID), List.of(1));
        assertEquals("", text());
    }

    @Test
    void submenuForwardsResponseThroughIndependentOutputBoundaryWithoutFormattingIt() {
        List<PlaceOrderResponse> presented = new ArrayList<>();
        new PlaceOrderController(handler, presented::add, new Scanner(validPreview()), output).run();
        assertEquals(1, requests.size());
        assertEquals(1, presented.size());
        assertSame(response, presented.getFirst());
        assertTrue(text().contains("Returning to main menu."));
        assertFalse(text().contains("SUCCESS"));
        assertFalse(text().contains("Subtotal ="));
        assertFalse(text().contains("Preview calculated"));
    }

    private OrderOutputBoundary presenter() {
        return new PlaceOrderPresenter(new ConsoleOrderView(output));
    }

    private PlaceOrderController controller(String input) {
        return new PlaceOrderController(handler, presenter(), new Scanner(input), output);
    }

    private void assertRequest(int index, boolean confirmed, int points, List<UUID> ids, List<Integer> quantities) {
        PlaceOrderRequest request = requests.get(index);
        assertEquals(CUSTOMER_ID, request.getCustomerId());
        assertEquals(confirmed, request.isConfirmed());
        assertEquals(points, request.getPointsToRedeem());
        assertEquals(ids, request.getItems().stream().map(OrderItemRequest::getProductId).toList());
        assertEquals(quantities, request.getItems().stream().map(OrderItemRequest::getQuantity).toList());
    }

    private static String validPreview() {
        return lines("1", CUSTOMER_ID.toString(), PRODUCT_ID.toString(), "1", "", "0", "0");
    }

    private static String lines(String... values) {
        return String.join("\n", values) + "\n";
    }

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
