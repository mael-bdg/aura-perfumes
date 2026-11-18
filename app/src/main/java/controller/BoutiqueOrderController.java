package controller;

import datatransferobjects.ProcessBoutiqueOrderRequest;
import datatransferobjects.ProcessBoutiqueOrderResponse;
import entities.OrderStatus;
import usecases.ProcessBoutiqueOrderRequestHandler;

import java.util.UUID;

public class BoutiqueOrderController {

    private final ProcessBoutiqueOrderRequestHandler requestHandler;
    private final BoutiqueOrderOutputBoundary outputBoundary;

    public BoutiqueOrderController(
            ProcessBoutiqueOrderRequestHandler requestHandler,
            BoutiqueOrderOutputBoundary outputBoundary) {

        this.requestHandler = requestHandler;
        this.outputBoundary = outputBoundary;
    }

    public void processOrder(
            UUID orderId,
            UUID employeeId,
            OrderStatus targetStatus,
            String pickupPin,
            String cancellationReason) {

        ProcessBoutiqueOrderRequest request =
                new ProcessBoutiqueOrderRequest(
                        orderId,
                        employeeId,
                        targetStatus,
                        cancellationReason,
                        pickupPin
                );

        ProcessBoutiqueOrderResponse response =
                requestHandler.handle(request);

        outputBoundary.presentOrderProcessing(response);
    }
}
