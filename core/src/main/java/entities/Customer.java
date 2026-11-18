package entities;

import java.util.Objects;
import java.util.UUID;

/** Customer identity and required contact details for placing an order. */
public final class Customer extends Entity {
    private final String name;
    private final String email;

    /** Creates a customer with a non-null UUID and nonblank name and email. */
    public Customer(UUID id, String name, String email) {
        super(id);
        this.name = Objects.requireNonNull(name, "Customer name cannot be null");
        this.email = Objects.requireNonNull(email, "Customer email cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be blank");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("Customer email cannot be blank");
        }
    }

    /** Returns the inherited customer UUID. */
    public UUID getCustomerId() {
        return getId();
    }

    /** Returns the customer name. */
    public String getName() {
        return name;
    }

    /** Returns the customer email. */
    public String getEmail() {
        return email;
    }
}
