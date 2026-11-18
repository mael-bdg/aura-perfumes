package entities;

/** Order statuses shared by order placement and fulfillment. */
public enum OrderStatus {
    PENDING_CONFIRMATION,
    CONFIRMED,
    IN_PREPARATION,
    READY_FOR_PICKUP,
    CANCELLED,
    REFUNDED
}
