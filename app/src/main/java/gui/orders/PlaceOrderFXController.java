package gui.orders;

import controller.OrderOutputBoundary;
import datatransferobjects.PlaceOrderResponse;
import entities.FulfillmentType;
import gui.orders.OrderDataQuery.Customer;
import gui.orders.OrderDataQuery.Product;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import presenter.PlaceOrderPresenter;

/** JavaFX view of the session workspace. All persistence is delegated to U2 through the workspace. */
public final class PlaceOrderFXController {
    private final OrderWorkspace workspace;
    private final ObservableList<Product> catalog = FXCollections.observableArrayList();
    private final ObservableList<ShoppingCart.Line> cart = FXCollections.observableArrayList();
    private final Map<UUID, String> quantityDrafts = new HashMap<>();
    private boolean rendering;
    private Runnable unsubscribe;
    private OrderOutputBoundary presenter;
    private PlaceOrderResponse shownOutcome;

    @FXML private ScrollPane ordersPage;
    @FXML private TableView<Product> catalogTable;
    @FXML private TableColumn<Product, String> catalogName;
    @FXML private TableColumn<Product, String> catalogCategory;
    @FXML private TableColumn<Product, BigDecimal> catalogPrice;
    @FXML private TableColumn<Product, Number> catalogStock;
    @FXML private TextField addQuantity;
    @FXML private Button addButton;
    @FXML private Label selectedCatalogProduct;
    @FXML private Label cartCount;
    @FXML private TableView<ShoppingCart.Line> cartTable;
    @FXML private TableColumn<ShoppingCart.Line, String> cartName;
    @FXML private TableColumn<ShoppingCart.Line, Integer> cartQuantity;
    @FXML private TableColumn<ShoppingCart.Line, BigDecimal> cartPrice;
    @FXML private TableColumn<ShoppingCart.Line, BigDecimal> cartTotal;
    @FXML private TableColumn<ShoppingCart.Line, Void> cartRemove;
    @FXML private Label cartSubtotal;
    @FXML private ComboBox<Customer> customerChoice;
    @FXML private Label customerDetails;
    @FXML private Label availablePoints;
    @FXML private TextField pointsToRedeem;
    @FXML private ComboBox<FulfillmentType> fulfillmentChoice;
    @FXML private Label pointsValidation;
    @FXML private Label quoteSubtotal;
    @FXML private Label quoteDiscount;
    @FXML private Label quoteTotal;
    @FXML private Label quotePoints;
    @FXML private Label quoteFulfillment;
    @FXML private Label previewStatus;
    @FXML private Label confirmationLabel;
    @FXML private Label sessionStatus;
    @FXML private Label feedback;
    @FXML private Button reloadButton;
    @FXML private Button previewButton;
    @FXML private Button confirmButton;
    @FXML private ProgressIndicator processing;

    public PlaceOrderFXController(OrderWorkspace workspace) {
        this.workspace = Objects.requireNonNull(workspace);
    }

    @FXML
    private void initialize() {
        presenter = new PlaceOrderPresenter(text -> feedback.setText(text));
        initializeCatalog();
        initializeCart();
        fulfillmentChoice.getItems().setAll(FulfillmentType.values());
        fulfillmentChoice.setConverter(new StringConverter<>() {
            @Override public String toString(FulfillmentType type) { return fulfillmentLabel(type); }
            @Override public FulfillmentType fromString(String value) {
                return fulfillmentChoice.getItems().stream().filter(type -> fulfillmentLabel(type).equals(value)).findFirst().orElse(null);
            }
        });
        customerChoice.setConverter(new StringConverter<>() {
            @Override public String toString(Customer customer) {
                return customer == null ? "Choose an existing customer" : customer.name() + " - " + customer.email();
            }
            @Override public Customer fromString(String value) {
                return customerChoice.getItems().stream().filter(customer -> toString(customer).equals(value)).findFirst().orElse(null);
            }
        });
        customerChoice.valueProperty().addListener((observable, previous, customer) -> {
            if (!rendering) invoke(() -> workspace.selectCustomer(customer == null ? null : customer.id()));
        });
        pointsToRedeem.textProperty().addListener((observable, previous, text) -> {
            if (!rendering) invoke(() -> workspace.setPointsText(text));
        });
        fulfillmentChoice.valueProperty().addListener((observable, previous, choice) -> {
            if (!rendering) invoke(() -> workspace.selectFulfillment(choice));
        });
        addQuantity.textProperty().addListener((observable, previous, text) -> updateAddButton(workspace.snapshot()));
        ordersPage.sceneProperty().addListener((observable, previous, scene) -> {
            if (unsubscribe != null) { unsubscribe.run(); unsubscribe = null; }
            if (scene != null) {
                unsubscribe = workspace.listen(() -> Platform.runLater(() -> {
                    if (ordersPage.getScene() != null) render();
                }));
                render();
                if (!workspace.snapshot().busy()) observe(workspace.refresh());
            }
        });
        render();
    }

