package notifications;

import entities.ScentConsultation;
import gateways.ConsultationNotificationGateway;

public class EmailConsultationNotificationGateway
        implements ConsultationNotificationGateway {

    @Override
    public boolean sendConfirmation(ScentConsultation booking) {

        if (booking == null
                || booking.getCustomerEmail() == null
                || booking.getCustomerEmail().isBlank()) {
            return false;
        }

        System.out.println();
        System.out.println("===== EMAIL CONFIRMATION (SIMULATION) =====");
        System.out.println("To: " + booking.getCustomerEmail());
        System.out.println("Subject: Aura Perfumes - Consultation Confirmation");
        System.out.println();
        System.out.println("Hello " + booking.getCustomerName() + ",");
        System.out.println("Your scent consultation is confirmed!");
        System.out.println("Booking ID: " + booking.getBookingId());
        System.out.println("Date and time: "
                + booking.getSlot().getStartTime());
        System.out.println("We look forward to welcoming you.");
        System.out.println("===========================================");

        return true;
    }
}

