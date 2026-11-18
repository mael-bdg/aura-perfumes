package gui.navigation;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.util.Callback;

/** Keeps the shared shell in place while selecting and loading its current page. */
public final class ApplicationShellController {
    private final Callback<Class<?>, Object> controllerFactory;

    public ApplicationShellController() {
        this(null);
    }

    /** Receives the session's dependency factory from the GUI composition root. */
    public ApplicationShellController(Callback<Class<?>, Object> controllerFactory) {
        this.controllerFactory = controllerFactory;
    }

    @FXML
    private BorderPane contentContainer;

    @FXML
    private Button homeButton;
    @FXML
    private Button stockButton;
    @FXML
    private Button ordersButton;
    @FXML
    private Button consultationButton;
    @FXML
    private Button boutiqueButton;

    private NavigationManager navigationManager;
    private Button activeButton;

    @FXML
    private void initialize() {
        navigationManager = new NavigationManager(contentContainer, controllerFactory);
        showHome();
    }

    @FXML
    private void showHome() {
        navigateTo("/gui/fxml/home.fxml", homeButton);
    }

    @FXML
    private void showStock() {
        navigateTo("/gui/fxml/stock.fxml", stockButton);
    }

    @FXML
    private void showOrders() {
        navigateTo("/gui/fxml/orders.fxml", ordersButton);
    }

    @FXML
    private void showConsultations() {
        navigateTo("/gui/fxml/consultations.fxml", consultationButton);
    }

    @FXML
    private void showBoutiqueOrders() {
        navigateTo("/gui/fxml/boutique-orders.fxml", boutiqueButton);
    }

    private void navigateTo(String path, Button button) {
        navigationManager.navigateTo(path);
        if (activeButton != null) {
            activeButton.getStyleClass().remove("is-active");
            activeButton.setAccessibleHelp(null);
        }
        activeButton = button;
        activeButton.getStyleClass().add("is-active");
        activeButton.setAccessibleHelp("Current page");
    }
}

