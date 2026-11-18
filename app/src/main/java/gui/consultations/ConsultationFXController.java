package gui.consultations;

import controller.ConsultationController;
import controller.ConsultationOutputBoundary;
import datatransferobjects.BookConsultationRequest;
import datatransferobjects.BookConsultationResponse;
import datatransferobjects.ConsultationAvailabilityResponse;
import entities.ConsultationSlot;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javafx.application.Platform;

/** JavaFX adapter: all booking decisions remain in U3 interactors. */
public final class ConsultationFXController implements ConsultationOutputBoundary {
    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    @FXML private DatePicker consultationDate;
    @FXML private ComboBox<AssociateChoice> associateChoice;
    @FXML private CheckBox anyAssociate;
    @FXML private ListView<ConsultationSlot> availableSlots;
    @FXML private TextField customerName;
    @FXML private TextField customerEmail;
    @FXML private TextArea fragrancePreferences;
    @FXML private Button searchButton;
    @FXML private Button bookButton;
    @FXML private Label statusLabel;
    @FXML private VBox confirmationPanel;
    @FXML private Label confirmationDetails;

    private ConsultationController controller;
    private boolean busy;
    private boolean refreshAfterBooking;
    private final Executor worker;
    private java.util.function.Function<UUID, String> associateNameLookup = id -> null;

    public void setAssociateNameLookup(java.util.function.Function<UUID, String> lookup) {
        this.associateNameLookup = java.util.Objects.requireNonNull(lookup);
    }

    /** Standalone compatibility; the application supplies its session worker. */
    public ConsultationFXController() { this(java.util.concurrent.ForkJoinPool.commonPool()); }

    public ConsultationFXController(Executor worker) {
        this.worker = java.util.Objects.requireNonNull(worker);
    }

