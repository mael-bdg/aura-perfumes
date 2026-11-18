package entities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FragranceDetailsTest {
    @Test
    void defensivelyCopiesEveryListAndExposesUnmodifiableNotes() {
        List<String> top = new ArrayList<>(List.of("Bergamot"));
        List<String> heart = new ArrayList<>(List.of("Rose"));
        List<String> base = new ArrayList<>(List.of("Musk"));
        FragranceDetails details = new FragranceDetails(top, heart, base, "Parfum");
        top.clear();
        heart.clear();
        base.clear();

        assertEquals(List.of("Bergamot"), details.getTopNotes());
        assertEquals(List.of("Rose"), details.getHeartNotes());
        assertEquals(List.of("Musk"), details.getBaseNotes());
        assertEquals("Parfum", details.getConcentration());
        for (List<String> notes : List.of(details.getTopNotes(), details.getHeartNotes(), details.getBaseNotes())) {
            assertThrows(UnsupportedOperationException.class, () -> notes.add("Vanilla"));
            assertThrows(UnsupportedOperationException.class, () -> notes.set(0, "Vanilla"));
        }
    }

    @Test
    void comparesEveryAttributeByValue() {
        FragranceDetails details = new FragranceDetails(List.of("A"), List.of("B"), List.of("C"), "Parfum");
        FragranceDetails equal = new FragranceDetails(List.of("A"), List.of("B"), List.of("C"), "Parfum");
        assertEquals(details, details);
        assertEquals(details, equal);
        assertEquals(equal, details);
        assertEquals(details.hashCode(), equal.hashCode());
        assertNotEquals(details, null);
        assertNotEquals(details, "Parfum");
        assertNotEquals(details, new FragranceDetails(List.of("X"), List.of("B"), List.of("C"), "Parfum"));
        assertNotEquals(details, new FragranceDetails(List.of("A"), List.of("X"), List.of("C"), "Parfum"));
        assertNotEquals(details, new FragranceDetails(List.of("A"), List.of("B"), List.of("X"), "Parfum"));
        assertNotEquals(details, new FragranceDetails(List.of("A"), List.of("B"), List.of("C"), "EDT"));
    }

    @Test
    void allowsEmptyNoteLists() {
        FragranceDetails details = new FragranceDetails(List.of(), List.of(), List.of(), "Parfum");
        assertTrue(details.getTopNotes().isEmpty());
        assertTrue(details.getHeartNotes().isEmpty());
        assertTrue(details.getBaseNotes().isEmpty());
    }

    @Test
    void rejectsNullListsAndNullOrBlankNotesInEveryPosition() {
        List<String> valid = List.of("Rose");
        for (List<String> invalid : Arrays.asList(null, Arrays.asList((String) null), List.of(""), List.of(" \t"))) {
            Class<? extends RuntimeException> exception = invalid == null || invalid.stream().anyMatch(java.util.Objects::isNull)
                ? NullPointerException.class : IllegalArgumentException.class;
            assertThrows(exception, () -> new FragranceDetails(invalid, valid, valid, "Parfum"));
            assertThrows(exception, () -> new FragranceDetails(valid, invalid, valid, "Parfum"));
            assertThrows(exception, () -> new FragranceDetails(valid, valid, invalid, "Parfum"));
        }
    }

    @Test
    void rejectsNullOrBlankConcentration() {
        assertThrows(NullPointerException.class,
            () -> new FragranceDetails(List.of(), List.of(), List.of(), null));
        for (String concentration : List.of("", " \t\n")) {
            assertThrows(IllegalArgumentException.class,
                () -> new FragranceDetails(List.of(), List.of(), List.of(), concentration));
        }
    }
}
