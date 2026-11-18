package gui;

import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/** Independent JavaFX entry point; console startup remains unchanged. */
public final class AuraPerfumesFXApplication extends Application {
    private GuiCompositionRoot composition;
    /** Loads the welcome page, shared design system, and page styles from the classpath. */
    @Override
    public void start(Stage stage) {
        try {
            URL welcome = requireResource("/gui/fxml/application-shell.fxml");
            URL theme = requireResource("/gui/css/theme.css");
            URL components = requireResource("/gui/css/components.css");
            URL welcomeStyles = requireResource("/gui/css/welcome.css");
            composition = GuiCompositionRoot.demonstration();
            FXMLLoader loader = new FXMLLoader(welcome);
            loader.setControllerFactory(composition::createController);
            Parent root = loader.load();
            Scene scene = new Scene(root, 1120, 760, Color.web("#F8F5F0"));
            scene.getStylesheets().addAll(theme.toExternalForm(), components.toExternalForm(),
                welcomeStyles.toExternalForm());
            stage.setTitle("Aura Perfumes");
            stage.setMinWidth(960);
            stage.setMinHeight(680);
            stage.setScene(scene);
            stage.show();
        } catch (IOException | RuntimeException exception) {
            if (composition != null) composition.close();
            throw new IllegalStateException(
                "Unable to start the Aura Perfumes GUI. Check the welcome FXML and GUI stylesheets.",
                exception);
        }
    }

    /** Releases this GUI session's background worker. */
    @Override
    public void stop() {
        if (composition != null) composition.close();
    }

    /** Reports the precise classpath path when a required resource is missing. */
    private static URL requireResource(String path) throws IOException {
        URL resource = AuraPerfumesFXApplication.class.getResource(path);
        if (resource == null) {
            throw new IOException("Missing required GUI resource: " + path);
        }
        return resource;
    }

    /** Launches the desktop application without initializing console workflows. */
    public static void main(String[] args) {
        launch(args);
    }
}
