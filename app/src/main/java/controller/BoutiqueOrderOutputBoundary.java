package controller;

import datatransferobjects.ProcessBoutiqueOrderResponse;

public interface BoutiqueOrderOutputBoundary {

    void presentOrderProcessing(
            ProcessBoutiqueOrderResponse response
    );
}

