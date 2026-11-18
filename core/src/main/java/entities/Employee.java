package entities;

import java.util.Objects;
import java.util.UUID;

public class Employee extends Entity {

    private final String name;
    private final EmployeeRole role;

    public Employee(UUID id, String name, EmployeeRole role) {
        super(id);

        this.name = Objects.requireNonNull(
            name, "Employee name cannot be null"
        );

        this.role = Objects.requireNonNull(
            role, "Employee role cannot be null"
        );
    }

    public UUID getEmployeeId() {
        return getId();
    }

    public String getName() {
        return name;
    }

    public EmployeeRole getRole() {
        return role;
    }
}

