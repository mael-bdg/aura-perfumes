package repositories;

import database.SQLiteConnection;
import entities.FulfillmentType;
import entities.Order;
import entities.OrderItem;
import entities.OrderStatus;
import gateways.BoutiqueOrderRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseBoutiqueOrderRepository
        implements BoutiqueOrderRepository {

    @Override
    public Order findById(UUID orderId) {

        if (orderId == null) {
            return null;
        }

        String sql = """
            SELECT *
            FROM boutique_orders
            WHERE id = ?
            """;

        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, orderId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                List<OrderItem> items =
                        findOrderItems(connection, orderId);

                return new Order(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("order_number"),
                        UUID.fromString(resultSet.getString("customer_id")),
                        items,
                        new BigDecimal(resultSet.getString("subtotal")),
                        new BigDecimal(resultSet.getString("discount")),
                        new BigDecimal(resultSet.getString("total")),
                        resultSet.getInt("redeemed_points"),
                        OrderStatus.valueOf(resultSet.getString("status")),
                        FulfillmentType.valueOf(
                                resultSet.getString("fulfillment_type")
                        ),
                        resultSet.getString("packaging_instructions"),
                        resultSet.getString("delivery_notes"),
                        resultSet.getString("cancellation_reason"),
                        resultSet.getString("pickup_pin"),
                        resultSet.getLong("version")
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to retrieve boutique order", e
            );
        }
    }

    private List<OrderItem> findOrderItems(
            Connection connection,
            UUID orderId) throws SQLException {

        String sql = """
            SELECT id, product_id, quantity, unit_price
            FROM boutique_order_items
            WHERE order_id = ?
            """;

        List<OrderItem> items = new ArrayList<>();

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setString(1, orderId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {

                    items.add(new OrderItem(
                            UUID.fromString(resultSet.getString("id")),
                            UUID.fromString(resultSet.getString("product_id")),
                            resultSet.getInt("quantity"),
                            new BigDecimal(
                                    resultSet.getString("unit_price")
                            )
                    ));
                }
            }
        }

        return items;
    }

    @Override
    public boolean updateIfVersionMatches(
            Order order,
            long expectedVersion) {

        String sql = """
            UPDATE boutique_orders
            SET status = ?,
                cancellation_reason = ?,
                pickup_pin = ?,
                version = version + 1
            WHERE id = ?
              AND version = ?
            """;

        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, order.getStatus().name());
            statement.setString(2, order.getCancellationReason());
            statement.setString(3, order.getPickupPin());
            statement.setString(4, order.getOrderId().toString());
            statement.setLong(5, expectedVersion);

            return statement.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to update boutique order", e
            );
        }
    }
}

