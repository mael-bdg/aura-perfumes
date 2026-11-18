package datatransferobjects;

import entities.ConsultationSlot;
import java.util.List;
import java.util.Objects;

public class ConsultationAvailabilityResponse {

    private final boolean success;
    private final List<ConsultationSlot> availableSlots;
    private final String message;

    public ConsultationAvailabilityResponse(
            boolean success,
            List<ConsultationSlot> availableSlots,
            String message) {

        this.success = success;

        this.availableSlots = List.copyOf(
            Objects.requireNonNull(
                availableSlots,
                "Available slots cannot be null"
            )
        );

        this.message = Objects.requireNonNull(
            message,
            "Message cannot be null"
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public List<ConsultationSlot> getAvailableSlots() {
        return availableSlots;
    }

    public String getMessage() {
        return message;
    }
}