    @FXML private void initialize() {
        consultationDate.setValue(LocalDate.now().plusDays(1));
        consultationDate.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });
        // Associate choices are discovered from the repository's actual slots.
        anyAssociate.setSelected(true);
        associateChoice.disableProperty().bind(anyAssociate.selectedProperty().or(searchButton.disabledProperty()));
        availableSlots.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ConsultationSlot slot, boolean empty) {
                super.updateItem(slot, empty);
                if (empty || slot == null) { setText(null); return; }
                LocalDateTime start = slot.getStartTime();
                String name = associateChoice.getItems().stream()
                    .filter(a -> a.id().equals(slot.getAssociateId()))
                    .map(AssociateChoice::name).findFirst().orElse(slot.getAssociateId().toString());
                setText(start.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "   "
                    + start.format(SLOT_FORMAT) + " – "
                    + start.plusMinutes(slot.getDurationMinutes()).format(SLOT_FORMAT)
                    + "   |   " + name);
            }
        });
        availableSlots.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> updateBookingButton());
        customerName.textProperty().addListener((obs, old, value) -> updateBookingButton());
        customerEmail.textProperty().addListener((obs, old, value) -> updateBookingButton());
        anyAssociate.selectedProperty().addListener((obs, old, value) -> clearSlots());
        associateChoice.valueProperty().addListener((obs, old, value) -> clearSlots());
        consultationDate.valueProperty().addListener((obs, old, value) -> clearSlots());
        confirmationPanel.setVisible(false);
        confirmationPanel.setManaged(false);
        updateBookingButton();
    }

    public void setController(ConsultationController controller) {
        this.controller = java.util.Objects.requireNonNull(controller);
        searchAvailability();
    }

    @FXML private void searchAvailability() {
        if (controller == null || busy) return;
        LocalDate date = consultationDate.getValue();
        if (date == null || date.isBefore(LocalDate.now())) {
            showStatus("Error: Choose today or a future date.", false); return;
        }
        if (!anyAssociate.isSelected() && associateChoice.getValue() == null) {
            showStatus("Error: Select an associate or choose any available associate.", false); return;
        }
        clearSlots();
        busy = true; updateBookingButton(); searchButton.setDisable(true);
        UUID associate = anyAssociate.isSelected() ? null : associateChoice.getValue().id();
        runBackground(() -> controller.listAvailableSlots(date, associate),
            "Error: Unable to retrieve consultation availability.");
    }

    @FXML private void confirmBooking() {
        if (controller == null || busy) return;
        ConsultationSlot selected = availableSlots.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Error: Select an available appointment.", false); return; }
        String name = customerName.getText().trim();
        String email = customerEmail.getText().trim();
        if (name.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            showStatus("Error: Enter your name and a valid email address.", false); return;
        }
        busy = true; updateBookingButton(); searchButton.setDisable(true);
        // For an explicit slot, preserve its real associate ID. In any-associate
        // mode U3 selects the least-booked available associate at that time.
        BookConsultationRequest request = new BookConsultationRequest(
                name, email, fragrancePreferences.getText(), selected.getStartTime(),
                anyAssociate.isSelected() ? null : selected.getAssociateId(), anyAssociate.isSelected());
        runBackground(() -> controller.bookConsultation(request),
            "Error: Unable to complete consultation booking.");
    }

    @Override public void presentAvailability(ConsultationAvailabilityResponse response) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> presentAvailability(response));
            return;
        }
        List<ConsultationSlot> slots = response.isSuccess() ? response.getAvailableSlots().stream()
            .filter(slot -> !slot.getStartTime().isBefore(LocalDateTime.now())).toList() : List.of();
        // Discover associates only from real U3 availability, never from demo IDs.
        for (ConsultationSlot slot : slots) {
            boolean known = associateChoice.getItems().stream()
                .anyMatch(choice -> choice.id().equals(slot.getAssociateId()));
            if (!known) associateChoice.getItems().add(new AssociateChoice(
                java.util.Objects.requireNonNullElse(associateNameLookup.apply(slot.getAssociateId()),
                    "Associate " + slot.getAssociateId().toString().substring(0, 8)), slot.getAssociateId()));
        }
        availableSlots.getItems().setAll(slots);
        showStatus(response.isSuccess()
            ? (slots.isEmpty() ? "No available appointments for this date." : slots.size() + " available appointment(s). Select a time below.")
            : "Error: " + response.getMessage(), response.isSuccess());
        updateBookingButton();
    }

    @Override public void presentBooking(BookConsultationResponse response) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> presentBooking(response));
            return;
        }
        if (!response.isSuccess()) {
            showStatus("Booking failed: " + response.getMessage(), false);
            // Refresh to reflect a slot taken by another client.
            searchAvailabilityAfterBooking();
            return;
        }
        showStatus("Success: " + response.getMessage(), true);
        confirmationDetails.setText("Booking reference: " + response.getBookingId()
            + "\nScheduled: " + response.getScheduledStartTime()
            + "\nAssociate: " + java.util.Objects.requireNonNullElse(
                associateNameLookup.apply(response.getAssignedAssociateId()), "Not available")
            + "\nConfirmation email: " + (response.isEmailSent() ? "Sent" : "Not sent"));
        confirmationPanel.setManaged(true);
        confirmationPanel.setVisible(true);
        // Remove the selected time immediately so it cannot be submitted twice.
        availableSlots.getItems().clear();
        availableSlots.getSelectionModel().clearSelection();
    }

    private void searchAvailabilityAfterBooking() {
        refreshAfterBooking = true;
    }

    /** SQLite and notification work runs off the UI thread; completion releases the submission gate. */
    private void runBackground(Runnable action, String failure) {
        try {
            worker.execute(() -> {
                try { action.run(); }
                catch (RuntimeException exception) {
                    Platform.runLater(() -> showStatus(failure, false));
                } finally {
                    Platform.runLater(() -> {
                        busy = false;
                        searchButton.setDisable(false);
                        updateBookingButton();
                        if (refreshAfterBooking) {
                            refreshAfterBooking = false;
                            searchAvailability();
                        }
                    });
                }
            });
        } catch (RuntimeException exception) {
            busy = false;
            searchButton.setDisable(false);
            updateBookingButton();
            showStatus(failure, false);
        }
    }

    private void clearSlots() {
        availableSlots.getItems().clear();
        availableSlots.getSelectionModel().clearSelection();
        updateBookingButton();
    }
    private void updateBookingButton() {
        if (bookButton != null) {
            bookButton.setDisable(busy || controller == null || availableSlots.getSelectionModel().getSelectedItem() == null);
            consultationDate.setDisable(busy);
            anyAssociate.setDisable(busy);
            availableSlots.setDisable(busy);
            customerName.setDisable(busy);
            customerEmail.setDisable(busy);
            fragrancePreferences.setDisable(busy);
        }
    }
    private void showStatus(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("message-error", "message-success");
        statusLabel.getStyleClass().add(success ? "message-success" : "message-error");
        statusLabel.setAccessibleText(message);
    }
    private record AssociateChoice(String name, UUID id) {
        @Override public String toString() { return name; }
    }
}
