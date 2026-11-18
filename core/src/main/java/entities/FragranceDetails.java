package entities;

import java.util.List;
import java.util.Objects;

/** Immutable fragrance profile whose note order is significant for equality. */
public final class FragranceDetails {
    private final List<String> topNotes;
    private final List<String> heartNotes;
    private final List<String> baseNotes;
    private final String concentration;

    /**
     * Creates a fragrance profile. Empty note lists are allowed.
     *
     * @param topNotes non-null list of non-null, non-blank top notes
     * @param heartNotes non-null list of non-null, non-blank heart notes
     * @param baseNotes non-null list of non-null, non-blank base notes
     * @param concentration non-null, non-blank concentration
     */
    public FragranceDetails(List<String> topNotes, List<String> heartNotes,
                            List<String> baseNotes, String concentration) {
        this.topNotes = copyNotes(topNotes);
        this.heartNotes = copyNotes(heartNotes);
        this.baseNotes = copyNotes(baseNotes);
        this.concentration = Objects.requireNonNull(concentration, "Concentration cannot be null");
        if (concentration.isBlank()) {
            throw new IllegalArgumentException("Concentration cannot be blank");
        }
    }

    private static List<String> copyNotes(List<String> notes) {
        List<String> copy = List.copyOf(notes);
        for (String note : copy) {
            if (note.isBlank()) {
                throw new IllegalArgumentException("Fragrance notes cannot be blank");
            }
        }
        return copy;
    }

    /** Returns the immutable top notes. */
    public List<String> getTopNotes() {
        return topNotes;
    }

    /** Returns the immutable heart notes. */
    public List<String> getHeartNotes() {
        return heartNotes;
    }

    /** Returns the immutable base notes. */
    public List<String> getBaseNotes() {
        return baseNotes;
    }

    /** Returns the concentration. */
    public String getConcentration() {
        return concentration;
    }

    /** Compares all fragrance attributes by value. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FragranceDetails details)) {
            return false;
        }
        return topNotes.equals(details.topNotes)
            && heartNotes.equals(details.heartNotes)
            && baseNotes.equals(details.baseNotes)
            && concentration.equals(details.concentration);
    }

    /** Returns a hash derived from all fragrance attributes. */
    @Override
    public int hashCode() {
        return Objects.hash(topNotes, heartNotes, baseNotes, concentration);
    }
}
