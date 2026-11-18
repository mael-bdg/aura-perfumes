package controller;

import datatransferobjects.BookConsultationRequest;
import datatransferobjects.ConsultationAvailabilityRequest;

import usecases.BookConsultationRequestHandler;
import usecases.ConsultationAvailabilityRequestHandler;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class ConsultationController {

    private final ConsultationAvailabilityRequestHandler availabilityHandler;
    private final BookConsultationRequestHandler bookingHandler;
    private final ConsultationOutputBoundary outputBoundary;

    public ConsultationController(
            ConsultationAvailabilityRequestHandler availabilityHandler,
            BookConsultationRequestHandler bookingHandler,
            ConsultationOutputBoundary outputBoundary) {

        this.availabilityHandler = Objects.requireNonNull(availabilityHandler);
        this.bookingHandler = Objects.requireNonNull(bookingHandler);
        this.outputBoundary = Objects.requireNonNull(outputBoundary);
    }

    public void listAvailableSlots(LocalDate date, UUID associateId) {

        ConsultationAvailabilityRequest request =
            new ConsultationAvailabilityRequest(date, associateId);

        outputBoundary.presentAvailability(
            availabilityHandler.handle(request)
        );
    }

    public void bookConsultation(BookConsultationRequest request) {

        outputBoundary.presentBooking(
            bookingHandler.handle(request)
        );
    }
}
