package gateways;

import entities.ScentConsultation;
import java.util.UUID;

public interface ConsultationBookingRepository {

    boolean reserveSlotAndSave(
        ScentConsultation booking
    );

    ScentConsultation findById(
        UUID bookingId
    );
}
