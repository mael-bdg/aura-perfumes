package gui.stock;

import entities.ProductCategory;
import inventory.InventoryRow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockDashboardModelTest {
    private final StockDashboardModel model = new StockDashboardModel(5);

    @Test
    void summarizesLoadedInventoryUsingLongUnitTotalsAndKnownQuantities() {
        var summary = model.summarize(List.of(row("Aura", 5), row("Candle", Integer.MAX_VALUE),
            row("Sample", Integer.MAX_VALUE), row("Unknown", -1), row("Empty", 0)));
        assertEquals(5, summary.products());
        assertEquals(4_294_967_299L, summary.units());
        assertEquals(1, summary.lowStockProducts());
        assertEquals(1, summary.unavailableProducts());
        assertEquals(new StockDashboardModel.Summary(0, 0, 0, 0), model.summarize(List.of()));
    }

    @Test
    void usesInclusivePositiveLowStockBandAndDistinguishesZeroAndUnknown() {
        assertEquals("Unavailable", model.status(row("Missing", -1)));
        assertEquals("Out of stock", model.status(row("Empty", 0)));
        assertEquals("Low stock", model.status(row("One", 1)));
        assertEquals("Low stock", model.status(row("Five", 5)));
        assertEquals("In stock", model.status(row("Six", 6)));
        assertEquals("In stock", new StockDashboardModel(0).status(row("One", 1)));
        assertThrows(IllegalArgumentException.class, () -> new StockDashboardModel(-1));
    }

    @Test
    void filtersLocallyByCaseInsensitiveNameAndCategoryWithoutChangingInput() {
        InventoryRow perfume = row("Aura Rose", 10);
        InventoryRow candle = new InventoryRow(new UUID(0, 2), "Aura Candle", ProductCategory.SCENTED_CANDLE, 20);
        List<InventoryRow> rows = List.of(perfume, candle);
        assertEquals(List.of(perfume), model.filter(rows, " rOsE ", null));
        assertEquals(List.of(candle), model.filter(rows, "AURA", ProductCategory.SCENTED_CANDLE));
        assertTrue(model.filter(rows, "Rose", ProductCategory.SCENTED_CANDLE).isEmpty());
        assertEquals(rows, model.filter(rows, null, null));
        assertEquals(30, model.summarize(rows).units());
        assertThrows(UnsupportedOperationException.class, () -> model.filter(rows, "", null).clear());
    }

    @Test
    void parsesOnlyPositiveWholeQuantitiesWithoutCheckingStockAvailability() {
        assertEquals(Integer.MAX_VALUE, StockDashboardModel.parseQuantity("2147483647"));
        assertEquals(12, StockDashboardModel.parseQuantity(" 12 "));
        for (String invalid : List.of("", "-", "0", "-1", "1.2", "abc", "2147483648")) {
            assertThrows(IllegalArgumentException.class, () -> StockDashboardModel.parseQuantity(invalid));
        }
    }

    private static InventoryRow row(String name, int quantity) {
        return new InventoryRow(new UUID(0, name.hashCode()), name, ProductCategory.PERFUME, quantity);
    }
}
