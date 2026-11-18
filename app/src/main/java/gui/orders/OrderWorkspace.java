package gui.orders;

import datatransferobjects.OrderItemRequest;
import datatransferobjects.PlaceOrderRequest;
import datatransferobjects.PlaceOrderResponse;
import entities.FulfillmentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Session-scoped cart and order workflow, independent of JavaFX and repository implementations. */
public final class OrderWorkspace {
    private static final Logger LOG = Logger.getLogger(OrderWorkspace.class.getName());
    private final ShoppingCart cart = new ShoppingCart();
    private final OrderDataQuery query;
    private final OrderRequestAdapter handler;
    private final Executor worker;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private OrderDataQuery.Data data = new OrderDataQuery.Data(List.of(), List.of());
    private UUID customerId;
    private String pointsText = "0";
    private FulfillmentType fulfillment;
    private long revision;
    private Quote preview;
    private Receipt receipt;
    private PlaceOrderResponse outcome;
    private boolean outcomeConfirmation;
    private String message = "Load the catalog to begin.";
    private boolean error;
    private boolean busy;
    private boolean confirming;
    private boolean loaded;

    public OrderWorkspace(OrderDataQuery query, OrderRequestAdapter handler, Executor worker) {
        this.query = Objects.requireNonNull(query);
        this.handler = Objects.requireNonNull(handler);
        this.worker = Objects.requireNonNull(worker);
    }

