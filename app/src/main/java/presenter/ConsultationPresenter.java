package presenter;

import controller.ConsultationOutputBoundary;
import datatransferobjects.BookConsultationResponse;
import datatransferobjects.ConsultationAvailabilityResponse;
import entities.ConsultationSlot;

import java.util.Objects;

public class ConsultationPresenter
        implements ConsultationOutputBoundary {

    private final ConsultationViewBoundary view;

    public ConsultationPresenter(ConsultationViewBoundary view) {
        this.view = Objects.requireNonNull(view);
    }

    @Override
    public void presentAvailability(
            ConsultationAvailabilityResponse response) {

        if (!response.isSuccess()) {
            view.display("Error: " + response.getMessage());
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(response.getMessage()).append("\n");

        if (response.getAvailableSlots().isEmpty()) {
            message.append("No consultation slots available.");
        } else {
            message.append("Available consultation slots:\n");

            for (ConsultationSlot slot : response.getAvailableSlots()) {
                message.append("- ")
                       .append(slot.getStartTime())
                       .append(" | Associate: ")
                       .append(slot.getAssociateId())
                       .append(" | Duration: ")
                       .append(slot.getDurationMinutes())
                       .append(" minutes\n");
            }
        }

        view.display(message.toString());
    }

    @Override
    public void presentBooking(BookConsultationResponse response) {

        if (!response.isSuccess()) {
            view.display("Booking failed: " + response.getMessage());
            return;
        }

        StringBuilder message = new StringBuilder();

        message.append("Consultation successfully booked!\n")
               .append("Booking ID: ")
               .append(response.getBookingId())
               .append("\n")
               .append("Associate ID: ")
               .append(response.getAssignedAssociateId())
               .append("\n")
               .append("Scheduled time: ")
               .append(response.getScheduledStartTime())
               .append("\n")
               .append("Confirmation email: ")
               .append(response.isEmailSent() ? "Sent" : "Not sent")
               .append("\n")
               .append(response.getMessage());

        view.display(message.toString());
    }
}
