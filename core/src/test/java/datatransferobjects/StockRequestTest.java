package datatransferobjects;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockRequestTest {
    @Test
    void preservesEveryOperationAndLeavesQuantityValidationToBusinessLogic() {
        UUID id = UUID.randomUUID();
        assertArrayEquals(new StockOperation[] {StockOperation.CHECK, StockOperation.INCREASE, StockOperation.DECREASE},
            StockOperation.values());
        for (StockOperation operation : StockOperation.values()) {
            for (int quantity : new int[] {Integer.MIN_VALUE, 0, 1, Integer.MAX_VALUE}) {
                StockRequest request = new StockRequest(id, operation, quantity);
                assertEquals(id, request.getProductId());
                assertEquals(operation, request.getOperation());
                assertEquals(quantity, request.getQuantity());
            }
        }
    }

    @Test
    void rejectsNullProductIdAndOperation() {
        assertThrows(NullPointerException.class, () -> new StockRequest(null, StockOperation.CHECK, 0));
        assertThrows(NullPointerException.class, () -> new StockRequest(UUID.randomUUID(), null, 0));
    }
}
