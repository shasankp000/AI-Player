package net.shasankp000.GraphicalUserInterface;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shasankp000.AIPlayer;
import net.shasankp000.AIPlayerClient;
import net.shasankp000.GraphicalUserInterface.Widgets.DropdownMenuWidget;
import net.shasankp000.Network.configNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigManager extends Screen {
    public static final Logger LOGGER = LoggerFactory.getLogger("ConfigMan");
    public Screen parent;
    private DropdownMenuWidget dropdownMenuWidget;
    private EditBox searchField;
    private List<String> allModels;
    private List<String> filteredModels;

    public ConfigManager(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        // added this line so that the models will load immediately after the API key has been entered and saved into the json.
        LOGGER.info("Refreshing model list from provider...");
        AIPlayer.CONFIG.updateModels();

        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            AIPlayerClient.CONFIG.updateModels();
        }

        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            allModels = AIPlayerClient.CONFIG.getModelList();
        } else {
            allModels = AIPlayer.CONFIG.getModelList();
        }
        LOGGER.info("Fetched {} models from provider on frontend.", allModels.size());
        filteredModels = new ArrayList<>(allModels);

        int centerX = this.width / 2;
        int topMargin = 50;
        int fieldWidth = Math.max(300, Math.min(520, this.width - 80));
        int fieldHeight = 20;
        int buttonWidth = 100;
        int spacing = 30;

        searchField = new EditBox(this.font, centerX - fieldWidth / 2, topMargin, fieldWidth, fieldHeight, Component.nullToEmpty("Search models..."));
        searchField.setMaxLength(256);
        searchField.setHint(Component.nullToEmpty("Search models..."));
        searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchField);
        this.addWidget(searchField);

        int dropdownY = topMargin + spacing + 10;
        dropdownMenuWidget = new DropdownMenuWidget(centerX - fieldWidth / 2, dropdownY, fieldWidth, fieldHeight, Component.nullToEmpty("List of available models"), filteredModels);
        this.dropdownMenuWidget = dropdownMenuWidget;
        this.addWidget(dropdownMenuWidget);

        int buttonY = this.height - 40;
        int buttonSpacing = buttonWidth + 20;
        int totalButtonWidth = buttonSpacing * 5 - 20;
        int buttonsStartX = centerX - totalButtonWidth / 2;

        this.addRenderableWidget(Button.builder(Component.nullToEmpty("API Keys"), (btn) -> Objects.requireNonNull(this.minecraft).gui.setScreen(new APIKeysScreen(Component.nullToEmpty("API Keys"), this))).bounds(buttonsStartX, buttonY, buttonWidth, fieldHeight).build());
        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Reasoning Log"), (btn) -> Objects.requireNonNull(this.minecraft).gui.setScreen(new ReasoningLogScreen(this))).bounds(buttonsStartX + buttonSpacing, buttonY, buttonWidth, fieldHeight).build());
        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Refresh Models"), (btn) -> this.reloadModels()).bounds(buttonsStartX + buttonSpacing * 2, buttonY, buttonWidth, fieldHeight).build());
        
        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Save"), (btn1) -> {
            this.saveToFile();
            if (this.minecraft != null) {
                this.minecraft.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Settings saved!"), Component.nullToEmpty("Saved settings.")));
            }
        }).bounds(buttonsStartX + buttonSpacing * 3, buttonY, buttonWidth, fieldHeight).build());

        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Close"), (btn1) -> this.onClose()).bounds(buttonsStartX + buttonSpacing * 4, buttonY, buttonWidth, fieldHeight).build());

        this.addRenderableWidget(dropdownMenuWidget);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        String title = "AI-Player Mod Configuration Menu v1.0.5.4-release+26.2";
        context.text(this.font, title, centerX - (this.font.width(title) / 2), 20, 0xFFFFFFFF, true);

        int labelX = searchField.getX();
        context.text(this.font, "Search Models:", labelX, searchField.getY() - 15, 0xFFFFD700, true);
        context.text(this.font, "Select Language Model:", labelX, dropdownMenuWidget.getY() - 15, 0xFFFFD700, true);

        // Shifted an extra 10px down as requested
        int textOffset = dropdownMenuWidget.isExpanded() ? Math.min(filteredModels.size(), 10) * 14 + 20 : 40;
        int infoY = dropdownMenuWidget.getY() + textOffset;

        String currentModel = AIPlayer.CONFIG.getSelectedLanguageModel();
        context.text(this.font, "Currently selected: " + (currentModel != null ? currentModel : "None"), labelX, infoY, 0xFF00FF00, true);
        context.text(this.font, "Showing " + filteredModels.size() + " of " + allModels.size() + " models", labelX, infoY + 15, 0xFFADD8E6, true);

        String helpText = "Search to filter models • Select a model and click Save";
        context.text(this.font, helpText, centerX - (this.font.width(helpText) / 2), this.height - 65, 0xFFFFB6C1, true);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void onSearchChanged(String searchText) {
        if (searchText.trim().isEmpty()) {
            filteredModels = new ArrayList<>(allModels);
        } else {
            filteredModels = allModels.stream()
                    .filter(model -> model.toLowerCase().contains(searchText.toLowerCase().trim()))
                    .collect(Collectors.toList());
        }
        dropdownMenuWidget.updateOptions(filteredModels);
    }

    private void reloadModels() {
        LOGGER.info("Reloading model list from provider...");
        AIPlayer.CONFIG.updateModels();
        if (FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            AIPlayerClient.CONFIG.updateModels();
            allModels = AIPlayerClient.CONFIG.getModelList();
        } else {
            allModels = AIPlayer.CONFIG.getModelList();
        }
        filteredModels = new ArrayList<>(allModels);
        dropdownMenuWidget.updateOptions(filteredModels);
        
        if (this.minecraft != null) {
            this.minecraft.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Models Reloaded"), Component.nullToEmpty("Found " + allModels.size() + " models")));
        }
    }

    private void saveToFile() {
        String modelName = this.dropdownMenuWidget.getSelectedOption();
        if (modelName == null || modelName.trim().isEmpty()) {
            LOGGER.warn("No model selected or model name is empty. Skipping save.");
            if (this.minecraft != null) {
                this.minecraft.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Error"), Component.nullToEmpty("Please select a model first!")));
            }
            return;
        }

        System.out.println("Selected model: " + modelName);
        AIPlayer.CONFIG.setSelectedLanguageModel(modelName);
        AIPlayer.CONFIG.save();
        configNetworkManager.sendSaveConfigPacket(modelName);

        // Restored the indicator screen refresh logic
        onClose();
        assert this.minecraft != null;
        this.minecraft.gui.setScreen(new ConfigManager(Component.empty(), this.parent));
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
    }
}
