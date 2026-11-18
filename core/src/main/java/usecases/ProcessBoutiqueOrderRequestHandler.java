package usecases;

import datatransferobjects.ProcessBoutiqueOrderRequest;
import datatransferobjects.ProcessBoutiqueOrderResponse;

public interface ProcessBoutiqueOrderRequestHandler {

    ProcessBoutiqueOrderResponse handle(
            ProcessBoutiqueOrderRequest request
    );
}
