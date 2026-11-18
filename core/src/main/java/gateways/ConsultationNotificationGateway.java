package gateways;

import entities.ScentConsultation;

public interface ConsultationNotificationGateway {

    boolean sendConfirmation(
        ScentConsultation booking
    );
}

