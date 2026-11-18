package gui.boutique;

import controller.BoutiqueOrderController;
import controller.BoutiqueOrderOutputBoundary;
import datatransferobjects.ProcessBoutiqueOrderResponse;
import entities.Order;
import entities.OrderItem;
import entities.OrderStatus;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.Executor;
import javafx.application.Platform;

/** JavaFX adapter for the existing U4 application boundary. */
public final class BoutiqueOrdersFXController implements BoutiqueOrderOutputBoundary {
    @FXML private TextField orderIdField;
    @FXML private TextField employeeIdField;
    @FXML private Button lookupButton;
    @FXML private ComboBox<OrderStatus> targetStatus;
    @FXML private TextField pickupPin;
    @FXML private TextArea cancellationReason;
    @FXML private Button processButton;
    @FXML private Label statusMessage;
    @FXML private Label orderDetails;
    @FXML private VBox orderPanel;

    private final Function<UUID, Order> orderLookup;
    private BoutiqueOrderController controller;
    private Order currentOrder;
    private boolean busy;
    private final Executor worker;
    private Function<String, UUID> orderReferenceLookup = value -> UUID.fromString(value);
    private Function<String, UUID> employeeReferenceLookup = value -> UUID.fromString(value);
    private Function<UUID, String> employeeNameLookup = id -> null;
    private Function<UUID, String> employeeCodeLookup = id -> id.toString();

    public void setReferenceLookup(Function<String, UUID> orderLookup,
            Function<String, UUID> employeeLookup, Function<UUID, String> employeeName,
            Function<UUID, String> employeeCode) {
        this.orderReferenceLookup = java.util.Objects.requireNonNull(orderLookup);
        this.employeeReferenceLookup = java.util.Objects.requireNonNull(employeeLookup);
        this.employeeNameLookup = java.util.Objects.requireNonNull(employeeName);
        this.employeeCodeLookup = java.util.Objects.requireNonNull(employeeCode);
    }


    public BoutiqueOrdersFXController(Function<UUID, Order> orderLookup) {
        this(orderLookup, java.util.concurrent.ForkJoinPool.commonPool());
    }

    public BoutiqueOrdersFXController(Function<UUID, Order> orderLookup, Executor worker) {
        this.orderLookup = java.util.Objects.requireNonNull(orderLookup);
        this.worker = java.util.Objects.requireNonNull(worker);
    }

    @FXML private void initialize() {
        targetStatus.getItems().setAll(OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION,
            OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED);
        targetStatus.valueProperty().addListener((obs, oldValue, newValue) -> updateFields());
        orderPanel.setVisible(false);
        orderPanel.setManaged(false);
        updateFields();
    }

    public void setController(BoutiqueOrderController controller) {
        this.controller = java.util.Objects.requireNonNull(controller);
        updateFields();
    }

    @FXML private void lookupOrder() {
        if (busy) return;
        UUID id;
        try { id = orderReferenceLookup.apply(orderIdField.getText().trim()); }
        catch (IllegalArgumentException exception) { id = null; }
        if (id == null) { showMessage("Error: Order not found. Enter an order number or UUID.", false); return; }
        final UUID resolvedOrderId = id;
        busy = true;
        updateFields();
        runBackground(() -> {
            Order found = orderLookup.apply(resolvedOrderId);
            Platform.runLater(() -> {
                currentOrder = found;
                renderOrder();
                showMessage(found == null ? "Order not found." : "Order loaded.", found != null);
            });
        }, "Error: Unable to retrieve this order.");
    }

