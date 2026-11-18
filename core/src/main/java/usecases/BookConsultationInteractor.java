package usecases;

import datatransferobjects.BookConsultationRequest;
import datatransferobjects.BookConsultationResponse;
import entities.ConsultationSlot;
import entities.SalesAssociate;
import entities.ScentConsultation;
import gateways.AssociateScheduleRepository;
import gateways.ConsultationBookingRepository;
import gateways.ConsultationNotificationGateway;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class BookConsultationInteractor
        implements BookConsultationRequestHandler {

    private final AssociateScheduleRepository scheduleRepository;
    private final ConsultationBookingRepository bookingRepository;
    private final ConsultationNotificationGateway notificationGateway;

    public BookConsultationInteractor(
            AssociateScheduleRepository scheduleRepository,
            ConsultationBookingRepository bookingRepository,
            ConsultationNotificationGateway notificationGateway) {

        this.scheduleRepository = Objects.requireNonNull(scheduleRepository);
        this.bookingRepository = Objects.requireNonNull(bookingRepository);
        this.notificationGateway = Objects.requireNonNull(notificationGateway);
    }

    @Override
    public BookConsultationResponse handle(BookConsultationRequest request) {

        if (request == null
                || request.getCustomerName() == null
                || request.getCustomerName().isBlank()
                || request.getCustomerEmail() == null
                || !request.getCustomerEmail().matches(
                    "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                )
                || request.getSelectedStartTime() == null) {

            return failure("Invalid consultation request");
        }

        if (request.getSelectedStartTime().isBefore(LocalDateTime.now())) {
            return failure("Cannot book a consultation in the past");
        }

        try {
            UUID associateId = request.getPreferredAssociateId();

            if (request.isAnyAvailableAssociate()) {
                SalesAssociate associate =
                    scheduleRepository.findLeastBookedAvailableAssociate(
                        request.getSelectedStartTime()
                    );

                if (associate == null) {
                    return failure("No associate available");
                }

                associateId = associate.getAssociateId();

            } else if (associateId == null) {
                return failure("Please select an associate");
            }

            List<ConsultationSlot> availableSlots =
                scheduleRepository.findAvailableSlots(
                    request.getSelectedStartTime().toLocalDate(),
                    associateId
                );

            if (availableSlots == null) {
                return failure("Unable to retrieve available slots");
            }

            UUID selectedAssociateId = associateId;

            ConsultationSlot selectedSlot = availableSlots.stream()
                .filter(slot ->
                    slot.getAssociateId().equals(selectedAssociateId)
                    && slot.getStartTime().equals(
                        request.getSelectedStartTime()
                    )
                )
                .findFirst()
                .orElse(null);

            if (selectedSlot == null) {
                return failure("Selected consultation slot is unavailable");
            }

            ScentConsultation booking = new ScentConsultation(
                UUID.randomUUID(),
                request.getCustomerName(),
                request.getCustomerEmail(),
                request.getFragrancePreferences(),
                selectedSlot
            );

            boolean saved = bookingRepository.reserveSlotAndSave(booking);

            if (!saved) {
                return failure("The selected slot could not be reserved");
            }

            boolean emailSent;

            try {
                emailSent = notificationGateway.sendConfirmation(booking);
            } catch (RuntimeException exception) {
                emailSent = false;
            }

            return new BookConsultationResponse(
                true,
                booking.getBookingId(),
                associateId,
                selectedSlot.getStartTime(),
                emailSent,
                emailSent
                    ? "Consultation booked successfully"
                    : "Consultation booked, but confirmation email failed"
            );

        } catch (RuntimeException exception) {
            return failure("Unable to complete consultation booking");
        }
    }

    private BookConsultationResponse failure(String message) {
        return new BookConsultationResponse(
            false,
            null,
            null,
            null,
            false,
            message
        );
    }
}

