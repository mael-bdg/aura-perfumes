package gui.orders;

import entities.ProductCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Read-only inputs for the catalog and customer selector. Missing quantities use -1. */
@FunctionalInterface
public interface OrderDataQuery {
    Data load();

    record Product(UUID id, String name, ProductCategory category, BigDecimal unitPrice, int available) { }
    record Customer(UUID id, String name, String email, Integer points) { }
    record Data(List<Product> products, List<Customer> customers) {
        public Data {
            products = List.copyOf(products);
            customers = List.copyOf(customers);
        }
    }
}
