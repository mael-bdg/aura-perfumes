package repositories;

import database.SQLiteConnection;
import entities.Employee;
import entities.EmployeeRole;
import gateways.BoutiqueEmployeeRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseBoutiqueEmployeeRepository
        implements BoutiqueEmployeeRepository {

    @Override
    public Employee findById(UUID employeeId) {

        if (employeeId == null) {
            return null;
        }

        String sql = """
            SELECT id, name, role
            FROM boutique_employees
            WHERE id = ?
            """;

        try (Connection connection = SQLiteConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, employeeId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return new Employee(
                        UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("name"),
                        EmployeeRole.valueOf(
                                resultSet.getString("role")
                        )
                );
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to retrieve boutique employee", e
            );
        }
    }
}

