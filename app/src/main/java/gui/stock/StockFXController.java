package gui.stock;

import controller.StockOutputBoundary;
import datatransferobjects.StockOperation;
import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import entities.ProductCategory;
import inventory.InventoryRow;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import presenter.StockPresenter;

/** FX view adapter for the injected inventory query and existing U1 input/output boundaries. */
public final class StockFXController {
    private static final Logger LOG = Logger.getLogger(StockFXController.class.getName());
    private final StockDashboardService service;
    private final StockDashboardModel model;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final BooleanProperty loaded = new SimpleBooleanProperty();
    private final BooleanProperty validQuantity = new SimpleBooleanProperty(true);
    private final ObservableList<InventoryRow> visibleRows = FXCollections.observableArrayList();
    private List<InventoryRow> allRows = List.of();
    private StockOutputBoundary output;

    @FXML private ComboBox<ProductCategory> categoryFilter;
    @FXML private ComboBox<StockOperation> adjustmentOperation;
    @FXML private Spinner<Integer> adjustmentQuantity;
    @FXML private TableView<InventoryRow> inventoryTable;
    @FXML private TableColumn<InventoryRow, String> productColumn;
    @FXML private TableColumn<InventoryRow, String> categoryColumn;
    @FXML private TableColumn<InventoryRow, Number> quantityColumn;
    @FXML private TableColumn<InventoryRow, String> statusColumn;
    @FXML private TableColumn<InventoryRow, Void> actionsColumn;
    @FXML private TextField productSearch;
    @FXML private TextField selectedProduct;
    @FXML private Button submitAdjustment;
    @FXML private Button refreshButton;
    @FXML private Button clearFiltersButton;
    @FXML private Label totalProductsValue;
    @FXML private Label availableUnitsValue;
    @FXML private Label lowStockProductsValue;
    @FXML private Label totalProductsHint;
    @FXML private Label availableUnitsHint;
    @FXML private Label lowStockHint;
    @FXML private Label inventoryStatus;
    @FXML private Label feedback;
    @FXML private Label quantityError;
    @FXML private Label emptyTitle;
    @FXML private Label emptyDescription;
    @FXML private ScrollPane stockPage;

    /** Receives session dependencies; the controller never constructs repositories. */
    public StockFXController(StockDashboardService service, StockDashboardModel model) {
        this.service = java.util.Objects.requireNonNull(service);
        this.model = java.util.Objects.requireNonNull(model);
    }

