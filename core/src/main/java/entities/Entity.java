
package entities;

import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for all persistent business
 * entities in the Aura Perfumes application.
 *
 * Every entity has a unique UUID identifier.
 */
public abstract class Entity {

    private final UUID id;

    /**
     * Creates an entity with the specified UUID.
     *
     * @param id unique identifier of the entity
     */
    protected Entity(UUID id) {
        this.id = Objects.requireNonNull(
            id,
            "Entity ID cannot be null"
        );
    }

    /**
     * Returns the unique identifier of the entity.
     *
     * @return entity UUID
     */
    public UUID getId() {
        return id;
    }
}

