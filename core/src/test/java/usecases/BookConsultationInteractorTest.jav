package usecases;

import datatransferobjects.BookConsultationRequest;
import datatransferobjects.BookConsultationResponse;
import gateways.AssociateScheduleRepository;
import gateways.ConsultationBookingRepository;
import gateways.ConsultationNotificationGateway;
import entities.ConsultationSlot;
import entities.SalesAssociate;
import entities.ScentConsultation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BookConsultationInteractorTest {

    @Test
    void shouldRejectBookingWhenCustomerNameIsEmpty() {

        // Gateways are not needed for this validation test
        AssociateScheduleRepository scheduleRepository = null;
        ConsultationBookingRepository bookingRepository = null;
        ConsultationNotificationGateway notificationGateway = null;

        BookConsultationInteractor interactor =
                new BookConsultationInteractor(
                        scheduleRepository,
                        bookingRepository,
                        notificationGateway
                );

        BookConsultationRequest request =
                new BookConsultationRequest(
                        "",
                        "alice@example.com",
                        "Floral fragrances",
                        LocalDateTime.now().plusDays(1),
                        null,
                        true
                );

        BookConsultationResponse response =
                interactor.handle(request);

        assertFalse(response.isSuccess());
    }

    BookConsultationRequest request =
            new BookConsultationRequest(
                    "Alice Martin",
                    "",
                    "Floral fragrances",
                    LocalDateTime.now().plusDays(1),
                    null,
                    true
            );
    
    BookConsultationResponse response =
            interactor.handle(request);

    assertFalse(response.isSuccess());

    BookConsultationRequest request =
            new BookConsultationRequest(
                    "Bob Dupont",
                    "bob@example.com",
                    "Woody fragrances",
                    LocalDateTime.now().plusDays(1),
                    associateId,
                    false
            );

    BookConsultationResponse response =
            interactor.handle(request);
    
    assertFalse(response.isSuccess());


    @Test
    void shouldBookConsultationWhenSlotIsAvailable() {

        UUID associateId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);

        ConsultationSlot availableSlot = new ConsulationSlot(
            UUID.randomUUID(),
            associateId,
            startTime
        );

        AssociateScheduleRepository scheduleRepository =
                new AssociateScheduleRepository() {

            @Override
            public List<ConsultationSlot> findAvailableSlots(
                    LocalDate date, UUID requestedAssociateId) {

                return List.of(availableSlot);
            }  

            @Override
            public SalesAssociate findLeastBookedAvailableAssociate(
                    LocalDateTime requestedTime) {

                return null;
            }
        };

        AtomicBoolean bookingSaved = new AtomicBoolean(false);
        AtomicBoolean emailSent = new AtomicBoolean(false);

        ConsultationBookingRepository bookingRepository =
                new ConsultationBookingRepository() {

            @Override
            public boolean reserveSlotAndSave(ScentConsultation booking) {
               bookingSaved.set(true);
               return true;
            }

            @Override
            public ScentConsultation findById(UUID bookingId) {
               return null;
            }
        };

        ConsultationNotificationGateway notificationGateway =
               new ConsultationNotificationGateway() {

            @Override
            public boolean sendConfirmation(ScentConsultation booking) {
               emailSent.set(true);
               return true;
            }
        };

        BookConsultationInteractor interactor =
                new BookConsultationInteractor(
                        scheduleRepository,
                        bookingRepository,
                        notificationGateway
                );

        BookConsultationRequest request =
                new BookConsultationRequest(
                        "Alice Martin",
                        "alice@example.com",
                        "Floral fragrances",
                        startTime,
                        associateId,
                        false
                );

        BookConsultationResponse response =
                interactor.handle(request);

        assertTrue(response.isSuccess());
        assertTrue(bookingSaved.get());
        assertTrue(emailSent.get());
        assertNotNull(response.getBookingId());
        assertEquals(associateId, response.getAssignedAssociateId());
    }

    @Test
    void shouldNotSendEmailWhenBookingFails() {

        UUID associateId = UUID.randomUUID();
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);

        ConsultationSlot availableSlot = new ConsultationSlot(
                UUID.randomUUID(),
                associateId,
                startTime
        );

        // Simulate an available consultation slot
        AssociateScheduleRepository scheduleRepository =
                new AssociateScheduleRepository() {

                    @Override
                    public List<ConsultationSlot> findAvailableSlots(
                            LocalDate date, UUID requestedAssociateId) {
                        return List.of(availableSlot);
                    }

                    @Override
                    public SalesAssociate findLeastBookedAvailableAssociate(
                            LocalDateTime requestedTime) {
                        return null;
                    }
                };

        // Simulate a database refusing the reservation
        AtomicBoolean bookingAttempted = new AtomicBoolean(false);

        ConsultationBookingRepository bookingRepository =
                new ConsultationBookingRepository() {

                    @Override
                    public boolean reserveSlotAndSave(ScentConsultation booking) {
                        bookingAttempted.set(true);
                        return false;
                    }

                    @Override
                    public ScentConsultation findById(UUID bookingId) {
                        return null;
                    }
                };

        // Track whether the notification service is called
        AtomicBoolean emailSent = new AtomicBoolean(false);

        ConsultationNotificationGateway notificationGateway =
                new ConsultationNotificationGateway() {

                    @Override
                    public boolean sendConfirmation(ScentConsultation booking) {
                        emailSent.set(true);
                        return true;
                    }
                };

        BookConsultationInteractor interactor =
                new BookConsultationInteractor(
                        scheduleRepository,
                        bookingRepository,
                        notificationGateway
                );

        BookConsultationRequest request =
                new BookConsultationRequest(
                        "Bob Dupont",
                        "bob@example.com",
                        "Woody fragrances",
                        startTime,
                        associateId,
                        false
                );

        BookConsultationResponse response =
                interactor.handle(request);

        assertFalse(response.isSuccess());
        assertTrue(bookingAttempted.get());
        assertFalse(emailSent.get());
    }
}
