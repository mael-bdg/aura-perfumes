package gui.consultations;

import controller.ConsultationController;
import database.SQLiteDatabaseInitializer;
import database.SQLiteTestDataSeeder;
import notifications.EmailConsultationNotificationGateway;
import repositories.DatabaseAssociateScheduleRepository;
import repositories.DatabaseConsultationBookingRepository;
import usecases.BookConsultationInteractor;
import usecases.CheckConsultationAvailabilityInteractor;

/** Application-scoped U3 dependencies. Initialize once, never per navigation. */
public final class ConsultationDependencies {
    private static final ConsultationDependencies INSTANCE = new ConsultationDependencies();
    private final CheckConsultationAvailabilityInteractor availability;
    private final BookConsultationInteractor booking;

    private ConsultationDependencies() {
        try {
            SQLiteDatabaseInitializer.initialize();
            SQLiteTestDataSeeder.seed();
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to initialize consultation storage", exception);
        }
        var schedules = new DatabaseAssociateScheduleRepository();
        availability = new CheckConsultationAvailabilityInteractor(schedules);
        booking = new BookConsultationInteractor(schedules,
            new DatabaseConsultationBookingRepository(),
            new EmailConsultationNotificationGateway());
    }

    public static ConsultationDependencies getInstance() { return INSTANCE; }
    public ConsultationController controllerFor(ConsultationFXController view) {
        return new ConsultationController(availability, booking, view);
    }
}
