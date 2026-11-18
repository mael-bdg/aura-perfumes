package application;

import entities.Product;
import entities.ProductCategory;
import entities.Stock;
import java.math.BigDecimal;
import java.util.UUID;
import repositories.DatabaseProductRepository;
import repositories.DatabaseStockRepository;

/** Existing console demonstration inventory, shared by application bootstraps. */
public final class DemoInventory {
    private DemoInventory() { }

    /** Seeds the existing two demonstration products into the supplied session stores. */
    public static void populate(DatabaseProductRepository products, DatabaseStockRepository stocks,
                                UUID perfumeId, UUID candleId) {
        products.addProduct(new Product(perfumeId, "Aura", ProductCategory.PERFUME,
            new BigDecimal("79.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), perfumeId, 10, 0));
        products.addProduct(new Product(candleId, "Aura Candle", ProductCategory.SCENTED_CANDLE,
            new BigDecimal("24.50"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), candleId, 20, 0));
        // One stable demonstration product in each remaining catalog category.
        UUID product301 = new UUID(0, 301);
        products.addProduct(new Product(product301, "Jardin d’Aura Diffuser", ProductCategory.REED_DIFFUSER,
            new BigDecimal("34.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), product301, 16, 0));
        UUID product302 = new UUID(0, 302);
        products.addProduct(new Product(product302, "Velvet Room Spray", ProductCategory.ROOM_SPRAY,
            new BigDecimal("19.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), product302, 24, 0));
        UUID product303 = new UUID(0, 303);
        products.addProduct(new Product(product303, "Rose Silk Body Lotion", ProductCategory.SCENTED_BODY_LOTION,
            new BigDecimal("29.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), product303, 18, 0));
        UUID product304 = new UUID(0, 304);
        products.addProduct(new Product(product304, "Pure Aura Shower Gel", ProductCategory.SHOWER_GEL,
            new BigDecimal("17.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), product304, 25, 0));
        UUID product305 = new UUID(0, 305);
        products.addProduct(new Product(product305, "Aura Travel Spray", ProductCategory.TRAVEL_ATOMIZER,
            new BigDecimal("14.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), product305, 30, 0));
        UUID product306 = new UUID(0, 306);
        products.addProduct(new Product(product306, "Discovery Collection", ProductCategory.SAMPLE_SET,
            new BigDecimal("22.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), product306, 12, 0));
        UUID product307 = new UUID(0, 307);
        products.addProduct(new Product(product307, "Signature Gift Box", ProductCategory.GIFT_BOX,
            new BigDecimal("119.90"), null));
        stocks.addStock(new Stock(UUID.randomUUID(), product307, 8, 0));

    }
}
