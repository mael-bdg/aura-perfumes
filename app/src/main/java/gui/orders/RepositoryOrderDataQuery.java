package gui.orders;

import entities.LoyaltyAccount;
import entities.Stock;
import gateways.LoyaltyRepository;
import java.util.Comparator;
import java.util.List;
import repositories.DatabaseProductRepository;
import usecases.StockRepository;

/** Detached catalog/customer reads from the same in-memory participants as U1/U2. */
public final class RepositoryOrderDataQuery implements OrderDataQuery {
    private final DatabaseProductRepository products;
    private final StockRepository stocks;
    private final LoyaltyRepository loyalty;
    private final List<entities.Customer> customers;

    public RepositoryOrderDataQuery(DatabaseProductRepository products, StockRepository stocks,
                                    LoyaltyRepository loyalty, List<entities.Customer> customers) {
        this.products = java.util.Objects.requireNonNull(products);
        this.stocks = java.util.Objects.requireNonNull(stocks);
        this.loyalty = java.util.Objects.requireNonNull(loyalty);
        this.customers = List.copyOf(customers);
    }

    /** Matches the stock → loyalty monitor order of the single-JVM transaction gateway. */
    @Override
    public Data load() {
        synchronized (stocks) {
            synchronized (loyalty) {
                List<Product> catalog = products.findAll().stream().map(product -> {
                    Stock stock = stocks.findByProductId(product.getProductId());
                    return new Product(product.getProductId(), product.getName(), product.getCategory(),
                        product.getUnitPrice(), stock == null ? -1 : stock.getAvailableQuantity());
                }).sorted(Comparator.comparing(Product::name).thenComparing(Product::id)).toList();
                List<Customer> choices = customers.stream().map(customer -> {
                    LoyaltyAccount account = loyalty.findByCustomerId(customer.getCustomerId());
                    return new Customer(customer.getCustomerId(), customer.getName(), customer.getEmail(),
                        account == null ? null : account.getPointsBalance());
                }).toList();
                return new Data(catalog, choices);
            }
        }
    }
}
