package presenter;

import controller.BoutiqueOrderOutputBoundary;
import datatransferobjects.ProcessBoutiqueOrderResponse;

public class BoutiqueOrderPresenter
        implements BoutiqueOrderOutputBoundary {

    private final ConsultationViewBoundary view;

    public BoutiqueOrderPresenter(ConsultationViewBoundary view) {
        this.view = view;
    }

    @Override
    public void presentOrderProcessing(
            ProcessBoutiqueOrderResponse response) {

        StringBuilder message = new StringBuilder();

        if (response.isSuccess()) {
            message.append("Order processing successful!\n");
            message.append("Order ID: ")
                    .append(response.getOrderId())
                    .append("\n");
            message.append("New status: ")
                    .append(response.getNewStatus())
                    .append("\n");
        } else {
            message.append("Order processing failed.\n");
        }

        message.append(response.getMessage());

        view.display(message.toString());
    }
}
