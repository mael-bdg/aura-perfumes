package gui.navigation;

import controller.ConsultationController;
import database.SQLiteDatabaseInitializer;
import database.SQLiteTestDataSeeder;
import gateways.AssociateScheduleRepository;
import gateways.ConsultationBookingRepository;
import gateways.ConsultationNotificationGateway;
import gui.consultations.ConsultationFXController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import notifications.EmailConsultationNotificationGateway;
import repositories.DatabaseAssociateScheduleRepository;
import repositories.DatabaseConsultationBookingRepository;
import usecases.BookConsultationInteractor;
import usecases.BookConsultationRequestHandler;
import usecases.CheckConsultationAvailabilityInteractor;
import usecases.ConsultationAvailabilityRequestHandler;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;

public final class NavigationManager {

    private final BorderPane applicationShell;
    private final Callback<Class<?>, Object> controllerFactory;

    public NavigationManager(BorderPane applicationShell) {
        this(applicationShell, null);
    }

    /** Reuses the application's controller factory when loading a destination page. */
    public NavigationManager(BorderPane applicationShell, Callback<Class<?>, Object> controllerFactory) {
        this.applicationShell = Objects.requireNonNull(applicationShell);
        this.controllerFactory = controllerFactory;
    }

    public void navigateTo(String fxmlPath) {
        Objects.requireNonNull(fxmlPath);

        URL resource = NavigationManager.class.getResource(fxmlPath);

        if (resource == null) {
            throw new IllegalArgumentException(
                "FXML resource not found: " + fxmlPath
            );
        }

        try {
            FXMLLoader loader = new FXMLLoader(resource);
            if (controllerFactory != null) loader.setControllerFactory(controllerFactory);
            Node page = loader.load();

            if (loader.getController() instanceof ConsultationFXController consultationView) {
                consultationView.setController(
                    ConsultationDependencies.createController(consultationView)
                );
            }

            applicationShell.setCenter(page);

        } catch (IOException exception) {
            throw new IllegalStateException(
                "Unable to load page: " + fxmlPath,
                exception
            );
        }
    }

    private static final class ConsultationDependencies {

        private static final AssociateScheduleRepository scheduleRepository;
        private static final ConsultationBookingRepository bookingRepository;
        private static final ConsultationNotificationGateway notificationGateway;

        
    static {
        try {
            SQLiteDatabaseInitializer.initialize();
            SQLiteTestDataSeeder.seed();
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(
                "Unable to initialize the consultation database",
                exception
           );
        }

        scheduleRepository = new DatabaseAssociateScheduleRepository();
        bookingRepository = new DatabaseConsultationBookingRepository();
        notificationGateway = new EmailConsultationNotificationGateway();
    }


        private static ConsultationController createController(
                ConsultationFXController consultationView) {

            ConsultationAvailabilityRequestHandler availabilityHandler =
                new CheckConsultationAvailabilityInteractor(scheduleRepository);

            BookConsultationRequestHandler bookingHandler =
                new BookConsultationInteractor(
                    scheduleRepository,
                    bookingRepository,
                    notificationGateway
                );

            return new ConsultationController(
                availabilityHandler,
                bookingHandler,
                consultationView
            );
        }
    }
}
