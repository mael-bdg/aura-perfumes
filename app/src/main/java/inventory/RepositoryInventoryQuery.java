package inventory;

import entities.Product;
import entities.Stock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import repositories.DatabaseProductRepository;
import usecases.StockRepository;

/** Reads the existing session stores without exposing repositories to FX controllers. */
public final class RepositoryInventoryQuery implements InventoryQuery {
    private final DatabaseProductRepository products;
    private final StockRepository stocks;

    public RepositoryInventoryQuery(DatabaseProductRepository products, StockRepository stocks) {
        this.products = Objects.requireNonNull(products);
        this.stocks = Objects.requireNonNull(stocks);
    }

    /** Uses the stock monitor shared by U1/U2 to obtain one consistent stock snapshot. */
    @Override
    public List<InventoryRow> load() {
        synchronized (stocks) {
            List<InventoryRow> rows = new ArrayList<>();
            for (Product product : products.findAll()) {
                Stock stock = stocks.findByProductId(product.getProductId());
                rows.add(new InventoryRow(product.getProductId(), product.getName(), product.getCategory(),
                    stock == null ? -1 : stock.getAvailableQuantity()));
            }
            rows.sort(Comparator.comparing(InventoryRow::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(InventoryRow::productId));
            return List.copyOf(rows);
        }
    }
}
