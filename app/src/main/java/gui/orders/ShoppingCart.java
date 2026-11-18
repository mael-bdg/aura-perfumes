package gui.orders;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Session-local selections and estimated totals. Cart edits never reserve inventory. */
public final class ShoppingCart {
    private final Map<UUID, Line> lines = new LinkedHashMap<>();

    public void add(OrderDataQuery.Product product, int quantity) {
        requireQuantity(quantity);
        Line previous = lines.get(product.id());
        int combined;
        try {
            combined = Math.addExact(previous == null ? 0 : previous.quantity(), quantity);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Combined quantity exceeds the integer range", overflow);
        }
        lines.put(product.id(), new Line(product, combined));
    }

    public void changeQuantity(UUID productId, int quantity) {
        requireQuantity(quantity);
        Line previous = lines.get(productId);
        if (previous == null) throw new IllegalArgumentException("Cart item is unavailable");
        lines.put(productId, new Line(previous.product(), quantity));
    }

    public void remove(UUID productId) { lines.remove(productId); }
    public void clear() { lines.clear(); }
    public List<Line> lines() { return List.copyOf(lines.values()); }
    public BigDecimal subtotal() {
        return lines.values().stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Updates display prices from a fresh catalog while retaining unavailable cart items. */
    public void updateCatalog(List<OrderDataQuery.Product> products) {
        for (OrderDataQuery.Product product : products) {
            Line line = lines.get(product.id());
            if (line != null) lines.put(product.id(), new Line(product, line.quantity()));
        }
    }

    private static void requireQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be a positive whole number");
    }

    public record Line(OrderDataQuery.Product product, int quantity) {
        public BigDecimal total() { return product.unitPrice().multiply(BigDecimal.valueOf(quantity)); }
    }
}
