package entities;

import java.util.Objects;
import java.util.UUID;

public class ScentConsultation extends Entity {

    private final String customerName;
    private final String customerEmail;
    private final String fragrancePreferences;
    private final ConsultationSlot slot;
    private final ConsultationStatus status;

    public ScentConsultation(
            UUID id,
            String customerName,
            String customerEmail,
            String fragrancePreferences,
            ConsultationSlot slot) {

        super(id);

        this.customerName = Objects.requireNonNull(
            customerName, "Customer name cannot be null"
        );

        this.customerEmail = Objects.requireNonNull(
            customerEmail, "Customer email cannot be null"
        );

        this.fragrancePreferences = fragrancePreferences;

        this.slot = Objects.requireNonNull(
            slot, "Consultation slot cannot be null"
        );

        this.status = ConsultationStatus.CONFIRMED;
    }

    public UUID getBookingId() {
        return getId();
    }

    public ConsultationSlot getSlot() {
        return slot;
    }

    public ConsultationStatus getStatus() {
        return status;
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
}

