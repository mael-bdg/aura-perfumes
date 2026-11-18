package repositories;

import database.SQLiteConnection;
import database.SQLiteDatabaseInitializer;
import entities.ConsultationSlot;
import entities.ScentConsultation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.UUID;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConsultationBookingRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception {

        Path databaseFile =
                temporaryDirectory.resolve("booking-test.db");

        SQLiteConnection.setDatabaseUrl(
                "jdbc:sqlite:" + databaseFile.toAbsolutePath()
        );

        SQLiteDatabaseInitializer.initialize();
    }

    @AfterEach
    void tearDown() {
        SQLiteConnection.resetDatabaseUrl();
    }

    @Test
    void shouldSaveAndRetrieveBookingFromSQLite() throws Exception {

        UUID associateId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        LocalDateTime startTime =
                LocalDateTime.of(2026, 11, 20, 10, 0);

        // Prepare an associate and a consultation slot
        try (Connection connection = SQLiteConnection.getConnection()) {

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO sales_associates (id, name)
                        VALUES (?, ?)
                        """)) {

                statement.setString(1, associateId.toString());
                statement.setString(2, "Emma");
                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO consultation_slots
                            (id, associate_id, start_time, duration_minutes)
                        VALUES (?, ?, ?, 30)
                        """)) {

                statement.setString(1, slotId.toString());
                statement.setString(2, associateId.toString());
                statement.setString(3, startTime.toString());
                statement.executeUpdate();
            }
        }

        ConsultationSlot slot = new ConsultationSlot(
                slotId,
                associateId,
                startTime
        );

        ScentConsultation booking = new ScentConsultation(
                bookingId,
                "Alice Martin",
                "alice@example.com",
                "Floral fragrances",
                slot
        );

        DatabaseConsultationBookingRepository repository =
                new DatabaseConsultationBookingRepository();

        // Save the booking
        boolean saved = repository.reserveSlotAndSave(booking);

        assertTrue(saved);

        // Retrieve the booking
        ScentConsultation retrieved =
                repository.findById(bookingId);

        assertNotNull(retrieved);
        assertEquals(bookingId, retrieved.getBookingId());
        assertEquals("Alice Martin", retrieved.getCustomerName());
        assertEquals("alice@example.com", retrieved.getCustomerEmail());
        assertEquals(slotId, retrieved.getSlot().getId());
        assertEquals(startTime, retrieved.getSlot().getStartTime());
    }

    @Test
    void shouldPreventDoubleBookingInSQLite() throws Exception {

        UUID associateId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        LocalDateTime startTime =
                LocalDateTime.of(2026, 11, 20, 10, 0);

        // Prepare an associate and a consultation slot
        try (Connection connection = SQLiteConnection.getConnection()) {

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO sales_associates (id, name)
                        VALUES (?, ?)
                        """)) {

                statement.setString(1, associateId.toString());
                statement.setString(2, "Emma");
                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO consultation_slots
                            (id, associate_id, start_time, duration_minutes)
                        VALUES (?, ?, ?, 30)
                        """)) {

                statement.setString(1, slotId.toString());
                statement.setString(2, associateId.toString());
                statement.setString(3, startTime.toString());
                statement.executeUpdate();
            }
        }

        ConsultationSlot slot = new ConsultationSlot(
                slotId,
                associateId,
                startTime
        );

        // First customer
        ScentConsultation aliceBooking = new ScentConsultation(
                UUID.randomUUID(),
                "Alice Martin",
                "alice@example.com",
                "Floral fragrances",
                slot
        );

        // Second customer requests the same slot
        ScentConsultation bobBooking = new ScentConsultation(
                UUID.randomUUID(),
                "Bob Dupont",
                "bob@example.com",
                "Woody fragrances",
                slot
        );

        DatabaseConsultationBookingRepository repository =
                new DatabaseConsultationBookingRepository();

        boolean aliceSaved =
                repository.reserveSlotAndSave(aliceBooking);

        boolean bobSaved =
                repository.reserveSlotAndSave(bobBooking);

        assertTrue(aliceSaved, "Alice's booking should succeed");
        assertFalse(bobSaved, "Bob's booking must be rejected");

        assertNotNull(repository.findById(aliceBooking.getBookingId()));
        assertNull(repository.findById(bobBooking.getBookingId()));
    }

    @Test
    void shouldNotSaveBookingWhenSlotDoesNotExist() throws Exception {

        UUID associateId = UUID.randomUUID();
        UUID nonexistentSlotId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        LocalDateTime startTime =
                LocalDateTime.of(2026, 11, 20, 10, 0);

        // This slot exists only as a Java object.
        // It has NOT been inserted into SQLite.
        ConsultationSlot nonexistentSlot = new ConsultationSlot(
                nonexistentSlotId,
                associateId,
                startTime
        );

        ScentConsultation booking = new ScentConsultation(
                bookingId,
                "Alice Martin",
                "alice@example.com",
                "Floral fragrances",
                nonexistentSlot
        );

        DatabaseConsultationBookingRepository repository =
                new DatabaseConsultationBookingRepository();

        // Attempt to reserve a slot that does not exist
        boolean saved = repository.reserveSlotAndSave(booking);

        // The reservation must be rejected
        assertFalse(saved);

        // No booking should exist in the database
        ScentConsultation retrieved =
                repository.findById(bookingId);

        assertNull(retrieved);

        // Verify that the database contains no reservations
        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT COUNT(*) FROM scent_consultations"
                     );
             java.sql.ResultSet resultSet = statement.executeQuery()) {

            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
        }
    }

    @Test
    void shouldPreventConcurrentDoubleBooking() throws Exception {

        UUID associateId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        LocalDateTime startTime =
                LocalDateTime.of(2026, 11, 20, 10, 0);

        // Prepare the associate and the slot
        try (Connection connection = SQLiteConnection.getConnection()) {

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO sales_associates (id, name)
                        VALUES (?, ?)
                        """)) {

                statement.setString(1, associateId.toString());
                statement.setString(2, "Emma");
                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO consultation_slots
                            (id, associate_id, start_time, duration_minutes)
                        VALUES (?, ?, ?, 30)
                        """)) {

                statement.setString(1, slotId.toString());
                statement.setString(2, associateId.toString());
                statement.setString(3, startTime.toString());
                statement.executeUpdate();
            }
        }

        ConsultationSlot slot =
                new ConsultationSlot(slotId, associateId, startTime);

        ScentConsultation aliceBooking = new ScentConsultation(
                UUID.randomUUID(),
                "Alice Martin",
                "alice@example.com",
                "Floral",
                slot
        );

        ScentConsultation bobBooking = new ScentConsultation(
                UUID.randomUUID(),
                "Bob Dupont",
                "bob@example.com",
                "Woody",
                slot
        );

        DatabaseConsultationBookingRepository repository =
                new DatabaseConsultationBookingRepository();

        CountDownLatch startSignal = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> aliceResult = executor.submit(() -> {
                startSignal.await();
                return repository.reserveSlotAndSave(aliceBooking);
            });

            Future<Boolean> bobResult = executor.submit(() -> {
                startSignal.await();
                return repository.reserveSlotAndSave(bobBooking);
            });

            // Release both threads at approximately the same time
            startSignal.countDown();

            boolean aliceSaved = aliceResult.get(10, TimeUnit.SECONDS);
            boolean bobSaved = bobResult.get(10, TimeUnit.SECONDS);

            // Exactly one booking must succeed
            assertNotEquals(aliceSaved, bobSaved);

            // The database must contain exactly one booking
            try (Connection connection = SQLiteConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(
                                 "SELECT COUNT(*) FROM scent_consultations"
                         );
                 java.sql.ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }

        } finally {
            executor.shutdownNow();
        }
    }
}

