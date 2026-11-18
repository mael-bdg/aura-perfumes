package usecases;

import datatransferobjects.ConsultationAvailabilityRequest;
import datatransferobjects.ConsultationAvailabilityResponse;
import entities.ConsultationSlot;
import gateways.AssociateScheduleRepository;

import java.util.List;
import java.util.Objects;

public class CheckConsultationAvailabilityInteractor
        implements ConsultationAvailabilityRequestHandler {

    private final AssociateScheduleRepository scheduleRepository;

    public CheckConsultationAvailabilityInteractor(
            AssociateScheduleRepository scheduleRepository) {

        this.scheduleRepository = Objects.requireNonNull(
            scheduleRepository,
            "Schedule repository cannot be null"
        );
    }

    @Override
    public ConsultationAvailabilityResponse handle(
            ConsultationAvailabilityRequest request) {

        if (request == null) {
            return new ConsultationAvailabilityResponse(
                false,
                List.of(),
                "Availability request cannot be null"
            );
        }

        try {
            List<ConsultationSlot> availableSlots =
                scheduleRepository.findAvailableSlots(
                    request.getPreferredDate(),
                    request.getAssociateId()
                );

            if (availableSlots == null) {
                return new ConsultationAvailabilityResponse(
                    false,
                    List.of(),
                    "Unable to retrieve available slots"
                );
            }

            if (availableSlots.isEmpty()) {
                return new ConsultationAvailabilityResponse(
                    true,
                    List.of(),
                    "No consultation slots available"
                );
            }

            return new ConsultationAvailabilityResponse(
                true,
                availableSlots,
                "Available consultation slots found"
            );

        } catch (RuntimeException exception) {
            return new ConsultationAvailabilityResponse(
                false,
                List.of(),
                "Unable to retrieve consultation availability"
            );
        }
    }
}

