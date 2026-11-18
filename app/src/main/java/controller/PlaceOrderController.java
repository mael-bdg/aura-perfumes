package controller;

import datatransferobjects.OrderItemRequest;
import datatransferobjects.PlaceOrderRequest;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import usecases.PlaceOrderRequestHandler;

/**
 * U2 controller delegating business results through its output boundary.
 * The submenu uses caller-owned streams for application navigation.
 */
public final class PlaceOrderController {
    private final PlaceOrderRequestHandler requestHandler;
    private final OrderOutputBoundary outputBoundary;
    private final Scanner input;
    private final PrintStream output;

    /** Creates a controller using the UML dependencies and the process console. */
    public PlaceOrderController(PlaceOrderRequestHandler requestHandler, OrderOutputBoundary outputBoundary) {
        this(requestHandler, outputBoundary, new Scanner(System.in), System.out);
    }

    /** Creates a controller with injected boundaries and externally managed streams. */
    public PlaceOrderController(PlaceOrderRequestHandler requestHandler, OrderOutputBoundary outputBoundary,
                                Scanner input, PrintStream output) {
        this.requestHandler = Objects.requireNonNull(requestHandler, "Request handler is required");
        this.outputBoundary = Objects.requireNonNull(outputBoundary, "Order output boundary is required");
        this.input = Objects.requireNonNull(input, "Console input is required");
        this.output = Objects.requireNonNull(output, "Console output is required");
    }

    /** Constructs and submits a preview request using the UML signature. */
    public void previewOrder(UUID customerId, List<OrderItemRequest> items, int points) {
        submit(customerId, items, points, false);
    }

    /** Requests confirmation, displaying the handler's actual outcome without assuming a commit. */
    public void confirmOrder(UUID customerId, List<OrderItemRequest> items, int points) {
        submit(customerId, items, points, true);
    }

    private void submit(UUID customerId, List<OrderItemRequest> items, int points, boolean confirmed) {
        PlaceOrderRequest request;
        try {
            request = new PlaceOrderRequest(customerId, items, points, confirmed);
        } catch (IllegalArgumentException | NullPointerException exception) {
            displayInputFailure(exception);
            return;
        }
        outputBoundary.present(requestHandler.handle(request));
    }

    /** Runs the submenu until return or end of input, leaving the shared Scanner open. */
    public void run() {
        while (true) {
            output.println("1. Preview order\n2. Request order confirmation\n0. Return to main menu");
            String command = readLine("Choose an operation");
            if (command == null || command.equals("0")) {
                output.println("Returning to main menu.");
                return;
            }
            if (!command.equals("1") && !command.equals("2")) {
                output.println("FAILURE - Invalid menu choice. Enter 1, 2, or 0.");
                continue;
            }
            PlaceOrderRequest request;
            try {
                request = readRequest(command.equals("2"));
            } catch (IllegalArgumentException | NullPointerException exception) {
                displayInputFailure(exception);
                continue;
            }
            if (request == null) {
                output.println("Returning to main menu.");
                return;
            }
            outputBoundary.present(requestHandler.handle(request));
        }
    }

    private PlaceOrderRequest readRequest(boolean confirmed) {
        String customerText = readLine("Enter the customer UUID");
        if (customerText == null) {
            return null;
        }
        UUID customerId = parseUuid(customerText, "customer");
        List<OrderItemRequest> items = new ArrayList<>();
        while (true) {
            String productText = readLine("Enter a product UUID, or leave blank to finish items");
            if (productText == null) {
                return null;
            }
            if (productText.isEmpty()) {
                break;
            }
            UUID productId = parseUuid(productText, "product");
            String quantityText = readLine("Enter the product quantity");
            if (quantityText == null) {
                return null;
            }
            items.add(new OrderItemRequest(productId, parseInteger(quantityText, "Quantity")));
        }
        String pointsText = readLine("Enter loyalty points to redeem");
        if (pointsText == null) {
            return null;
        }
        return new PlaceOrderRequest(customerId, items, parseInteger(pointsText, "Loyalty points"), confirmed);
    }

    private static UUID parseUuid(String text, String label) {
        try {
            UUID id = UUID.fromString(text);
            if (!id.toString().equalsIgnoreCase(text)) {
                throw new IllegalArgumentException("UUID must use the standard format");
            }
            return id;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + label + " UUID. Use the standard UUID format.");
        }
    }

    private static int parseInteger(String text, String label) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number within the integer range.");
        }
    }

    private void displayInputFailure(RuntimeException exception) {
        output.println("FAILURE - " + Objects.toString(exception.getMessage(), "Invalid order input"));
    }

    private String readLine(String prompt) {
        output.println(prompt);
        output.flush();
        return input.hasNextLine() ? input.nextLine().strip() : null;
    }
}
