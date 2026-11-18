package database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SQLiteDatabaseInitializer {

    private SQLiteDatabaseInitializer() {
        // Utility class
    }

    public static void initialize() throws SQLException {

        try (Connection connection = SQLiteConnection.getConnection();
             Statement statement = connection.createStatement()) {

            // Store sales associates
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sales_associates (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                )
                """);

            // Store consultation slots
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS consultation_slots (
                    id TEXT PRIMARY KEY,
                    associate_id TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    duration_minutes INTEGER NOT NULL DEFAULT 30
                        CHECK (duration_minutes = 30),
                    FOREIGN KEY (associate_id)
                        REFERENCES sales_associates(id),
                    UNIQUE (associate_id, start_time)
                )
                """);

            // Store confirmed consultation bookings
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS scent_consultations (
                    id TEXT PRIMARY KEY,
                    customer_name TEXT NOT NULL,
                    customer_email TEXT NOT NULL,
                    fragrance_preferences TEXT,
                    slot_id TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL DEFAULT 'CONFIRMED'
                        CHECK (status = 'CONFIRMED'),
                    FOREIGN KEY (slot_id)
                        REFERENCES consultation_slots(id)
                )
                """);

            // UC4 - Store employees
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS boutique_employees (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    role TEXT NOT NULL
                        CHECK (role IN (
                            'SALES_ASSOCIATE',
                            'STORE_MANAGER',
                            'OPERATIONS_MANAGER'
                        ))
                )
                """);

            // UC4 - Store boutique orders
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS boutique_orders (
                    id TEXT PRIMARY KEY,
                    order_number TEXT NOT NULL UNIQUE,
                    customer_id TEXT NOT NULL,
                    subtotal TEXT NOT NULL,
                    discount TEXT NOT NULL,
                    total TEXT NOT NULL,
                    redeemed_points INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL,
                    fulfillment_type TEXT NOT NULL,
                    packaging_instructions TEXT,
                    delivery_notes TEXT,
                    cancellation_reason TEXT,
                    pickup_pin TEXT,
                    version INTEGER NOT NULL DEFAULT 0
                )
                """);

            // UC4 - Store order items
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS boutique_order_items (
                    id TEXT PRIMARY KEY,
                    order_id TEXT NOT NULL,
                    product_id TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    unit_price TEXT NOT NULL,
                    FOREIGN KEY (order_id)
                        REFERENCES boutique_orders(id)
                )
                """);
        }
    }
}

