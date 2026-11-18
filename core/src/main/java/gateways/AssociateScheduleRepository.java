package gateways;

import entities.ConsultationSlot;
import entities.SalesAssociate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AssociateScheduleRepository {

    List<ConsultationSlot> findAvailableSlots(
        LocalDate date,
        UUID associateId
    );

    SalesAssociate findLeastBookedAvailableAssociate(
        LocalDateTime startTime
    );
}

