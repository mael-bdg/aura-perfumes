package datatransferobjects;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class BookConsultationRequest {

    private final String customerName;
    private final String customerEmail;
    private final String fragrancePreferences;
    private final LocalDateTime selectedStartTime;
    private final UUID preferredAssociateId;
    private final boolean anyAvailableAssociate;

    public BookConsultationRequest(
            String customerName,
            String customerEmail,
            String fragrancePreferences,
            LocalDateTime selectedStartTime,
            UUID preferredAssociateId,
            boolean anyAvailableAssociate) {

        this.customerName = Objects.requireNonNull(
            customerName, "Customer name cannot be null"
        );

        this.customerEmail = Objects.requireNonNull(
            customerEmail, "Customer email cannot be null"
        );

        this.fragrancePreferences = fragrancePreferences;

        this.selectedStartTime = Objects.requireNonNull(
            selectedStartTime, "Selected start time cannot be null"
        );

        this.preferredAssociateId = preferredAssociateId;
        this.anyAvailableAssociate = anyAvailableAssociate;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getFragrancePreferences() {
        return fragrancePreferences;
    }

    public LocalDateTime getSelectedStartTime() {
        return selectedStartTime;
    }

    public UUID getPreferredAssociateId() {
        return preferredAssociateId;
    }

    public boolean isAnyAvailableAssociate() {
        return anyAvailableAssociate;
    }
}
