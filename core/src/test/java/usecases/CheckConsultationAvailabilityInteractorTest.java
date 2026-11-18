package usecases;

import datatransferobjects.ConsultationAvailabilityRequest;
import datatransferobjects.ConsultationAvailabilityResponse;
import entities.ConsultationSlot;
import entities.SalesAssociate;
import gateways.AssociateScheduleRepository;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CheckConsultationAvailabilityInteractorTest {

    @Test
    void shouldReturnAvailableConsultationSlots() {

        UUID associateId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);

        ConsultationSlot slot = new ConsultationSlot(
                UUID.randomUUID(),
                associateId,
                date.atTime(10, 0)
        );

        // Simulate a repository with one available slot
        AssociateScheduleRepository scheduleRepository =
                new AssociateScheduleRepository() {

            @Override
            public List<ConsultationSlot> findAvailableSlots(
                    LocalDate requestedDate,
                    UUID requestedAssociateId) {

                return List.of(slot);
            }

            @Override
            public SalesAssociate findLeastBookedAvailableAssociate(
                    LocalDateTime startTime) {

                return null;
            }
        };

        CheckConsultationAvailabilityInteractor interactor =
                new CheckConsultationAvailabilityInteractor(
                        scheduleRepository
                );

        ConsultationAvailabilityRequest request =
                new ConsultationAvailabilityRequest(
                        date,
                        associateId
                );

        ConsultationAvailabilityResponse response =
                interactor.handle(request);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getAvailableSlots().size());
        assertEquals(
                slot.getId(),
                response.getAvailableSlots().get(0).getId()
        );
    }

    @Test
    void shouldReturnEmptyListWhenNoSlotsAreAvailable() {

        UUID associateId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);

        // Simulate a repository with no available slots
        AssociateScheduleRepository scheduleRepository =
                new AssociateScheduleRepository() {

                    @Override
                    public List<ConsultationSlot> findAvailableSlots(
                            LocalDate requestedDate,
                            UUID requestedAssociateId) {

                        return List.of();
                    }

                    @Override
                    public SalesAssociate findLeastBookedAvailableAssociate(
                            LocalDateTime startTime) {

                        return null;
                    }
                };

        CheckConsultationAvailabilityInteractor interactor =
                new CheckConsultationAvailabilityInteractor(
                        scheduleRepository
                );

        ConsultationAvailabilityRequest request =
                new ConsultationAvailabilityRequest(
                        date,
                        associateId
                );

        ConsultationAvailabilityResponse response =
                interactor.handle(request);

        assertTrue(response.isSuccess());
        assertTrue(response.getAvailableSlots().isEmpty());
    }

    @Test
    void shouldHandleRepositoryFailure() {

        UUID associateId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);

        // Simulate a repository failure
        AssociateScheduleRepository scheduleRepository =
                new AssociateScheduleRepository() {

                    @Override
                    public List<ConsultationSlot> findAvailableSlots(
                            LocalDate requestedDate,
                            UUID requestedAssociateId) {

                        throw new IllegalStateException(
                                "Database connection failed"
                        );
                    }

                    @Override
                    public SalesAssociate findLeastBookedAvailableAssociate(
                            LocalDateTime startTime) {

                        return null;
                    }
                };

        CheckConsultationAvailabilityInteractor interactor =
                new CheckConsultationAvailabilityInteractor(
                        scheduleRepository
                );

        ConsultationAvailabilityRequest request =
                new ConsultationAvailabilityRequest(
                        date,
                        associateId
                );

        ConsultationAvailabilityResponse response =
                assertDoesNotThrow(() -> interactor.handle(request));

        assertFalse(response.isSuccess());
        assertNotNull(response.getMessage());
    }

    @Test
    void shouldRejectAvailabilityRequestWithoutDate() {

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new ConsultationAvailabilityRequest(
                        null,
                        UUID.randomUUID()
                )
        );

        assertEquals(
                "Preferred date cannot be null",
                exception.getMessage()
        );
    }
}

