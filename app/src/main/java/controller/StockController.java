package controller;

import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import usecases.StockRequestHandler;

/**
 * U1 controller delegating operation results through its output boundary.
 * Owns the U1 submenu and uses caller-supplied streams for application navigation.
 */
public final class StockController {
    private final StockRequestHandler requestHandler;
    private final StockOutputBoundary outputBoundary;
    private final Scanner input;
    private final PrintStream output;

    /** Creates a controller using the process console without taking ownership of it. */
    public StockController(StockRequestHandler requestHandler, StockOutputBoundary outputBoundary) {
        this(requestHandler, outputBoundary, new Scanner(System.in), System.out);
    }

    /** Creates a controller with caller-owned streams shared with the application menu. */
    public StockController(StockRequestHandler requestHandler, StockOutputBoundary outputBoundary,
                    Scanner input, PrintStream output) {
        this.requestHandler = Objects.requireNonNull(requestHandler, "Request handler is required");
        this.outputBoundary = Objects.requireNonNull(outputBoundary, "Stock output boundary is required");
        this.input = Objects.requireNonNull(input, "Console input is required");
        this.output = Objects.requireNonNull(output, "Console output is required");
    }

    /** Converts the product UUID, submits CHECK, and displays its response. */
    public void checkStock(String productId) {
        submit(productId, StockOperation.CHECK, 0);
    }

    /** Converts the product UUID and submits INCREASE with the supplied quantity. */
    public void increaseStock(String productId, int quantity) {
        submit(productId, StockOperation.INCREASE, quantity);
    }

    /** Converts the product UUID and submits DECREASE with the supplied quantity. */
    public void decreaseStock(String productId, int quantity) {
        submit(productId, StockOperation.DECREASE, quantity);
    }

    private void submit(String productId, StockOperation operation, int quantity) {
        UUID id;
        try {
            String value = productId == null ? "" : productId.strip();
            id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("UUID must use the standard format");
            }
        } catch (IllegalArgumentException exception) {
            outputBoundary.present(new StockResponse(false, null, null, -1,
                "Invalid product UUID. Use the standard UUID format."));
            return;
        }
        StockResponse response = requestHandler.handle(new StockRequest(id, operation, quantity));
        outputBoundary.present(response);
    }

    /** Waits for commands until EXIT or end of input, leaving the input stream open. */
    public void run() {
        while (true) {
            output.println("1. CHECK stock\n2. INCREASE stock\n3. DECREASE stock\n4. EXIT");
            String command = readLine("Choose an operation");
            if (command == null || command.equals("4")) {
                output.println("Goodbye.");
                return;
            }
            switch (command) {
                case "1" -> readOperation(StockOperation.CHECK);
                case "2" -> readOperation(StockOperation.INCREASE);
                case "3" -> readOperation(StockOperation.DECREASE);
                default -> output.println("FAILURE - Invalid menu choice. Enter 1, 2, 3, or 4.");
            }
        }
    }

    private void readOperation(StockOperation operation) {
        String productId = readLine("Enter the product UUID");
        if (productId == null) {
            return;
        }
        if (operation == StockOperation.CHECK) {
            checkStock(productId);
            return;
        }
        String value = readLine("Enter the adjustment quantity");
        if (value == null) {
            return;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            output.println("FAILURE - Invalid quantity format. Enter a whole number within the integer range.");
            return;
        }
        switch (operation) {
            case INCREASE -> increaseStock(productId, quantity);
            case DECREASE -> decreaseStock(productId, quantity);
            case CHECK -> throw new IllegalStateException("CHECK has already been handled");
        }
    }

    private String readLine(String prompt) {
        output.println(prompt);
        output.flush();
        return input.hasNextLine() ? input.nextLine().strip() : null;
    }

}
