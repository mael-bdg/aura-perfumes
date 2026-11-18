package repositories;

import database.SQLiteConnection;
import database.SQLiteDatabaseInitializer;
import entities.Employee;
import entities.EmployeeRole;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBoutiqueEmployeeRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception {

        Path databaseFile =
                temporaryDirectory.resolve("employee-test.db");

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
    void shouldFindEmployeeById() throws Exception {

        UUID employeeId = UUID.randomUUID();

        // Insert an employee into SQLite
        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("""
                         INSERT INTO boutique_employees
                             (id, name, role)
                         VALUES (?, ?, ?)
                         """)) {

            statement.setString(1, employeeId.toString());
            statement.setString(2, "Emma");
            statement.setString(3, "SALES_ASSOCIATE");

            statement.executeUpdate();
        }

        DatabaseBoutiqueEmployeeRepository repository =
                new DatabaseBoutiqueEmployeeRepository();

        Employee employee = repository.findById(employeeId);

        assertNotNull(employee);
        assertEquals(employeeId, employee.getEmployeeId());
        assertEquals("Emma", employee.getName());
        assertEquals(EmployeeRole.SALES_ASSOCIATE, employee.getRole());
    }
}
