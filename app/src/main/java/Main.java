import controller.ConsultationController;
import controller.ConsultationOutputBoundary;

import database.SQLiteDatabaseInitializer;

import gateways.AssociateScheduleRepository;
import gateways.ConsultationBookingRepository;
import gateways.ConsultationNotificationGateway;

import notifications.EmailConsultationNotificationGateway;

import presenter.ConsultationPresenter;
import presenter.ConsultationViewBoundary;

import repositories.DatabaseAssociateScheduleRepository;
import repositories.DatabaseConsultationBookingRepository;

import usecases.BookConsultationInteractor;
import usecases.BookConsultationRequestHandler;
import usecases.CheckConsultationAvailabilityInteractor;
import usecases.ConsultationAvailabilityRequestHandler;

import view.ConsoleConsultationView;
import database.SQLiteTestDataSeeder;
import java.time.LocalDate;

import datatransferobjects.BookConsultationRequest;
import java.util.UUID;


import controller.BoutiqueOrderController;
import presenter.BoutiqueOrderPresenter;

import usecases.ProcessBoutiqueOrderInteractor;

import repositories.DatabaseBoutiqueOrderRepository;
import repositories.DatabaseBoutiqueEmployeeRepository;

import entities.OrderStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import database.SQLiteConnection;


public class Main {

    public static void main(String[] args) throws Exception {

        // 1. Initialize SQLite database
        SQLiteDatabaseInitializer.initialize();
        SQLiteTestDataSeeder.seed();

        // 2. Create repositories
        AssociateScheduleRepository scheduleRepository =
                new DatabaseAssociateScheduleRepository();

        ConsultationBookingRepository bookingRepository =
                new DatabaseConsultationBookingRepository();

        // 3. Create notification service
        ConsultationNotificationGateway notificationGateway =
                new EmailConsultationNotificationGateway();

        // 4. Create use case interactors
        ConsultationAvailabilityRequestHandler availabilityHandler =
                new CheckConsultationAvailabilityInteractor(
                        scheduleRepository
                );

        BookConsultationRequestHandler bookingHandler =
                new BookConsultationInteractor(
                        scheduleRepository,
                        bookingRepository,
                        notificationGateway
                );

        // 5. Create view and presenter
        ConsultationViewBoundary view =
                new ConsoleConsultationView();

        ConsultationOutputBoundary presenter =
                new ConsultationPresenter(view);

        // 6. Create controller
        ConsultationController controller =
                new ConsultationController(
                        availabilityHandler,
                        bookingHandler,
                        presenter
                );

        DatabaseBoutiqueOrderRepository boutiqueOrderRepository =
                new DatabaseBoutiqueOrderRepository();

        DatabaseBoutiqueEmployeeRepository boutiqueEmployeeRepository =
                new DatabaseBoutiqueEmployeeRepository();

        ProcessBoutiqueOrderInteractor boutiqueOrderInteractor =
                new ProcessBoutiqueOrderInteractor(
                        boutiqueOrderRepository,
                        boutiqueEmployeeRepository
                );

        BoutiqueOrderPresenter boutiqueOrderPresenter =
                new BoutiqueOrderPresenter(
                        new ConsoleConsultationView()
                );

        BoutiqueOrderController boutiqueOrderController =
                new BoutiqueOrderController(
                        boutiqueOrderInteractor,
                        boutiqueOrderPresenter
                );

        System.out.println("UC4 - Boutique order processing initialized.");


        // Test UC3: display available consultation slots
            System.out.println();
            System.out.println("===== AVAILABLE CONSULTATION SLOTS =====");

            controller.listAvailableSlots(
              LocalDate.now().plusDays(1),
              null
            );

            // Test UC3: book a scent consultation
            System.out.println();
            System.out.println("===== BOOK A CONSULTATION =====");

            BookConsultationRequest bookingRequest =
                   new BookConsultationRequest(
                      "Alice Martin",
                      "alice@example.com",
                      "Floral and fresh fragrances",
                      LocalDate.now().plusDays(1).atTime(10, 0),
                      null,
                      true
                    );

            controller.bookConsultation(bookingRequest);

            // Check availability after booking
            System.out.println();
            System.out.println("===== AVAILABILITY AFTER BOOKING =====");

            controller.listAvailableSlots(
              LocalDate.now().plusDays(1),
              null
            );

            System.out.println();
            System.out.println("===== DOUBLE BOOKING TEST =====");

            BookConsultationRequest doubleBookingRequest =
                   new BookConsultationRequest(
                      "Bob Dupont",
                      "bob@example.com",
                      "Woody fragrances",
                      LocalDate.now().plusDays(1).atTime(10, 0),
                      UUID.fromString("11111111-1111-1111-1111-111111111111"), // Same slot ID as Alice's booking
                      false
                    );

            controller.bookConsultation(doubleBookingRequest);

        System.out.println("Aura Perfumes application initialized.");
        System.out.println("SQLite database is ready.");
    }
}

