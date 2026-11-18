package repositories;

import database.SQLiteConnection;
import database.SQLiteDatabaseInitializer;
import entities.ConsultationSlot;
import entities.SalesAssociate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseAssociateScheduleRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception {

        Path databaseFile =
                temporaryDirectory.resolve("consultation-test.db");

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
    void shouldReturnAvailableSlotsFromSQLite() throws Exception {

        UUID associateId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        LocalDate date = LocalDate.of(2026, 11, 20);
        LocalDateTime startTime = date.atTime(10, 0);

        // Insert a test associate
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

            // Insert an available consultation slot
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

        DatabaseAssociateScheduleRepository repository =
                new DatabaseAssociateScheduleRepository();

        List<ConsultationSlot> availableSlots =
                repository.findAvailableSlots(date, associateId);

        assertEquals(1, availableSlots.size());
        assertEquals(slotId, availableSlots.get(0).getId());
        assertEquals(
                associateId,
                availableSlots.get(0).getAssociateId()
        );
        assertEquals(
                startTime,
                availableSlots.get(0).getStartTime()
        );
    }

    @Test
    void shouldExcludeBookedSlotsFromAvailability() throws Exception {

        UUID associateId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        LocalDate date = LocalDate.of(2026, 11, 20);
        LocalDateTime startTime = date.atTime(10, 0);

        try (Connection connection = SQLiteConnection.getConnection()) {

            // Create an associate
            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO sales_associates (id, name)
                        VALUES (?, ?)
                        """)) {

                statement.setString(1, associateId.toString());
                statement.setString(2, "Emma");
                statement.executeUpdate();
            }

            // Create a consultation slot
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

            // Book the consultation slot
            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO scent_consultations
                            (id, customer_name, customer_email,
                             fragrance_preferences, slot_id, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {

                statement.setString(1, bookingId.toString());
                statement.setString(2, "Alice Martin");
                statement.setString(3, "alice@example.com");
                statement.setString(4, "Floral fragrances");
                statement.setString(5, slotId.toString());
                statement.setString(6, "CONFIRMED");
                statement.executeUpdate();
            }
        }

        DatabaseAssociateScheduleRepository repository =
                new DatabaseAssociateScheduleRepository();

        List<ConsultationSlot> availableSlots =
                repository.findAvailableSlots(date, associateId);

        assertTrue(
                availableSlots.isEmpty(),
                "A booked consultation slot must not be available"
        );
    }

    @Test
    void shouldSelectLeastBookedAvailableAssociate() throws Exception {

        UUID emmaId = UUID.randomUUID();
        UUID lucasId = UUID.randomUUID();

        LocalDate date = LocalDate.of(2026, 11, 20);
        LocalDateTime requestedTime = date.atTime(11, 0);

        try (Connection connection = SQLiteConnection.getConnection()) {

            // Create two sales associates
            String insertAssociate = """
                INSERT INTO sales_associates (id, name)
                VALUES (?, ?)
                """;

            try (PreparedStatement statement =
                    connection.prepareStatement(insertAssociate)) {

                statement.setString(1, emmaId.toString());
                statement.setString(2, "Emma");
                statement.executeUpdate();

                statement.setString(1, lucasId.toString());
                statement.setString(2, "Lucas");
                statement.executeUpdate();
            }

            // Create slots for both associates
            String insertSlot = """
                INSERT INTO consultation_slots
                    (id, associate_id, start_time, duration_minutes)
                VALUES (?, ?, ?, 30)
                """;

            String insertBooking = """
                INSERT INTO scent_consultations
                    (id, customer_name, customer_email,
                     fragrance_preferences, slot_id, status)
                VALUES (?, ?, ?, ?, ?, 'CONFIRMED')
                """;

            try (PreparedStatement slotStatement =
                        connection.prepareStatement(insertSlot);
                 PreparedStatement bookingStatement =
                        connection.prepareStatement(insertBooking)) {

                UUID[] associates = {emmaId, lucasId};

                for (UUID associateId : associates) {

                    // Both associates are available at 11:00
                    slotStatement.setString(1, UUID.randomUUID().toString());
                    slotStatement.setString(2, associateId.toString());
                    slotStatement.setString(3, requestedTime.toString());
                    slotStatement.executeUpdate();

                    int bookingCount = associateId.equals(emmaId) ? 2 : 1;

                    for (int i = 0; i < bookingCount; i++) {

                        UUID slotId = UUID.randomUUID();

                        LocalDateTime bookedTime =
                                date.atTime(9, 0).plusMinutes(i * 30);

                        slotStatement.setString(
                                1, slotId.toString()
                        );
                        slotStatement.setString(2, associateId.toString());
                        slotStatement.setString(3, bookedTime.toString());
                        slotStatement.executeUpdate();

                        bookingStatement.setString(
                                1, UUID.randomUUID().toString()
                        );
                        bookingStatement.setString(2, "Test Customer");
                        bookingStatement.setString(
                                3, "customer@example.com"
                        );
                        bookingStatement.setString(4, "Floral");
                        bookingStatement.setString(5, slotId.toString());
                        bookingStatement.executeUpdate();
                    }
                }
            }
        }

        DatabaseAssociateScheduleRepository repository =
                new DatabaseAssociateScheduleRepository();

        SalesAssociate selectedAssociate =
                repository.findLeastBookedAvailableAssociate(requestedTime);

        assertNotNull(selectedAssociate);
        assertEquals(lucasId, selectedAssociate.getAssociateId());
    }
}
