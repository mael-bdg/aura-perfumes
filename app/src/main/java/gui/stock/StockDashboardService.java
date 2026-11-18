package gui.stock;

import datatransferobjects.StockRequest;
import datatransferobjects.StockResponse;
import inventory.InventoryQuery;
import inventory.InventoryRow;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import usecases.StockRequestHandler;

/** Runs inventory reads and the existing U1 boundary on the injected application worker. */
public final class StockDashboardService {
    private final InventoryQuery inventory;
    private final StockRequestHandler handler;
    private final Executor worker;
    private final AtomicBoolean operationPending = new AtomicBoolean();

    public StockDashboardService(InventoryQuery inventory, StockRequestHandler handler, Executor worker) {
        this.inventory = Objects.requireNonNull(inventory);
        this.handler = Objects.requireNonNull(handler);
        this.worker = Objects.requireNonNull(worker);
    }

    public CompletableFuture<List<InventoryRow>> refresh() {
        return CompletableFuture.supplyAsync(() -> List.copyOf(inventory.load()), worker);
    }

    /** Claims the operation before scheduling to reject duplicate submissions across views. */
    public CompletableFuture<OperationResult> execute(StockRequest request) {
        Objects.requireNonNull(request);
        if (!operationPending.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A stock operation is already running"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                StockResponse response = Objects.requireNonNull(handler.handle(request), "U1 response is required");
                try {
                    return new OperationResult(response, List.copyOf(inventory.load()), null);
                } catch (RuntimeException refreshFailure) {
                    return new OperationResult(response, null, refreshFailure);
                }
            }, worker).whenComplete((result, failure) -> operationPending.set(false));
        } catch (RuntimeException schedulingFailure) {
            operationPending.set(false);
            return CompletableFuture.failedFuture(schedulingFailure);
        }
    }

    /** Keeps a confirmed use-case outcome even when a subsequent refresh fails. */
    public record OperationResult(StockResponse response, List<InventoryRow> rows, RuntimeException refreshFailure) { }
}
