package repositories;

import database.SQLiteConnection;
import database.SQLiteDatabaseInitializer;
import entities.Order;
import entities.OrderStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBoutiqueOrderRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile =
                temporaryDirectory.resolve("boutique-order-test.db");

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
    void shouldFindOrderWithItemsFromSQLite() throws Exception {

        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        try (Connection connection = SQLiteConnection.getConnection()) {

            // Insert the order
            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO boutique_orders (
                            id, order_number, customer_id,
                            subtotal, discount, total,
                            redeemed_points, status,
                            fulfillment_type, version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {

                statement.setString(1, orderId.toString());
                statement.setString(2, "ORD-1001");
                statement.setString(3, customerId.toString());
                statement.setString(4, "100.00");
                statement.setString(5, "10.00");
                statement.setString(6, "90.00");
                statement.setInt(7, 0);
                statement.setString(8, "PENDING_CONFIRMATION");
                statement.setString(9, "IN_STORE_PICKUP");
                statement.setLong(10, 0);

                statement.executeUpdate();
            }

            // Insert one order item
            try (PreparedStatement statement =
                    connection.prepareStatement("""
                        INSERT INTO boutique_order_items (
                            id, order_id, product_id,
                            quantity, unit_price
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """)) {

                statement.setString(1, itemId.toString());
                statement.setString(2, orderId.toString());
                statement.setString(3, productId.toString());
                statement.setInt(4, 2);
                statement.setString(5, "50.00");

                statement.executeUpdate();
            }
        }

        DatabaseBoutiqueOrderRepository repository =
                new DatabaseBoutiqueOrderRepository();

        Order order = repository.findById(orderId);

        assertNotNull(order);
        assertEquals(orderId, order.getOrderId());
        assertEquals("ORD-1001", order.getOrderNumber());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, order.getStatus());

        assertEquals(1, order.getItems().size());
        assertEquals(productId, order.getItems().get(0).getProductId());
        assertEquals(2, order.getItems().get(0).getQuantity());

        assertEquals(0, order.getTotal().compareTo(
                new java.math.BigDecimal("90.00")
        ));
    }

    @Test
    void shouldUpdateOrderOnlyWhenVersionMatches() throws Exception {

        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // Create an order in SQLite
        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("""
                         INSERT INTO boutique_orders (
                             id, order_number, customer_id,
                             subtotal, discount, total,
                             redeemed_points, status,
                             fulfillment_type, version
                         )
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {

            statement.setString(1, orderId.toString());
            statement.setString(2, "ORD-2001");
            statement.setString(3, customerId.toString());
            statement.setString(4, "50.00");
            statement.setString(5, "0.00");
            statement.setString(6, "50.00");
            statement.setInt(7, 0);
            statement.setString(8, "PENDING_CONFIRMATION");
            statement.setString(9, "IN_STORE_PICKUP");
            statement.setLong(10, 0);

            statement.executeUpdate();
        }

        DatabaseBoutiqueOrderRepository repository =
                new DatabaseBoutiqueOrderRepository();

        // Simulate a status change to CONFIRMED
        Order updatedOrder = new Order(
                orderId,
                "ORD-2001",
                customerId,
                java.util.List.of(
                        new entities.OrderItem(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                1,
                                new java.math.BigDecimal("50.00")
                        )
                ),
                new java.math.BigDecimal("50.00"),
                java.math.BigDecimal.ZERO,
                new java.math.BigDecimal("50.00"),
                0,
                OrderStatus.CONFIRMED,
                entities.FulfillmentType.IN_STORE_PICKUP,
                null,
                null,
                null,
                null,
                0
        );

        // Correct version: update should succeed
        boolean firstUpdate =
                repository.updateIfVersionMatches(updatedOrder, 0);

        assertTrue(firstUpdate);

        // Old version: update should be rejected
        boolean secondUpdate =
                repository.updateIfVersionMatches(updatedOrder, 0);

        assertFalse(secondUpdate);

        // Verify the persisted state
        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("""
                         SELECT status, version
                         FROM boutique_orders
                         WHERE id = ?
                         """)) {

            statement.setString(1, orderId.toString());

            try (java.sql.ResultSet resultSet = statement.executeQuery()) {

                assertTrue(resultSet.next());
                assertEquals("CONFIRMED", resultSet.getString("status"));
                assertEquals(1, resultSet.getLong("version"));
            }
        }
    }
}
