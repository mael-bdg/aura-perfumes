package entities;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class OrderAuditEntry extends Entity {

    private final UUID orderId;
    private final UUID employeeId;
    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
    private final String reason;
    private final LocalDateTime timestamp;

    public OrderAuditEntry(
            UUID id,
            UUID orderId,
            UUID employeeId,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            String reason,
            LocalDateTime timestamp) {

        super(id);

        this.orderId = Objects.requireNonNull(
            orderId, "Order ID cannot be null"
        );

        this.employeeId = Objects.requireNonNull(
            employeeId, "Employee ID cannot be null"
        );

        this.previousStatus = Objects.requireNonNull(
            previousStatus, "Previous status cannot be null"
        );

        this.newStatus = Objects.requireNonNull(
            newStatus, "New status cannot be null"
        );

        this.reason = reason;

        this.timestamp = Objects.requireNonNull(
            timestamp, "Timestamp cannot be null"
        );
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public OrderStatus getPreviousStatus() {
        return previousStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}

