package repositories;

import database.SQLiteConnection;
import entities.ConsultationSlot;
import entities.SalesAssociate;
import gateways.AssociateScheduleRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseAssociateScheduleRepository
        implements AssociateScheduleRepository {

    @Override
    public List<ConsultationSlot> findAvailableSlots(
            LocalDate date, UUID associateId) {

        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        String sql = """
            SELECT s.id, s.associate_id, s.start_time
            FROM consultation_slots s
            LEFT JOIN scent_consultations c
                ON c.slot_id = s.id
            WHERE date(s.start_time) = ?
              AND c.id IS NULL
              AND (? IS NULL OR s.associate_id = ?)
            ORDER BY s.start_time, s.associate_id
            """;

        List<ConsultationSlot> slots = new ArrayList<>();

        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, date.toString());

            String id = associateId == null
                    ? null : associateId.toString();

            statement.setString(2, id);
            statement.setString(3, id);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    slots.add(new ConsultationSlot(
                        UUID.fromString(result.getString("id")),
                        UUID.fromString(result.getString("associate_id")),
                        LocalDateTime.parse(result.getString("start_time"))
                    ));
                }
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to retrieve consultation slots",
                exception
            );
        }

        return slots;
    }

    @Override
    public SalesAssociate findLeastBookedAvailableAssociate(
            LocalDateTime startTime) {

        if (startTime == null) {
            throw new IllegalArgumentException(
                "Start time cannot be null"
            );
        }

        String sql = """
            SELECT a.id, a.name, COUNT(c.id) AS booking_count
            FROM sales_associates a
            JOIN consultation_slots s
                ON s.associate_id = a.id
            LEFT JOIN scent_consultations occupied
                ON occupied.slot_id = s.id
            LEFT JOIN consultation_slots daily_slots
                ON daily_slots.associate_id = a.id
                AND date(daily_slots.start_time) = date(?)
            LEFT JOIN scent_consultations c
                ON c.slot_id = daily_slots.id
            WHERE s.start_time = ?
              AND occupied.id IS NULL
            GROUP BY a.id, a.name
            ORDER BY booking_count ASC, a.id ASC
            LIMIT 1
            """;

        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, startTime.toString());
            statement.setString(2, startTime.toString());

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return new SalesAssociate(
                        UUID.fromString(result.getString("id")),
                        result.getString("name")
                    );
                }
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to find an available associate",
                exception
            );
        }

        return null;
    }
}

