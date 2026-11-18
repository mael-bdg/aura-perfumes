package gui;

import java.util.Map;
import java.util.UUID;

/**
 * Converts internal UUIDs into readable demo references.
 * Does not modify business entities or repositories.
 */
public final class BoutiqueDisplayDirectory {

    private static final Map<String, UUID> EMPLOYEES = Map.of(
        "EMP-3333",
        UUID.fromString("33333333-3333-3333-3333-333333333333"),
        "EMP-8888",
        UUID.fromString("88888888-8888-8888-8888-888888888888"),
        "EMP-9999",
        UUID.fromString("99999999-9999-9999-9999-999999999999")
    );

    private static final Map<UUID, String> ASSOCIATES = Map.of(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        "Emma Laurent",

        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        "Lucas Martin"
    );

    public UUID employeeId(String reference) {
        if (reference == null) {
            return null;
        }

        UUID id = EMPLOYEES.get(reference.trim().toUpperCase());

        if (id != null) {
            return id;
        }

        try {
            return UUID.fromString(reference.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String employeeName(UUID id) {
        if (id == null) {
            return "Unknown employee";
        }

        if (id.equals(EMPLOYEES.get("EMP-3333"))) {
            return "Emma";
        }
        if (id.equals(EMPLOYEES.get("EMP-8888"))) {
            return "Lucas";
        }
        if (id.equals(EMPLOYEES.get("EMP-9999"))) {
            return "Sofia";
        }

        return "Employee " + id.toString().substring(0, 8);
    }

    public String employeeCode(UUID id) {
        if (id == null) {
            return "";
        }

        for (var entry : EMPLOYEES.entrySet()) {
            if (entry.getValue().equals(id)) {
                return entry.getKey();
            }
        }

        return id.toString();
    }

    public String associateName(UUID id) {
        if (id == null) {
            return "Any available associate";
        }

        return ASSOCIATES.getOrDefault(
            id,
            "Associate " + id.toString().substring(0, 8)
        );
    }
}

