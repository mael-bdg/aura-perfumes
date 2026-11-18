package inventory;

import java.util.List;

/** Read-only inventory input for application presentation adapters. */
@FunctionalInterface
public interface InventoryQuery {
    /** Returns detached catalog and stock values from the current session. */
    List<InventoryRow> load();
}
