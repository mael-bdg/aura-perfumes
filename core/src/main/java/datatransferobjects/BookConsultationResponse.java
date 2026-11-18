package datatransferobjects;

import java.time.LocalDateTime;
import java.util.UUID;

public class BookConsultationResponse {

    private final boolean success;
    private final UUID bookingId;
    private final UUID assignedAssociateId;
    private final LocalDateTime scheduledStartTime;
    private final boolean emailSent;
    private final String message;

    public BookConsultationResponse(
            boolean success,
            UUID bookingId,
            UUID assignedAssociateId,
            LocalDateTime scheduledStartTime,
            boolean emailSent,
            String message) {

        this.success = success;
        this.bookingId = bookingId;
        this.assignedAssociateId = assignedAssociateId;
        this.scheduledStartTime = scheduledStartTime;
        this.emailSent = emailSent;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getAssignedAssociateId() {
        return assignedAssociateId;
    }

    public LocalDateTime getScheduledStartTime() {
        return scheduledStartTime;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public String getMessage() {
        return message;
    }
}

