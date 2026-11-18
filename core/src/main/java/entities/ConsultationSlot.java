
package entities;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class ConsultationSlot extends Entity {

    private final UUID associateId;
    private final LocalDateTime startTime;
    private final int durationMinutes;

    public ConsultationSlot(UUID id, UUID associateId,
                            LocalDateTime startTime) {
        super(id);

        this.associateId = Objects.requireNonNull(
            associateId, "Associate ID cannot be null"
        );

        this.startTime = Objects.requireNonNull(
            startTime, "Start time cannot be null"
        );

        this.durationMinutes = 30;
    }

    public UUID getAssociateId() {
        return associateId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
}
