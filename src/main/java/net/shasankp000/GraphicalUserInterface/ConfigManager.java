package net.shasankp000.GraphicalUserInterface;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
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
    private TextFieldWidget searchField;
    private List<String> allModels;
    private List<String> filteredModels;

    public ConfigManager(Text title, Screen parent) {
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
        int fieldWidth = 300;
        int fieldHeight = 20;
        int buttonWidth = 100;
        int spacing = 30;

        searchField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, topMargin, fieldWidth, fieldHeight, Text.of("Search models..."));
        searchField.setMaxLength(256);
        searchField.setPlaceholder(Text.of("Search models..."));
        searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(searchField);
        this.addSelectableChild(searchField);

        int dropdownY = topMargin + spacing + 10;
        dropdownMenuWidget = new DropdownMenuWidget(centerX - fieldWidth / 2, dropdownY, fieldWidth, fieldHeight, Text.of("List of available models"), filteredModels);
        this.dropdownMenuWidget = dropdownMenuWidget;
        this.addSelectableChild(dropdownMenuWidget);

        int buttonY = this.height - 40;
        int buttonSpacing = buttonWidth + 20;
        int totalButtonWidth = buttonSpacing * 5 - 20;
        int buttonsStartX = centerX - totalButtonWidth / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.of("API Keys"), (btn) -> Objects.requireNonNull(this.client).setScreen(new APIKeysScreen(Text.of("API Keys"), this))).dimensions(buttonsStartX, buttonY, buttonWidth, fieldHeight).build());
        this.addDrawableChild(ButtonWidget.builder(Text.of("Reasoning Log"), (btn) -> Objects.requireNonNull(this.client).setScreen(new ReasoningLogScreen(this))).dimensions(buttonsStartX + buttonSpacing, buttonY, buttonWidth, fieldHeight).build());
        this.addDrawableChild(ButtonWidget.builder(Text.of("Refresh Models"), (btn) -> this.reloadModels()).dimensions(buttonsStartX + buttonSpacing * 2, buttonY, buttonWidth, fieldHeight).build());
        
        this.addDrawableChild(ButtonWidget.builder(Text.of("Save"), (btn1) -> {
            this.saveToFile();
            if (this.client != null) {
                this.client.getToastManager().add(SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE, Text.of("Settings saved!"), Text.of("Saved settings.")));
            }
        }).dimensions(buttonsStartX + buttonSpacing * 3, buttonY, buttonWidth, fieldHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.of("Close"), (btn1) -> this.close()).dimensions(buttonsStartX + buttonSpacing * 4, buttonY, buttonWidth, fieldHeight).build());

        this.addDrawableChild(dropdownMenuWidget);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        String title = "AI-Player Mod Configuration Menu v1.0.5.4-release+1.21.4";
        context.drawText(this.textRenderer, title, centerX - (this.textRenderer.getWidth(title) / 2), 20, 0xFFFFFFFF, true);

        context.drawText(this.textRenderer, "Search Models:", centerX - 150, searchField.getY() - 15, 0xFFFFD700, true);
        context.drawText(this.textRenderer, "Select Language Model:", centerX - 150, dropdownMenuWidget.getY() - 15, 0xFFFFD700, true);

        // Shifted an extra 10px down as requested
        int textOffset = dropdownMenuWidget.isExpanded() ? Math.min(filteredModels.size(), 10) * 14 + 20 : 40;
        int infoY = dropdownMenuWidget.getY() + textOffset;

        String currentModel = AIPlayer.CONFIG.getSelectedLanguageModel();
        context.drawText(this.textRenderer, "Currently selected: " + (currentModel != null ? currentModel : "None"), centerX - 150, infoY, 0xFF00FF00, true);
        context.drawText(this.textRenderer, "Showing " + filteredModels.size() + " of " + allModels.size() + " models", centerX - 150, infoY + 15, 0xFFADD8E6, true);

        String helpText = "Search to filter models • Select a model and click Save";
        context.drawText(this.textRenderer, helpText, centerX - (this.textRenderer.getWidth(helpText) / 2), this.height - 65, 0xFFFFB6C1, true);

        super.render(context, mouseX, mouseY, delta);
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
        
        if (this.client != null) {
            this.client.getToastManager().add(SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE, Text.of("Models Reloaded"), Text.of("Found " + allModels.size() + " models")));
        }
    }

    private void saveToFile() {
        String modelName = this.dropdownMenuWidget.getSelectedOption();
        if (modelName == null || modelName.trim().isEmpty()) {
            LOGGER.warn("No model selected or model name is empty. Skipping save.");
            if (this.client != null) {
                this.client.getToastManager().add(SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE, Text.of("Error"), Text.of("Please select a model first!")));
            }
            return;
        }

        System.out.println("Selected model: " + modelName);
        AIPlayer.CONFIG.setSelectedLanguageModel(modelName);
        AIPlayer.CONFIG.save();
        configNetworkManager.sendSaveConfigPacket(modelName);

        // Restored the indicator screen refresh logic
        close();
        assert this.client != null;
        this.client.setScreen(new ConfigManager(Text.empty(), this.parent));
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}