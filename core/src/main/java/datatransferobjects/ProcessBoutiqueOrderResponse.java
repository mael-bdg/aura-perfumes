package datatransferobjects;

import entities.OrderStatus;
import java.util.UUID;

public class ProcessBoutiqueOrderResponse {

    private final boolean success;
    private final UUID orderId;
    private final OrderStatus newStatus;
    private final String message;

    public ProcessBoutiqueOrderResponse(
            boolean success,
            UUID orderId,
            OrderStatus newStatus,
            String message) {

        this.success = success;
        this.orderId = orderId;
        this.newStatus = newStatus;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public String getMessage() {
        return message;
    }
}
