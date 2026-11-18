
package entities;

import java.util.UUID;
import java.util.Objects;

public class SalesAssociate extends Entity {

    private final String name;

    public SalesAssociate(UUID id, String name) {
        super(id);
        this.name = Objects.requireNonNull(name, "Name cannot be null");
    }

    public UUID getAssociateId() {
        return getId();
    }

    public String getName() {
        return name;
    }
}

