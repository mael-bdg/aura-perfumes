package gateways;

import entities.Employee;
import java.util.UUID;

public interface BoutiqueEmployeeRepository {

    // Find an employee by their ID
    Employee findById(UUID employeeId);
}