    private void initializeCatalog() {
        catalogTable.setItems(catalog);
        catalogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        catalogName.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().name()));
        catalogCategory.setCellValueFactory(value -> new ReadOnlyStringWrapper(
            value.getValue().category().name().replace('_', ' ').toLowerCase(Locale.ROOT)));
        catalogPrice.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().unitPrice()));
        catalogPrice.setCellFactory(column -> moneyCell());
        catalogStock.setCellValueFactory(value -> new ReadOnlyIntegerWrapper(value.getValue().available()));
        catalogStock.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : value.intValue() < 0 ? "Unavailable" : value.toString());
            }
        });
        catalogTable.getSelectionModel().selectedItemProperty().addListener((observable, previous, product) -> {
            selectedCatalogProduct.setText(product == null ? "Select a product" : product.name());
            updateAddButton(workspace.snapshot());
        });
    }

    private void initializeCart() {
        cartTable.setItems(cart);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cartName.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().product().name()));
        cartPrice.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().product().unitPrice()));
        cartTotal.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().total()));
        cartPrice.setCellFactory(column -> moneyCell());
        cartTotal.setCellFactory(column -> moneyCell());
        cartQuantity.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().quantity()));
        cartQuantity.setCellFactory(column -> quantityCell());
        cartRemove.setCellFactory(column -> new TableCell<>() {
            private final Button remove = new Button("Remove");
            {
                remove.getStyleClass().addAll("button-secondary", "orders-row-action");
                remove.setOnAction(event -> {
                    ShoppingCart.Line line = getTableRow().getItem();
                    if (line != null) invoke(() -> { quantityDrafts.remove(line.product().id()); workspace.remove(line.product().id()); });
                });
            }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : remove);
            }
        });
    }

    private TableCell<ShoppingCart.Line, Integer> quantityCell() {
        return new TableCell<>() {
            private final TextField field = new TextField();
            private final Button update = new Button("Update");
            private final HBox editor = new HBox(6, field, update);
            private boolean updating;
            {
                field.setPrefWidth(58);
                field.setMinWidth(40);
                field.getStyleClass().add("form-field");
                field.setAccessibleText("Cart quantity");
                update.getStyleClass().addAll("button-secondary", "orders-row-action");
                editor.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                field.textProperty().addListener((observable, previous, text) -> {
                    ShoppingCart.Line line = getTableRow() == null ? null : getTableRow().getItem();
                    if (!updating && !rendering && line != null) {
                        quantityDrafts.put(line.product().id(), text);
                        invoke(workspace::invalidatePreview);
                    }
                });
                update.setOnAction(event -> applyQuantity());
                field.setOnAction(event -> applyQuantity());
            }
            private void applyQuantity() {
                ShoppingCart.Line line = getTableRow().getItem();
                if (line != null) invoke(() -> {
                    int quantity = positiveQuantity(field.getText());
                    quantityDrafts.remove(line.product().id());
                    workspace.changeQuantity(line.product().id(), quantity);
                });
            }
            @Override protected void updateItem(Integer quantity, boolean empty) {
                super.updateItem(quantity, empty);
                updating = true;
                try {
                    ShoppingCart.Line line = getIndex() >= 0 && getIndex() < cart.size() ? cart.get(getIndex()) : null;
                    String savedDraft = line == null ? null : quantityDrafts.get(line.product().id());
                    field.setText(savedDraft != null ? savedDraft : quantity == null ? "" : quantity.toString());
                }
                finally { updating = false; }
                setGraphic(empty ? null : editor);
            }
        };
    }

    @FXML private void addToCart() {
        Product product = catalogTable.getSelectionModel().getSelectedItem();
        if (product != null) invoke(() -> workspace.add(product.id(), positiveQuantity(addQuantity.getText())));
    }
    @FXML private void reload() { observe(workspace.refresh()); }
    @FXML private void preview() { if (quantityDrafts.isEmpty()) observe(workspace.preview()); }
    @FXML private void confirm() { if (quantityDrafts.isEmpty()) observe(workspace.confirm()); }

    private void render() {
        OrderWorkspace.State state = workspace.snapshot();
        rendering = true;
        try {
            Product selected = catalogTable.getSelectionModel().getSelectedItem();
            var currentProducts = state.loaded() ? state.data().products() : java.util.List.<Product>of();
            if (!catalog.equals(currentProducts)) {
                catalog.setAll(currentProducts);
                if (selected != null) catalog.stream().filter(product -> product.id().equals(selected.id())).findFirst()
                    .ifPresent(product -> catalogTable.getSelectionModel().select(product));
                if (catalogTable.getSelectionModel().isEmpty() && !catalog.isEmpty()) catalogTable.getSelectionModel().selectFirst();
            }
            if (!cart.equals(state.cart())) {
                cart.setAll(state.cart());
                quantityDrafts.keySet().retainAll(state.cart().stream().map(line -> line.product().id()).toList());
            }
            if (!customerChoice.getItems().equals(state.data().customers())) customerChoice.getItems().setAll(state.data().customers());
            Customer customer = state.data().customers().stream().filter(value -> value.id().equals(state.customerId())).findFirst().orElse(null);
            customerChoice.setValue(customer);
            pointsToRedeem.setText(state.pointsText());
            fulfillmentChoice.setValue(state.fulfillment());
            customerDetails.setText(customer == null ? "Select an existing customer." : customer.email());
            availablePoints.setText(!state.loaded() || customer == null || customer.points() == null
                ? "Available loyalty points: Unavailable" : "Available loyalty points: " + customer.points());
            boolean pointsInvalid;
            try { pointsInvalid = Integer.parseInt(state.pointsText().strip()) < 0; }
            catch (NumberFormatException invalid) { pointsInvalid = true; }
            pointsValidation.setVisible(pointsInvalid);
            pointsValidation.setManaged(pointsInvalid);
            cartCount.setText(state.cart().size() + " product(s) in cart");
            cartSubtotal.setText(money(state.estimatedSubtotal()));
            boolean busy = state.busy();
            catalogTable.setDisable(busy || !state.loaded());
            cartTable.setDisable(busy);
            customerChoice.setDisable(busy || !state.loaded());
            pointsToRedeem.setDisable(busy);
            fulfillmentChoice.setDisable(busy);
            addQuantity.setDisable(busy || !state.loaded());
            reloadButton.setDisable(busy);
            previewButton.setDisable(!state.canPreview() || !quantityDrafts.isEmpty());
            confirmButton.setDisable(!state.canConfirm() || !quantityDrafts.isEmpty());
            processing.setVisible(busy);
            processing.setManaged(busy);
            sessionStatus.setText(state.message());
            updateAddButton(state);
            renderQuote(state);
            confirmationLabel.setText(state.receipt() == null ? "" : "Last saved order: " + state.receipt().response().getOrderId());
            confirmationLabel.setVisible(state.receipt() != null);
            confirmationLabel.setManaged(state.receipt() != null);
            renderFeedback(state);
        } finally { rendering = false; }
    }

    private void renderQuote(OrderWorkspace.State state) {
        PlaceOrderResponse response = state.preview() == null ? null : state.preview().response();
        FulfillmentType type = state.preview() == null ? null : state.preview().fulfillment();
        if (response == null && state.cart().isEmpty() && state.receipt() != null) {
            response = state.receipt().response();
            type = state.receipt().fulfillment();
        }
        quoteSubtotal.setText(response == null ? "—" : money(response.getSubtotal()));
        quoteDiscount.setText(response == null ? "—" : money(response.getDiscount()));
        quoteTotal.setText(response == null ? "—" : money(response.getTotal()));
        quotePoints.setText(response == null ? "—" : Integer.toString(response.getRedeemedPoints()));
        quoteFulfillment.setText(type == null ? "—" : fulfillmentLabel(type));
        previewStatus.setText(state.preview() != null ? "Current preview ready" : state.cart().isEmpty() && state.receipt() != null
            ? "Order saved" : "Preview required for the current selections");
    }

    private void renderFeedback(OrderWorkspace.State state) {
        feedback.getStyleClass().removeAll("message-error", "message-success");
        boolean show = !state.busy() && (state.outcome() != null || state.error());
        feedback.setVisible(show);
        feedback.setManaged(show);
        if (!show) return;
        feedback.getStyleClass().add(state.error() ? "message-error" : "message-success");
        if (state.outcome() != null) {
            presenter.present(state.outcome());
            if (!state.loaded()) feedback.setText(feedback.getText() + "\n" + state.message());
        } else feedback.setText("Error - " + state.message());
        if (state.outcome() != shownOutcome) {
            shownOutcome = state.outcome();
            Platform.runLater(() -> {
                if (ordersPage.getScene() != null) {
                    ordersPage.applyCss();
                    ordersPage.layout();
                    ordersPage.setVvalue(1);
                }
            });
        }
    }

    private void updateAddButton(OrderWorkspace.State state) {
        Product selected = catalogTable.getSelectionModel().getSelectedItem();
        boolean valid;
        try { positiveQuantity(addQuantity.getText()); valid = true; }
        catch (IllegalArgumentException invalid) { valid = false; }
        addButton.setDisable(state.busy() || !state.loaded() || selected == null || selected.available() <= 0 || !valid);
    }

    private void invoke(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException invalid) { workspace.reportInputError(invalid.getMessage()); }
    }

    private static void observe(CompletableFuture<?> operation) {
        operation.exceptionally(failure -> null); // Workspace logs technical errors and publishes their visible outcome.
    }

    private static int positiveQuantity(String value) {
        try {
            int quantity = Integer.parseInt(value.strip());
            if (quantity > 0) return quantity;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Enter a positive whole quantity", invalid);
        }
        throw new IllegalArgumentException("Enter a positive whole quantity");
    }

    private static String fulfillmentLabel(FulfillmentType type) {
        if (type == null) return "Choose fulfillment";
        return type == FulfillmentType.DELIVERY ? "Delivery" : "In-store pickup";
    }

    private static String money(BigDecimal amount) {
        if (amount == null) return "—";
        return amount.scale() < 2 ? amount.setScale(2).toPlainString() : amount.toPlainString();
    }

    private static <T> TableCell<T, BigDecimal> moneyCell() {
        return new TableCell<>() {
            @Override protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : money(value));
            }
        };
    }
}
