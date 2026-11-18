package datatransferobjects;

import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

public class ConsultationAvailabilityRequest {

    private final LocalDate preferredDate;
    private final UUID associateId;

    public ConsultationAvailabilityRequest(
            LocalDate preferredDate,
            UUID associateId) {

        this.preferredDate = Objects.requireNonNull(
            preferredDate,
            "Preferred date cannot be null"
        );

        this.associateId = associateId;
    }

    public LocalDate getPreferredDate() {
        return preferredDate;
    }

    public UUID getAssociateId() {
        return associateId;
    }
}
