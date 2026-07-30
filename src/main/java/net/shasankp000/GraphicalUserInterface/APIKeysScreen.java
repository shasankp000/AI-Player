package net.shasankp000.GraphicalUserInterface;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shasankp000.AIPlayer;
import net.shasankp000.Network.configNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class APIKeysScreen extends Screen {

    private final Screen parent;
    private EditBox openAIKeyField;
    private EditBox claudeKeyField;
    private EditBox geminiKeyField;
    private EditBox grokKeyField;
    private EditBox customApiKeyField;
    private EditBox customApiUrlField;
    public static final Logger LOGGER = LoggerFactory.getLogger("ConfigAPIKeysMan");

    public APIKeysScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int startY = this.height / 4 + 10;
        int fieldWidth = 300;
        int fieldHeight = 20;
        int labelWidth = 100;
        int startX = this.width / 2 - (fieldWidth / 2) + labelWidth;
        int buttonWidth = 100;
        int maxApiKeyLength = 256; // Generous baseline for all API key providers

        // OpenAI Key Field
        this.openAIKeyField = new EditBox(this.font, startX, startY, fieldWidth, fieldHeight, Component.empty());
        this.openAIKeyField.setMaxLength(maxApiKeyLength);
        this.openAIKeyField.setValue(AIPlayer.CONFIG.getOpenAIKey());
        this.addRenderableWidget(this.openAIKeyField);
        this.addWidget(this.openAIKeyField);

        // Claude Key Field
        this.claudeKeyField = new EditBox(this.font, startX, startY + 30, fieldWidth, fieldHeight, Component.empty());
        this.claudeKeyField.setMaxLength(maxApiKeyLength);
        this.claudeKeyField.setValue(AIPlayer.CONFIG.getClaudeKey());
        this.addRenderableWidget(this.claudeKeyField);
        this.addWidget(this.claudeKeyField);

        // Gemini Key Field
        this.geminiKeyField = new EditBox(this.font, startX, startY + 60, fieldWidth, fieldHeight, Component.empty());
        this.geminiKeyField.setMaxLength(maxApiKeyLength);
        this.geminiKeyField.setValue(AIPlayer.CONFIG.getGeminiKey());
        this.addRenderableWidget(this.geminiKeyField);
        this.addWidget(this.geminiKeyField);

        // Grok Key Field
        this.grokKeyField = new EditBox(this.font, startX, startY + 90, fieldWidth, fieldHeight, Component.empty());
        this.grokKeyField.setMaxLength(maxApiKeyLength);
        this.grokKeyField.setValue(AIPlayer.CONFIG.getGrokKey());
        this.addRenderableWidget(this.grokKeyField);
        this.addWidget(this.grokKeyField);

        // Custom API URL Field
        this.customApiUrlField = new EditBox(this.font, startX, startY + 120, fieldWidth, fieldHeight, Component.empty());
        this.customApiUrlField.setMaxLength(512); // URLs can be longer than API keys
        this.customApiUrlField.setValue(AIPlayer.CONFIG.getCustomApiUrl());
        this.addRenderableWidget(this.customApiUrlField);
        this.addWidget(this.customApiUrlField);

        // Custom API Key Field
        this.customApiKeyField = new EditBox(this.font, startX, startY + 150, fieldWidth, fieldHeight, Component.empty());
        this.customApiKeyField.setMaxLength(maxApiKeyLength);
        this.customApiKeyField.setValue(AIPlayer.CONFIG.getCustomApiKey());
        this.addRenderableWidget(this.customApiKeyField);
        this.addWidget(this.customApiKeyField);

        // Save Button
        Button saveButton = Button.builder(Component.nullToEmpty("Save"), (btn) -> {
            this.saveToFile();

            if (this.minecraft != null) {
                this.minecraft.gui.toastManager().addToast(
                        new SystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("API Keys Saved!"), Component.nullToEmpty("Your API keys have been saved.")));
            }
        }).bounds(this.width / 2 - buttonWidth - 10, startY + 190, buttonWidth, fieldHeight).build();
        this.addRenderableWidget(saveButton);

        // Done Button
        Button doneButton = Button.builder(Component.nullToEmpty("Done"), (btn) -> {
            // Close the current screen and return to the parent
            assert this.minecraft != null;
            this.minecraft.gui.setScreen(this.parent);
        }).bounds(this.width / 2 + 10, startY + 190, buttonWidth, fieldHeight).build();
        this.addRenderableWidget(doneButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        // Draw labels
        int labelX = this.width / 2 - 150;
        int startY = this.height / 4 + 10;
        int spacing = 30;

        context.text(this.font, "OpenAI API Key:", labelX, startY + 5, 0xFFFFFFFF, true);
        context.text(this.font, "Claude API Key:", labelX, startY + spacing + 5, 0xFFFFFFFF, true);
        context.text(this.font, "Gemini API Key:", labelX, startY + (spacing * 2) + 5, 0xFFFFFFFF, true);
        context.text(this.font, "Grok API Key:", labelX, startY + (spacing * 3) + 5, 0xFFFFFFFF, true);
        context.text(this.font, "Custom API URL:", labelX, startY + (spacing * 4) + 5, 0xFFFFFFFF, true);
        context.text(this.font, "Custom API Key:", labelX, startY + (spacing * 5) + 5, 0xFFFFFFFF, true);
    }


    private void saveToFile() {
        // 1. Save all API keys to the client's config. This should always happen.
        AIPlayer.CONFIG.setOpenAIKey(this.openAIKeyField.getValue());
        AIPlayer.CONFIG.setClaudeKey(this.claudeKeyField.getValue());
        AIPlayer.CONFIG.setGeminiKey(this.geminiKeyField.getValue());
        AIPlayer.CONFIG.setGrokKey(this.grokKeyField.getValue());
        AIPlayer.CONFIG.setCustomApiKey(this.customApiKeyField.getValue());
        AIPlayer.CONFIG.setCustomApiUrl(this.customApiUrlField.getValue());

        // 2. Only save the config file once, after all values have been updated.
        AIPlayer.CONFIG.save();

        // 3. Send a network packet for the currently selected mode only if it's a valid provider.
        String llmMode = System.getProperty("aiplayer.llmMode", "custom");

        switch (llmMode) {
            case "openai":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.openAIKeyField.getValue());
                return;
            case "gemini":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.geminiKeyField.getValue());
                return;
            case "claude":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.claudeKeyField.getValue());
                return;
            case "grok":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.grokKeyField.getValue());
                return;
            case "custom":
                configNetworkManager.sendSaveCustomProviderPacket(this.customApiKeyField.getValue(), this.customApiUrlField.getValue());
                return;
            case "ollama":
                LOGGER.info("No API key packet sent for Ollama mode.");
                return;
            default:
                LOGGER.warn("Unsupported LLM mode: " + llmMode);
                break;
        }
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        this.minecraft.gui.setScreen(this.parent);
    }
}
