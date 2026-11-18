package entities;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductTest {
    @Test
    void createsProductWithInheritedIdentityAndFragrance() {
        UUID id = UUID.randomUUID();
        FragranceDetails details = new FragranceDetails(
            List.of("Bergamot"), List.of("Rose"), List.of("Musk"), "Eau de parfum");
        Product product = new Product(id, "Aura", ProductCategory.PERFUME,
            new BigDecimal("79.90"), details);

        Entity entity = product;
        assertEquals(id, entity.getId());
        assertEquals(entity.getId(), product.getProductId());
        assertEquals("Aura", product.getName());
        assertEquals(ProductCategory.PERFUME, product.getCategory());
        assertEquals(new BigDecimal("79.90"), product.getUnitPrice());
        assertSame(details, product.getFragranceDetails());
    }

    @Test
    void allowsZeroPriceAndAbsentFragrance() {
        Product product = new Product(UUID.randomUUID(), "Sample", ProductCategory.SAMPLE_SET,
            BigDecimal.ZERO, null);
        assertEquals(BigDecimal.ZERO, product.getUnitPrice());
        assertNull(product.getFragranceDetails());
    }

    @Test
    void rejectsInvalidAttributes() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class,
            () -> new Product(null, "Aura", ProductCategory.PERFUME, BigDecimal.ONE, null));
        assertThrows(NullPointerException.class,
            () -> new Product(id, null, ProductCategory.PERFUME, BigDecimal.ONE, null));
        for (String name : List.of("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException.class,
                () -> new Product(id, name, ProductCategory.PERFUME, BigDecimal.ONE, null));
        }
        assertThrows(NullPointerException.class,
            () -> new Product(id, "Aura", null, BigDecimal.ONE, null));
        assertThrows(NullPointerException.class,
            () -> new Product(id, "Aura", ProductCategory.PERFUME, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new Product(id, "Aura", ProductCategory.PERFUME, new BigDecimal("-0.01"), null));
    }

    @Test
    void hasExactlyTheSpecifiedCategories() {
        assertArrayEquals(new String[] {"PERFUME", "SCENTED_CANDLE", "REED_DIFFUSER",
            "ROOM_SPRAY", "SCENTED_BODY_LOTION", "SHOWER_GEL", "TRAVEL_ATOMIZER",
            "SAMPLE_SET", "GIFT_BOX"},
            java.util.Arrays.stream(ProductCategory.values()).map(Enum::name).toArray(String[]::new));
    }
}
