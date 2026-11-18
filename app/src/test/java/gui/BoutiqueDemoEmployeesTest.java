package gui;

import database.SQLiteConnection;
import database.SQLiteDatabaseInitializer;
import database.SQLiteTestDataSeeder;
import datatransferobjects.ProcessBoutiqueOrderRequest;
import entities.EmployeeRole;
import entities.OrderStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repositories.DatabaseBoutiqueEmployeeRepository;
import repositories.DatabaseBoutiqueOrderRepository;
import repositories.DatabaseOrderRepository;
import usecases.ProcessBoutiqueOrderInteractor;
import static org.junit.jupiter.api.Assertions.*;

class BoutiqueDemoEmployeesTest {
    @TempDir Path directory;
    private final BoutiqueDisplayDirectory display = new BoutiqueDisplayDirectory();
    private final DatabaseBoutiqueEmployeeRepository employees = new DatabaseBoutiqueEmployeeRepository();
    private static final List<String> CODES = List.of("EMP-3333", "EMP-8888", "EMP-9999");
    private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @BeforeEach void initialize() throws Exception {
        SQLiteConnection.setDatabaseUrl("jdbc:sqlite:" + directory.resolve("employees.db"));
        SQLiteDatabaseInitializer.initialize();
        SQLiteTestDataSeeder.seed();
    }

    @AfterEach void close() {
        SQLiteConnection.resetDatabaseUrl();
    }

    @Test void shortCodesAndUuidsResolveToSeededEmployees() {
        var names = List.of("Emma", "Lucas", "Sofia");
        var roles = List.of(EmployeeRole.SALES_ASSOCIATE, EmployeeRole.STORE_MANAGER,
            EmployeeRole.OPERATIONS_MANAGER);
        for (int i = 0; i < CODES.size(); i++) {
            String code = CODES.get(i);
            UUID id = display.employeeId(code);
            var employee = employees.findById(id);
            assertNotNull(employee, code);
            assertEquals(id, employee.getEmployeeId());
            assertEquals(names.get(i), employee.getName());
            assertEquals(roles.get(i), employee.getRole());
            assertEquals(employee.getName(), display.employeeName(id));
            assertEquals(code, display.employeeCode(id));
            assertEquals(id, display.employeeId(id.toString()));
            assertEquals(id, display.employeeId(" " + code.toLowerCase(java.util.Locale.ROOT) + " "));
        }
    }

    @Test void repeatedSeedingPreservesExistingEmployeesWithoutDuplicates() throws Exception {
        UUID customId = UUID.randomUUID();
        try (var connection = SQLiteConnection.getConnection()) {
            try (var insert = connection.prepareStatement(
                    "INSERT INTO boutique_employees(id, name, role) VALUES (?, 'Existing employee', 'SALES_ASSOCIATE')")) {
                insert.setString(1, customId.toString());
                insert.executeUpdate();
            }
            try (var update = connection.prepareStatement("UPDATE boutique_employees SET name = 'Existing Lucas' WHERE id = ?")) {
                update.setString(1, display.employeeId("EMP-8888").toString());
                update.executeUpdate();
            }
        }
        SQLiteTestDataSeeder.seed();
        SQLiteTestDataSeeder.seed();
        assertEquals("Existing employee", employees.findById(customId).getName());
        assertEquals("Existing Lucas", employees.findById(display.employeeId("EMP-8888")).getName());
        try (var connection = SQLiteConnection.getConnection();
             var query = connection.createStatement();
             var count = query.executeQuery("SELECT COUNT(*) FROM boutique_employees")) {
            assertTrue(count.next());
            assertEquals(4, count.getInt(1));
        }
    }

    @Test void seededEmployeesProcessOrdersWithExistingRolePermissions() {
        for (String code : CODES) {
            var orders = new DatabaseOrderRepository();
            orders.save(new DatabaseBoutiqueOrderRepository().findById(ORDER_ID));
            var interactor = new ProcessBoutiqueOrderInteractor(orders, employees);
            UUID employeeId = display.employeeId(code);
            for (OrderStatus status : List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION,
                    OrderStatus.READY_FOR_PICKUP)) {
                var response = interactor.handle(new ProcessBoutiqueOrderRequest(
                    ORDER_ID, employeeId, status, null, "1234"));
                assertTrue(response.isSuccess(), code + ": " + response.getMessage());
                assertEquals(status, orders.findById(ORDER_ID).getStatus());
            }
            // Use a fresh pending order so cancellation tests authorization on a valid transition.
            orders = new DatabaseOrderRepository();
            orders.save(new DatabaseBoutiqueOrderRepository().findById(ORDER_ID));
            interactor = new ProcessBoutiqueOrderInteractor(orders, employees);
            var response = interactor.handle(new ProcessBoutiqueOrderRequest(
                ORDER_ID, employeeId, OrderStatus.CANCELLED, "Customer request", null));
            boolean manager = !code.equals("EMP-3333");
            assertEquals(manager, response.isSuccess(), code + ": " + response.getMessage());
            assertEquals(manager ? OrderStatus.CANCELLED : OrderStatus.PENDING_CONFIRMATION,
                orders.findById(ORDER_ID).getStatus());
            if (!manager) {
                assertEquals("Employee is not authorized for this operation.", response.getMessage());
            }
        }
    }
}
