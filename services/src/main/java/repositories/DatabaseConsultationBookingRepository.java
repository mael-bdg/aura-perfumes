package repositories;

import database.SQLiteConnection;
import entities.ConsultationSlot;
import entities.ScentConsultation;
import gateways.ConsultationBookingRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

public class DatabaseConsultationBookingRepository
        implements ConsultationBookingRepository {

    @Override
    public boolean reserveSlotAndSave(ScentConsultation booking) {

        if (booking == null || booking.getSlot() == null) {
            return false;
        }

        String sql = """
            INSERT INTO scent_consultations (
                id,
                customer_name,
                customer_email,
                fragrance_preferences,
                slot_id,
                status
            )
            SELECT ?, ?, ?, ?, s.id, 'CONFIRMED'
            FROM consultation_slots s
            WHERE s.id = ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM scent_consultations c
                  WHERE c.slot_id = s.id
              )
            """;

        try (Connection connection = SQLiteConnection.getConnection()) {

            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                    connection.prepareStatement(sql)) {

                statement.setString(
                    1, booking.getBookingId().toString()
                );
                statement.setString(
                    2, booking.getCustomerName()
                );
                statement.setString(
                    3, booking.getCustomerEmail()
                );
                statement.setString(
                    4, booking.getFragrancePreferences()
                );
                statement.setString(
                    5, booking.getSlot().getId().toString()
                );

                int insertedRows = statement.executeUpdate();

                if (insertedRows != 1) {
                    connection.rollback();
                    return false;
                }

                connection.commit();
                return true;

            } catch (SQLException exception) {
                connection.rollback();
                return false;
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to save consultation booking",
                exception
            );
        }
    }

    @Override
    public ScentConsultation findById(UUID bookingId) {

        if (bookingId == null) {
            return null;
        }

        String sql = """
            SELECT
                c.id,
                c.customer_name,
                c.customer_email,
                c.fragrance_preferences,
                s.id AS slot_id,
                s.associate_id,
                s.start_time
            FROM scent_consultations c
            JOIN consultation_slots s
                ON s.id = c.slot_id
            WHERE c.id = ?
            """;

        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                    connection.prepareStatement(sql)) {

            statement.setString(1, bookingId.toString());

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    return null;
                }

                ConsultationSlot slot = new ConsultationSlot(
                    UUID.fromString(result.getString("slot_id")),
                    UUID.fromString(result.getString("associate_id")),
                    LocalDateTime.parse(result.getString("start_time"))
                );

                return new ScentConsultation(
                    UUID.fromString(result.getString("id")),
                    result.getString("customer_name"),
                    result.getString("customer_email"),
                    result.getString("fragrance_preferences"),
                    slot
                );
            }

        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to retrieve consultation booking",
                exception
            );
        }
    }
}