    @FXML
    private void initialize() {
        output = new StockPresenter(message -> feedback.setText(message));
        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(ProductCategory.values());
        categoryFilter.setConverter(enumLabels(ProductCategory.class, "All categories"));
        adjustmentOperation.getItems().setAll(StockOperation.INCREASE, StockOperation.DECREASE);
        adjustmentOperation.setConverter(enumLabels(StockOperation.class, "Choose an operation"));
        adjustmentQuantity.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
            Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
        adjustmentQuantity.setEditable(true);
        adjustmentQuantity.getEditor().setTextFormatter(new TextFormatter<String>(change -> {
            String text = change.getControlNewText();
            if (text.isEmpty() || text.equals("-")) return change;
            try {
                Integer.parseInt(text);
                return change;
            } catch (NumberFormatException invalidFormat) {
                return null;
            }
        }));
        adjustmentQuantity.getEditor().textProperty().addListener((observable, oldText, newText) -> validateQuantity());
        quantityError.visibleProperty().bind(validQuantity.not());
        quantityError.managedProperty().bind(quantityError.visibleProperty());
        selectedProduct.setEditable(false);
        initializeTable();
        BooleanBinding unavailable = busy.or(loaded.not());
        BooleanBinding noSelection = Bindings.isNull(inventoryTable.getSelectionModel().selectedItemProperty());
        productSearch.disableProperty().bind(unavailable);
        categoryFilter.disableProperty().bind(unavailable);
        clearFiltersButton.disableProperty().bind(unavailable);
        inventoryTable.disableProperty().bind(unavailable);
        adjustmentOperation.disableProperty().bind(unavailable.or(noSelection));
        adjustmentQuantity.disableProperty().bind(unavailable.or(noSelection));
        submitAdjustment.disableProperty().bind(unavailable.or(noSelection).or(validQuantity.not())
            .or(Bindings.isNull(adjustmentOperation.valueProperty())));
        refreshButton.disableProperty().bind(busy);
        productSearch.textProperty().addListener((observable, oldText, newText) -> applyFilters());
        categoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        inventoryTable.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, row) -> {
            selectedProduct.setText(row == null ? "" : row.name());
            selectedProduct.setAccessibleHelp(row == null ? "Select a product" : row.productId().toString());
        });
        refreshInventory();
    }

    private void initializeTable() {
        inventoryTable.setItems(visibleRows);
        inventoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        productColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().name()));
        categoryColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(label(value.getValue().category())));
        quantityColumn.setCellValueFactory(value -> new ReadOnlyIntegerWrapper(value.getValue().quantity()));
        quantityColumn.setComparator(java.util.Comparator.comparingInt(Number::intValue));
        quantityColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number quantity, boolean empty) {
                super.updateItem(quantity, empty);
                setText(empty || quantity == null ? null : quantity.intValue() < 0 ? "Unavailable" : quantity.toString());
            }
        });
        statusColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(model.status(value.getValue())));
        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button check = new Button("Check");
            {
                check.getStyleClass().addAll("button-secondary", "stock-row-action");
                check.disableProperty().bind(busy);
                check.setOnAction(event -> runOperation(getTableRow().getItem(), StockOperation.CHECK, 0));
            }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : check);
            }
        });
    }

    @FXML
    private void refreshInventory() {
        if (busy.get()) return;
        UUID selectedId = selectedId();
        busy.set(true);
        inventoryStatus.setText("Loading demonstration inventory...");
        service.refresh().whenComplete((rows, failure) -> Platform.runLater(() -> {
            busy.set(false);
            if (failure == null) {
                applySnapshot(rows, selectedId);
            } else {
                invalidateInventory();
                technicalFailure("Unable to load inventory. Use Reload inventory to try again.", failure);
            }
        }));
    }

    @FXML
    private void clearFilters() {
        productSearch.clear();
        categoryFilter.getSelectionModel().clearSelection();
    }

    @FXML
    private void submitAdjustment() {
        if (busy.get()) return;
        InventoryRow row = inventoryTable.getSelectionModel().getSelectedItem();
        if (row == null || adjustmentOperation.getValue() == null) {
            showMessage(false, "Error - select a product and an adjustment operation");
            return;
        }
        try {
            int quantity = StockDashboardModel.parseQuantity(adjustmentQuantity.getEditor().getText());
            runOperation(row, adjustmentOperation.getValue(), quantity);
        } catch (IllegalArgumentException invalidInput) {
            showMessage(false, "Error - " + invalidInput.getMessage());
        }
    }

    private void runOperation(InventoryRow row, StockOperation operation, int quantity) {
        if (busy.get() || row == null) return;
        busy.set(true);
        inventoryStatus.setText("Processing stock operation...");
        service.execute(new StockRequest(row.productId(), operation, quantity))
            .whenComplete((result, failure) -> Platform.runLater(() -> {
                busy.set(false);
                if (failure != null) {
                    invalidateInventory();
                    technicalFailure("Unable to confirm the stock operation. Reload inventory before another adjustment.", failure);
                    return;
                }
                StockResponse response = result.response();
                if (result.refreshFailure() == null) {
                    applySnapshot(result.rows(), row.productId());
                } else {
                    invalidateInventory();
                    LOG.log(Level.WARNING, "Inventory refresh after U1 failed", result.refreshFailure());
                    response = new StockResponse(response.isSuccess(), response.getProductId(),
                        response.getProductName(), -1, response.getMessage());
                }
                styleFeedback(response.isSuccess());
                output.present(response);
                if (result.refreshFailure() != null) {
                    feedback.setText(feedback.getText() + "\nInventory refresh failed. Reload inventory before another adjustment.");
                }
            }));
    }

    private void applySnapshot(List<InventoryRow> rows, UUID selectedId) {
        allRows = List.copyOf(rows);
        loaded.set(true);
        StockDashboardModel.Summary summary = model.summarize(allRows);
        totalProductsValue.setText(Integer.toString(summary.products()));
        availableUnitsValue.setText(Long.toString(summary.units()));
        lowStockProductsValue.setText(Long.toString(summary.lowStockProducts()));
        totalProductsHint.setText("All loaded products");
        availableUnitsHint.setText(summary.unavailableProducts() == 0 ? "Across the entire inventory" : "Known quantities only");
        lowStockHint.setText(model.lowStockThreshold() == 0 ? "Low-stock band disabled" : "1-" + model.lowStockThreshold() + " available units");
        inventoryStatus.setText(summary.unavailableProducts() == 0 ? "Demonstration inventory - session only"
            : "Demonstration inventory - some stock records are unavailable");
        applyFilters();
        selectProduct(selectedId);
    }

    private void applyFilters() {
        UUID selectedId = selectedId();
        visibleRows.setAll(model.filter(allRows, productSearch.getText(), categoryFilter.getValue()));
        inventoryTable.sort();
        selectProduct(selectedId);
        emptyTitle.setText(loaded.get() ? "No matching products" : "Inventory unavailable");
        emptyDescription.setText(loaded.get() ? "Try another search or category, or clear the filters."
            : "Reload inventory to retrieve current quantities.");
    }

    private void selectProduct(UUID productId) {
        inventoryTable.getSelectionModel().clearSelection();
        if (productId != null) {
            visibleRows.stream().filter(row -> row.productId().equals(productId)).findFirst()
                .ifPresent(row -> inventoryTable.getSelectionModel().select(row));
        }
    }

    private UUID selectedId() {
        InventoryRow row = inventoryTable.getSelectionModel().getSelectedItem();
        return row == null ? null : row.productId();
    }

    private void invalidateInventory() {
        allRows = List.of();
        loaded.set(false);
        visibleRows.clear();
        totalProductsValue.setText("—");
        availableUnitsValue.setText("—");
        lowStockProductsValue.setText("—");
        totalProductsHint.setText("Not confirmed");
        availableUnitsHint.setText("Not confirmed");
        lowStockHint.setText("Not confirmed");
        inventoryStatus.setText("Inventory unavailable - reload required");
        applyFilters();
    }

    private void validateQuantity() {
        try {
            StockDashboardModel.parseQuantity(adjustmentQuantity.getEditor().getText());
            validQuantity.set(true);
            adjustmentQuantity.getStyleClass().remove("validation-error");
        } catch (IllegalArgumentException invalidInput) {
            validQuantity.set(false);
            if (!adjustmentQuantity.getStyleClass().contains("validation-error")) {
                adjustmentQuantity.getStyleClass().add("validation-error");
            }
        }
    }

    private void technicalFailure(String message, Throwable failure) {
        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
        LOG.log(Level.WARNING, message, cause);
        showMessage(false, "Error - " + message);
    }

    private void showMessage(boolean success, String text) {
        styleFeedback(success);
        feedback.setText(text);
    }

    private void styleFeedback(boolean success) {
        feedback.getStyleClass().removeAll("message-success", "message-error");
        feedback.getStyleClass().add(success ? "message-success" : "message-error");
        feedback.setVisible(true);
        feedback.setManaged(true);
        Platform.runLater(() -> {
            stockPage.applyCss();
            stockPage.layout();
            stockPage.setVvalue(1);
            feedback.setFocusTraversable(true);
            feedback.requestFocus();
        });
    }

    private static String label(Enum<?> value) {
        String text = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static <E extends Enum<E>> StringConverter<E> enumLabels(Class<E> type, String empty) {
        return new StringConverter<>() {
            @Override public String toString(E value) { return value == null ? empty : label(value); }
            @Override public E fromString(String text) {
                return text == null || text.isBlank() ? null : Enum.valueOf(type, text.toUpperCase(Locale.ROOT).replace(' ', '_'));
            }
        };
    }
}
