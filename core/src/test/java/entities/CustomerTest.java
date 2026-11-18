package entities;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerTest {
    private static final UUID ID = new UUID(0, 1);

    @Test
    void createsCustomerWithInheritedIdentityAndContactDetails() {
        Customer customer = new Customer(ID, "Alex Martin", "alex@example.com");
        assertEquals(ID, customer.getId());
        assertEquals(customer.getId(), customer.getCustomerId());
        assertEquals("Alex Martin", customer.getName());
        assertEquals("alex@example.com", customer.getEmail());
    }

    @Test
    void rejectsMissingRequiredAttributes() {
        assertThrows(NullPointerException.class, () -> new Customer(null, "Alex", "alex@example.com"));
        assertThrows(NullPointerException.class, () -> new Customer(ID, null, "alex@example.com"));
        assertThrows(NullPointerException.class, () -> new Customer(ID, "Alex", null));
    }

    @Test
    void rejectsBlankNameAndEmail() {
        for (String blank : List.of("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException.class, () -> new Customer(ID, blank, "alex@example.com"));
            assertThrows(IllegalArgumentException.class, () -> new Customer(ID, "Alex", blank));
        }
    }
}