    /** Returns an unsubscribe action so detached screens release their observers. */
    public Runnable listen(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public synchronized void add(UUID productId, int quantity) {
        ensureEditable();
        OrderDataQuery.Product product = data.products().stream().filter(item -> item.id().equals(productId))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Product is unavailable"));
        cart.add(product, quantity);
        changed();
    }

    public synchronized void changeQuantity(UUID productId, int quantity) {
        ensureEditable();
        cart.changeQuantity(productId, quantity);
        changed();
    }

    public synchronized void remove(UUID productId) {
        ensureEditable();
        cart.remove(productId);
        changed();
    }

    public synchronized void selectCustomer(UUID id) {
        ensureEditable();
        if (!Objects.equals(customerId, id)) { customerId = id; changed(); }
    }

    public synchronized void setPointsText(String text) {
        ensureEditable();
        String value = text == null ? "" : text;
        if (!pointsText.equals(value)) { pointsText = value; changed(); }
    }

    public synchronized void selectFulfillment(FulfillmentType type) {
        ensureEditable();
        if (fulfillment != type) { fulfillment = type; changed(); }
    }

    /** Quantity drafts invalidate any earlier monetary preview before being applied. */
    public synchronized void invalidatePreview() {
        ensureEditable();
        changed();
    }

    public synchronized void reportInputError(String text) {
        message = text;
        error = true;
        outcome = null;
        publish();
    }

    public CompletableFuture<State> refresh() {
        synchronized (this) {
            if (busy) return CompletableFuture.failedFuture(new IllegalStateException("An order operation is running"));
            busy = true;
            message = "Loading demonstration catalog and loyalty...";
            error = false;
            publish();
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    OrderDataQuery.Data fresh = query.load();
                    synchronized (this) {
                        applyData(fresh);
                        busy = false;
                        message = "Demonstration session - catalog and loyalty loaded.";
                        publish();
                        return snapshot();
                    }
                } catch (RuntimeException exception) {
                    failed("Unable to load catalog and loyalty. Reload before ordering.", exception);
                    throw exception;
                }
            }, worker);
        } catch (RuntimeException exception) {
            failed("Unable to schedule catalog loading.", exception);
            return CompletableFuture.failedFuture(exception);
        }
    }

    public CompletableFuture<State> preview() { return perform(false); }
    public CompletableFuture<State> confirm() { return perform(true); }

    private CompletableFuture<State> perform(boolean confirmation) {
        Captured captured;
        synchronized (this) {
            if (busy) return CompletableFuture.failedFuture(new IllegalStateException("An order operation is running"));
            try {
                PlaceOrderRequest request = request(confirmation);
                if (confirmation && (preview == null || preview.revision() != revision || !preview.response().isSuccess())) {
                    throw new IllegalArgumentException("Preview the current order before confirming");
                }
                captured = new Captured(request, fulfillment, revision);
                busy = true;
                confirming = confirmation;
                if (confirmation) preview = null;
                outcome = null;
                error = false;
                message = confirmation ? "Confirming order..." : "Calculating authoritative preview...";
                publish();
            } catch (RuntimeException invalidInput) {
                reportInputError(invalidInput.getMessage());
                return CompletableFuture.failedFuture(invalidInput);
            }
        }
        try {
            return CompletableFuture.supplyAsync(() -> execute(captured), worker);
        } catch (RuntimeException exception) {
            failed("Unable to schedule the order operation.", exception);
            return CompletableFuture.failedFuture(exception);
        }
    }

    private State execute(Captured captured) {
        try {
            PlaceOrderResponse response = Objects.requireNonNull(handler.handle(captured.request(), captured.fulfillment()));
            boolean confirmation = captured.request().isConfirmed();
            if (confirmation && response.isSuccess() && response.getOrderId() == null) {
                throw new IllegalStateException("Confirmation returned no persisted order identifier");
            }
            OrderDataQuery.Data fresh = null;
            RuntimeException refreshFailure = null;
            if (confirmation || !response.isSuccess()) {
                try { fresh = query.load(); }
                catch (RuntimeException exception) { refreshFailure = exception; }
            }
            synchronized (this) {
                boolean current = captured.revision() == revision;
                if (confirmation && response.isSuccess()) {
                    receipt = new Receipt(response, captured.fulfillment());
                    cart.clear();
                    pointsText = "0";
                    revision++;
                    preview = null;
                }
                if (fresh != null) applyData(fresh);
                if (refreshFailure != null) {
                    loaded = false;
                    LOG.log(Level.WARNING, "Order data refresh failed", refreshFailure);
                }
                if (confirmation || current) {
                    outcome = response;
                    outcomeConfirmation = confirmation;
                    error = !response.isSuccess() || refreshFailure != null;
                    if (!confirmation && response.isSuccess()) preview = new Quote(response, captured.fulfillment(), revision);
                    message = refreshFailure == null ? response.getMessage()
                        : "Outcome received. Catalog and loyalty refresh failed; reload before another order.";
                } else {
                    message = "Inputs changed. Preview the current order again.";
                }
                busy = false;
                confirming = false;
                publish();
                return snapshot();
            }
        } catch (RuntimeException exception) {
            failed(captured.request().isConfirmed()
                ? "Unable to confirm the outcome. Reload and preview again before another attempt."
                : "Unable to calculate the preview.", exception);
            throw exception;
        }
    }

    private synchronized void failed(String text, RuntimeException exception) {
        busy = false;
        confirming = false;
        preview = null;
        loaded = false;
        outcome = null;
        message = text;
        error = true;
        LOG.log(Level.WARNING, text, exception);
        publish();
    }

    private PlaceOrderRequest request(boolean confirmation) {
        if (!loaded) throw new IllegalArgumentException("Reload catalog and loyalty before ordering");
        if (customerId == null) throw new IllegalArgumentException("Choose a customer");
        if (fulfillment == null) throw new IllegalArgumentException("Choose delivery or in-store pickup");
        int points;
        try { points = Integer.parseInt(pointsText.strip()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Enter nonnegative whole loyalty points", exception); }
        List<OrderItemRequest> items = cart.lines().stream()
            .map(line -> new OrderItemRequest(line.product().id(), line.quantity())).toList();
        return new PlaceOrderRequest(customerId, items, points, confirmation);
    }

    private void applyData(OrderDataQuery.Data fresh) {
        if (!fresh.equals(data)) {
            revision++;
            preview = null;
        }
        data = fresh;
        cart.updateCatalog(fresh.products());
        loaded = true;
    }

    private void changed() {
        revision++;
        preview = null;
        outcome = null;
        message = "Preview required for the current selections.";
        error = false;
        publish();
    }

    private void ensureEditable() {
        if (confirming) throw new IllegalStateException("Order confirmation is running");
    }

    private void publish() { listeners.forEach(Runnable::run); }

    public synchronized State snapshot() {
        boolean valid;
        try { request(false); valid = true; }
        catch (RuntimeException invalidInput) { valid = false; }
        return new State(data, cart.lines(), cart.subtotal(), customerId, pointsText, fulfillment,
            preview, receipt, outcome, outcomeConfirmation, message, error, busy, loaded,
            valid && !busy, valid && !busy && preview != null && preview.revision() == revision, revision);
    }

    private record Captured(PlaceOrderRequest request, FulfillmentType fulfillment, long revision) { }
    public record Quote(PlaceOrderResponse response, FulfillmentType fulfillment, long revision) { }
    public record Receipt(PlaceOrderResponse response, FulfillmentType fulfillment) { }
    public record State(OrderDataQuery.Data data, List<ShoppingCart.Line> cart, BigDecimal estimatedSubtotal,
                        UUID customerId, String pointsText, FulfillmentType fulfillment, Quote preview,
                        Receipt receipt, PlaceOrderResponse outcome, boolean outcomeConfirmation,
                        String message, boolean error, boolean busy, boolean loaded,
                        boolean canPreview, boolean canConfirm, long revision) { }
}
