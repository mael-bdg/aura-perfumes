package datatransferobjects;

import entities.OrderStatus;
import java.util.UUID;

public class ProcessBoutiqueOrderRequest {

    private final UUID orderId;
    private final UUID employeeId;
    private final OrderStatus targetStatus;
    private final String cancellationReason;
    private final String pickupPin;

    public ProcessBoutiqueOrderRequest(
            UUID orderId,
            UUID employeeId,
            OrderStatus targetStatus,
            String cancellationReason,
            String pickupPin) {

        this.orderId = orderId;
        this.employeeId = employeeId;
        this.targetStatus = targetStatus;
        this.cancellationReason = cancellationReason;
        this.pickupPin = pickupPin;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getPickupPin() {
        return pickupPin;
    }
}

