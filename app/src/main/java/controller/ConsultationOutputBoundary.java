package controller;

import datatransferobjects.ConsultationAvailabilityResponse;
import datatransferobjects.BookConsultationResponse;

public interface ConsultationOutputBoundary {

    void presentAvailability(
        ConsultationAvailabilityResponse response
    );

    void presentBooking(
        BookConsultationResponse response
    );
}

