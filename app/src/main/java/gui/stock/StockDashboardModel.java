package gui.stock;

import entities.ProductCategory;
import inventory.InventoryRow;
import java.util.List;
import java.util.Locale;

/** Pure presentation calculations over a detached inventory snapshot. */
public final class StockDashboardModel {
    private final int lowStockThreshold;

    /** Threshold is a presentation setting; zero disables the positive low-stock band. */
    public StockDashboardModel(int lowStockThreshold) {
        if (lowStockThreshold < 0) throw new IllegalArgumentException("Low-stock threshold must be nonnegative");
        this.lowStockThreshold = lowStockThreshold;
    }

    public int lowStockThreshold() { return lowStockThreshold; }

    /** Classifies stock without changing domain invariants or repository state. */
    public String status(InventoryRow row) {
        if (row.quantity() < 0) return "Unavailable";
        if (row.quantity() == 0) return "Out of stock";
        if (row.quantity() <= lowStockThreshold) return "Low stock";
        return "In stock";
    }

    /** Summaries cover all loaded products, independently of local filters. */
    public Summary summarize(List<InventoryRow> rows) {
        long units = rows.stream().filter(row -> row.quantity() >= 0).mapToLong(InventoryRow::quantity).sum();
        long low = rows.stream().filter(row -> row.quantity() > 0 && row.quantity() <= lowStockThreshold).count();
        long unavailable = rows.stream().filter(row -> row.quantity() < 0).count();
        return new Summary(rows.size(), units, low, unavailable);
    }

    /** Applies a case-insensitive name search and an optional category locally. */
    public List<InventoryRow> filter(List<InventoryRow> rows, String search, ProductCategory category) {
        String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return rows.stream().filter(row -> row.name().toLowerCase(Locale.ROOT).contains(term))
            .filter(row -> category == null || row.category() == category).toList();
    }

    /** Parses UI input only; availability and overflow validation remain in U1. */
    public static int parseQuantity(String text) {
        try {
            int quantity = Integer.parseInt(text.strip());
            if (quantity > 0) return quantity;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole quantity greater than zero", exception);
        }
        throw new IllegalArgumentException("Enter a whole quantity greater than zero");
    }

    public record Summary(int products, long units, long lowStockProducts, long unavailableProducts) { }
}
