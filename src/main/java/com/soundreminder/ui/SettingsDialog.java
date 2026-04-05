package com.soundreminder.ui;

import java.io.File;
import java.util.logging.Logger;

import com.soundreminder.model.AppSettings;
import com.soundreminder.sound.SoundManager;
import com.soundreminder.storage.StorageService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Settings dialog for configuring custom sound and volume.
 */
public class SettingsDialog {

    private static final Logger LOGGER = Logger.getLogger(SettingsDialog.class.getName());

    /** The dialog stage. */
    private final Stage dialogStage;

    /** Current application settings. */
    private final AppSettings settings;

    /** Storage service for persistence. */
    private final StorageService storageService;

    /** Sound manager for testing the sound. */
    private final SoundManager soundManager;

    /** Slider for volume control. */
    private Slider volumeSlider;

    /** Label showing the current custom sound path. */
    private Label soundPathLabel;

    /**
     * Creates and shows the Settings dialog modally.
     *
     * @param owner          the owner stage
     * @param settings       the current settings
     * @param storageService the storage service
     * @param soundManager   the sound manager
     */
    public SettingsDialog(Stage owner, AppSettings settings, StorageService storageService,
                          SoundManager soundManager) {
        this.settings = settings;
        this.storageService = storageService;
        this.soundManager = soundManager;

        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(owner);
        dialogStage.setTitle("Settings");
        dialogStage.setResizable(false);

        buildUI();
        dialogStage.showAndWait();
    }

    /**
     * Builds the dialog's user interface.
     */
    private void buildUI() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setPrefWidth(400);
        root.getStyleClass().add("settings-dialog");

        // --- Sound Settings ---
        Label soundTitle = new Label("Sound Settings");
        soundTitle.getStyleClass().add("section-label");

        // Custom sound file
        HBox soundRow = new HBox(10);
        soundRow.setAlignment(Pos.CENTER_LEFT);

        Label soundLabel = new Label("Sound File:");
        soundLabel.getStyleClass().add("field-label");

        soundPathLabel = new Label(
                settings.getCustomSoundPath() != null && !settings.getCustomSoundPath().isBlank()
                        ? settings.getCustomSoundPath()
                        : "Default (bundled)"
        );
        soundPathLabel.getStyleClass().add("sound-path-label");
        soundPathLabel.setWrapText(true);
        soundPathLabel.setMaxWidth(250);

        Button browseButton = new Button("Browse...");
        browseButton.getStyleClass().add("btn-secondary");
        browseButton.setOnAction(e -> handleBrowseSound());

        soundRow.getChildren().addAll(soundLabel, soundPathLabel, browseButton);

        // Test sound button
        Button testSoundButton = new Button("Test Sound");
        testSoundButton.getStyleClass().add("btn-secondary");
        testSoundButton.setOnAction(e -> soundManager.testSound());

        // Volume control
        VBox volumeSection = new VBox(8);

        HBox volumeRow = new HBox(10);
        volumeRow.setAlignment(Pos.CENTER_LEFT);

        Label volumeLabel = new Label("Volume:");
        volumeLabel.getStyleClass().add("field-label");

        volumeSlider = new Slider(0, 100, settings.getVolume() * 100);
        volumeSlider.getStyleClass().add("volume-slider");
        volumeSlider.setShowTickLabels(true);
        volumeSlider.setShowTickMarks(true);
        volumeSlider.setMajorTickUnit(25);
        volumeSlider.setMinorTickCount(5);
        volumeSlider.setBlockIncrement(5);
        volumeSlider.setPrefWidth(200);

        Label volumeValueLabel = new Label(String.format("%.0f%%", settings.getVolume() * 100));
        volumeValueLabel.getStyleClass().add("volume-value-label");
        volumeValueLabel.setPrefWidth(45);

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                volumeValueLabel.setText(String.format("%.0f%%", newVal.doubleValue()))
        );

        volumeRow.getChildren().addAll(volumeLabel, volumeSlider, volumeValueLabel);
        volumeSection.getChildren().addAll(volumeRow, testSoundButton);

        // --- Buttons ---
        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("btn-primary");
        saveButton.setOnAction(e -> handleSave());

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("btn-secondary");
        cancelButton.setOnAction(e -> dialogStage.close());

        buttonRow.getChildren().addAll(saveButton, cancelButton);

        root.getChildren().addAll(soundTitle, soundRow, new Separator(), volumeSection,
                new Separator(), buttonRow);

        Scene scene = new Scene(root);
        java.net.URL cssUrl = SettingsDialog.class.getResource("/css/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        dialogStage.setScene(scene);
    }

    /**
     * Handles the "Browse" button for selecting a custom sound file.
     */
    private void handleBrowseSound() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Sound File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aiff", "*.au")
        );

        File selected = fileChooser.showOpenDialog(dialogStage);
        if (selected != null && selected.exists()) {
            soundPathLabel.setText(selected.getAbsolutePath());
            LOGGER.info("Custom sound selected: " + selected.getAbsolutePath());
        }
    }

    /**
     * Handles the "Save" button click. Persists settings and updates the sound manager.
     */
    private void handleSave() {
        // Update settings from UI
        String soundPath = soundPathLabel.getText();
        if ("Default (bundled)".equals(soundPath)) {
            settings.setCustomSoundPath(null);
        } else {
            settings.setCustomSoundPath(soundPath);
        }

        settings.setVolume(volumeSlider.getValue() / 100.0);

        // Persist
        storageService.saveSettings(settings);

        // Update sound manager
        soundManager.updateSettings(settings);

        LOGGER.info("Settings saved: " + settings);
        dialogStage.close();
    }
}
