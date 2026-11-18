package usecases;

import datatransferobjects.BookConsultationRequest;
import datatransferobjects.BookConsultationResponse;

public interface BookConsultationRequestHandler {

    BookConsultationResponse handle(
        BookConsultationRequest request
    );
}

