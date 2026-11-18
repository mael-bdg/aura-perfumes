package database;

import entities.EmployeeRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class SQLiteTestDataSeeder {

    private SQLiteTestDataSeeder() {
        // Utility class
    }

    public static void seed() throws SQLException {

        LocalDate date = LocalDate.now().plusDays(1);

        try (Connection connection = SQLiteConnection.getConnection()) {

            connection.setAutoCommit(false);

            try {
                String[][] associates = {
                    {"11111111-1111-1111-1111-111111111111", "Emma"},
                    {"22222222-2222-2222-2222-222222222222", "Lucas"}
                };

                String insertAssociate = """
                    INSERT OR IGNORE INTO sales_associates (id, name)
                    VALUES (?, ?)
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertAssociate)) {

                    for (String[] associate : associates) {
                        statement.setString(1, associate[0]);
                        statement.setString(2, associate[1]);
                        statement.executeUpdate();
                    }
                }

                String insertSlot = """
                    INSERT OR IGNORE INTO consultation_slots
                        (id, associate_id, start_time, duration_minutes)
                    VALUES (?, ?, ?, 30)
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertSlot)) {

                    for (String[] associate : associates) {

                        for (int hour = 10; hour <= 11; hour++) {
                            for (int minute = 0; minute <= 30; minute += 30) {

                                LocalDateTime startTime =
                                        date.atTime(hour, minute);

                                String slotKey =
                                        associate[0] + "|" + startTime;

                                UUID slotId = UUID.nameUUIDFromBytes(
                                        slotKey.getBytes(
                                            java.nio.charset.StandardCharsets.UTF_8
                                        )
                                );

                                statement.setString(1, slotId.toString());
                                statement.setString(2, associate[0]);
                                statement.setString(3, startTime.toString());
                                statement.executeUpdate();
                            }
                        }
                    }
                }

                String insertEmployee = """
                    INSERT OR IGNORE INTO boutique_employees
                        (id, name, role)
                    VALUES (?, ?, ?)
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertEmployee)) {

                    String[][] employees = {
                        {"33333333-3333-3333-3333-333333333333", "Emma", EmployeeRole.SALES_ASSOCIATE.name()},
                        {"88888888-8888-8888-8888-888888888888", "Lucas", EmployeeRole.STORE_MANAGER.name()},
                        {"99999999-9999-9999-9999-999999999999", "Sofia", EmployeeRole.OPERATIONS_MANAGER.name()}
                    };
                    for (String[] employee : employees) {
                        statement.setString(1, employee[0]);
                        statement.setString(2, employee[1]);
                        statement.setString(3, employee[2]);
                        statement.executeUpdate();
                    }
                }

                String insertOrder = """
                    INSERT OR IGNORE INTO boutique_orders
                        (id, order_number, customer_id,
                         subtotal, discount, total, redeemed_points,
                         status, fulfillment_type, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertOrder)) {

                    statement.setString(1,
                            "44444444-4444-4444-4444-444444444444");
                    statement.setString(2, "ORD-UC4-001");
                    statement.setString(3,
                            "55555555-5555-5555-5555-555555555555");
                    statement.setString(4, "50.00");
                    statement.setString(5, "0.00");
                    statement.setString(6, "50.00");
                    statement.setInt(7, 0);
                    statement.setString(8, "PENDING_CONFIRMATION");
                    statement.setString(9, "IN_STORE_PICKUP");
                    statement.setLong(10, 0);

                    statement.executeUpdate();
                }

                String insertOrderItem = """
                    INSERT OR IGNORE INTO boutique_order_items
                        (id, order_id, product_id, quantity, unit_price)
                    VALUES (?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertOrderItem)) {

                    statement.setString(1,
                            "66666666-6666-6666-6666-666666666666");
                    statement.setString(2,
                            "44444444-4444-4444-4444-444444444444");
                    statement.setString(3,
                            "77777777-7777-7777-7777-777777777777");
                    statement.setInt(4, 1);
                    statement.setString(5, "50.00");

                    statement.executeUpdate();
                }

                connection.commit();

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }

        System.out.println("Test associates and slots initialized.");
        System.out.println("Consultation date: " + date);
    }
}

