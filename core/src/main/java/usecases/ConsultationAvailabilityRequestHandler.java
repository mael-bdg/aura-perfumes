package usecases;

import datatransferobjects.ConsultationAvailabilityRequest;
import datatransferobjects.ConsultationAvailabilityResponse;

public interface ConsultationAvailabilityRequestHandler {

    ConsultationAvailabilityResponse handle(
        ConsultationAvailabilityRequest request
    );
}

