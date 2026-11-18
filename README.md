# Aura Perfumes

Aura Perfumes is a JavaFX desktop application for managing a perfume boutique. The application covers inventory, customer orders, loyalty points, scent consultations, and in-store order processing.

## Team and contributions

- **Evie** — U1 stock management, U2 customer orders, and JavaFX integration.
- **Maëlys Badger** — U3 scent consultation booking, U4 boutique order processing, and JavaFX integration.

## Architecture

- `core/` contains the domain model and business use cases.
- `services/` contains persistence implementations, including SQLite repositories.
- `app/` contains the JavaFX interface and the application entry points.

## Run locally

Requires **JDK 25** and **Maven**. From the repository root:

```bash
mvn clean install
mvn -pl app javafx:run
```

The application initializes its SQLite data store as needed. Run `mvn clean test` to execute the test suite.