    @FXML private void processOrder() {
        if (busy || controller == null || currentOrder == null || targetStatus.getValue() == null) return;
        UUID employeeId;
        try { employeeId = employeeReferenceLookup.apply(employeeIdField.getText().trim()); }
        catch (IllegalArgumentException exception) { employeeId = null; }
        if (employeeId == null) { showMessage("Error: Unknown employee code or UUID.", false); return; }
        final UUID resolvedEmployeeId = employeeId;
        OrderStatus status = targetStatus.getValue();
        String pin = pickupPin.getText();
        String reason = cancellationReason.getText();
        if (status == OrderStatus.READY_FOR_PICKUP && pin.isBlank()) {
            showMessage("Error: Enter a pickup PIN.", false); return;
        }
        if (status == OrderStatus.CANCELLED && reason.isBlank()) {
            showMessage("Error: Enter a cancellation reason.", false); return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
            "Apply " + status + " to order " + currentOrder.getOrderNumber() + "?", ButtonType.OK, ButtonType.CANCEL);
        confirmation.setHeaderText("Confirm order update");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        busy = true;
        updateFields();
        UUID orderId = currentOrder.getOrderId();
        runBackground(() -> controller.processOrder(orderId, resolvedEmployeeId, status, pin, reason),
            "Error: Unexpected problem processing the order.");
    }

    @Override public void presentOrderProcessing(ProcessBoutiqueOrderResponse response) {
        if (Platform.isFxApplicationThread()) {
            busy = true;
            updateFields();
            runBackground(() -> presentOrderProcessing(response), "Error: Unable to refresh order details.");
            return;
        }
        Order refreshed = orderLookup.apply(response.getOrderId());
        Platform.runLater(() -> {
            currentOrder = refreshed;
            if (response.isSuccess()) {
                showMessage("Success: " + response.getMessage(), true);
            } else {
                showMessage("Order update rejected: " + response.getMessage(), false);
            }
            renderOrder();
        });
    }

    /** Keeps database work off JavaFX and releases the duplicate-submission gate on every outcome. */
    private void runBackground(Runnable action, String failure) {
        try {
            worker.execute(() -> {
                try { action.run(); }
                catch (RuntimeException exception) {
                    Platform.runLater(() -> {
                        currentOrder = null;
                        renderOrder();
                        showMessage(failure, false);
                    });
                } finally {
                    Platform.runLater(() -> { busy = false; updateFields(); });
                }
            });
        } catch (RuntimeException exception) {
            busy = false;
            updateFields();
            showMessage(failure, false);
        }
    }

    private void renderOrder() {
        boolean found = currentOrder != null;
        orderPanel.setManaged(found);
        orderPanel.setVisible(found);
        if (!found) { orderDetails.setText(""); updateFields(); return; }
        StringBuilder details = new StringBuilder()
            .append("Order number: ").append(currentOrder.getOrderNumber())
            .append("\nOrder ID: ").append(currentOrder.getOrderId())
            .append("\nCustomer ID: ").append(currentOrder.getCustomerId())
            .append("\nStatus: ").append(currentOrder.getStatus())
            .append("\nFulfillment: ").append(currentOrder.getFulfillmentType())
            .append("\nSubtotal: ").append(currentOrder.getSubtotal())
            .append("\nDiscount: ").append(currentOrder.getDiscount())
            .append("\nTotal: ").append(currentOrder.getTotal())
            .append("\nProducts:");
        for (OrderItem item : currentOrder.getItems()) {
            details.append("\n  ").append(item.getProductId()).append(" × ")
                .append(item.getQuantity()).append(" — ").append(item.getLineTotal());
        }
        orderDetails.setText(details.toString());
        updateFields();
    }

    private void updateFields() {
        if (targetStatus == null) return;
        boolean hasOrder = currentOrder != null;
        OrderStatus status = targetStatus.getValue();
        pickupPin.setDisable(status != OrderStatus.READY_FOR_PICKUP);
        cancellationReason.setDisable(status != OrderStatus.CANCELLED);
        processButton.setDisable(busy || !hasOrder || controller == null || status == null);
        lookupButton.setDisable(busy);
    }

    private void showMessage(String message, boolean success) {
        statusMessage.setText(message);
        statusMessage.getStyleClass().removeAll("message-error", "message-success");
        statusMessage.getStyleClass().add(success ? "message-success" : "message-error");
    }
}
