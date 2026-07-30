package net.shasankp000.GraphicalUserInterface;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.shasankp000.AIPlayer;
import net.shasankp000.Network.configNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class APIKeysScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget openAIKeyField;
    private TextFieldWidget claudeKeyField;
    private TextFieldWidget geminiKeyField;
    private TextFieldWidget grokKeyField;
    private TextFieldWidget customApiKeyField;
    private TextFieldWidget customApiUrlField;
    public static final Logger LOGGER = LoggerFactory.getLogger("ConfigAPIKeysMan");

    public APIKeysScreen(Text title, Screen parent) {
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
        this.openAIKeyField = new TextFieldWidget(this.textRenderer, startX, startY, fieldWidth, fieldHeight, Text.empty());
        this.openAIKeyField.setMaxLength(maxApiKeyLength);
        this.openAIKeyField.setText(AIPlayer.CONFIG.getOpenAIKey());
        this.addDrawableChild(this.openAIKeyField);
        this.addSelectableChild(this.openAIKeyField);

        // Claude Key Field
        this.claudeKeyField = new TextFieldWidget(this.textRenderer, startX, startY + 30, fieldWidth, fieldHeight, Text.empty());
        this.claudeKeyField.setMaxLength(maxApiKeyLength);
        this.claudeKeyField.setText(AIPlayer.CONFIG.getClaudeKey());
        this.addDrawableChild(this.claudeKeyField);
        this.addSelectableChild(this.claudeKeyField);

        // Gemini Key Field
        this.geminiKeyField = new TextFieldWidget(this.textRenderer, startX, startY + 60, fieldWidth, fieldHeight, Text.empty());
        this.geminiKeyField.setMaxLength(maxApiKeyLength);
        this.geminiKeyField.setText(AIPlayer.CONFIG.getGeminiKey());
        this.addDrawableChild(this.geminiKeyField);
        this.addSelectableChild(this.geminiKeyField);

        // Grok Key Field
        this.grokKeyField = new TextFieldWidget(this.textRenderer, startX, startY + 90, fieldWidth, fieldHeight, Text.empty());
        this.grokKeyField.setMaxLength(maxApiKeyLength);
        this.grokKeyField.setText(AIPlayer.CONFIG.getGrokKey());
        this.addDrawableChild(this.grokKeyField);
        this.addSelectableChild(this.grokKeyField);

        // Custom API URL Field
        this.customApiUrlField = new TextFieldWidget(this.textRenderer, startX, startY + 120, fieldWidth, fieldHeight, Text.empty());
        this.customApiUrlField.setMaxLength(512); // URLs can be longer than API keys
        this.customApiUrlField.setText(AIPlayer.CONFIG.getCustomApiUrl());
        this.addDrawableChild(this.customApiUrlField);
        this.addSelectableChild(this.customApiUrlField);

        // Custom API Key Field
        this.customApiKeyField = new TextFieldWidget(this.textRenderer, startX, startY + 150, fieldWidth, fieldHeight, Text.empty());
        this.customApiKeyField.setMaxLength(maxApiKeyLength);
        this.customApiKeyField.setText(AIPlayer.CONFIG.getCustomApiKey());
        this.addDrawableChild(this.customApiKeyField);
        this.addSelectableChild(this.customApiKeyField);

        // Save Button
        ButtonWidget saveButton = ButtonWidget.builder(Text.of("Save"), (btn) -> {
            this.saveToFile();

            if (this.client != null) {
                this.client.getToastManager().add(
                        SystemToast.create(this.client, SystemToast.Type.NARRATOR_TOGGLE, Text.of("API Keys Saved!"), Text.of("Your API keys have been saved.")));
            }
        }).dimensions(this.width / 2 - buttonWidth - 10, startY + 190, buttonWidth, fieldHeight).build();
        this.addDrawableChild(saveButton);

        // Done Button
        ButtonWidget doneButton = ButtonWidget.builder(Text.of("Done"), (btn) -> {
            // Close the current screen and return to the parent
            assert this.client != null;
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 + 10, startY + 190, buttonWidth, fieldHeight).build();
        this.addDrawableChild(doneButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // Draw labels
        int labelX = this.width / 2 - 150;
        int startY = this.height / 4 + 10;
        int spacing = 30;

        context.drawText(this.textRenderer, "OpenAI API Key:", labelX, startY + 5, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Claude API Key:", labelX, startY + spacing + 5, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Gemini API Key:", labelX, startY + (spacing * 2) + 5, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Grok API Key:", labelX, startY + (spacing * 3) + 5, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Custom API URL:", labelX, startY + (spacing * 4) + 5, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Custom API Key:", labelX, startY + (spacing * 5) + 5, 0xFFFFFFFF, true);
    }


    private void saveToFile() {
        // 1. Save all API keys to the client's config. This should always happen.
        AIPlayer.CONFIG.setOpenAIKey(this.openAIKeyField.getText());
        AIPlayer.CONFIG.setClaudeKey(this.claudeKeyField.getText());
        AIPlayer.CONFIG.setGeminiKey(this.geminiKeyField.getText());
        AIPlayer.CONFIG.setGrokKey(this.grokKeyField.getText());
        AIPlayer.CONFIG.setCustomApiKey(this.customApiKeyField.getText());
        AIPlayer.CONFIG.setCustomApiUrl(this.customApiUrlField.getText());

        // 2. Only save the config file once, after all values have been updated.
        AIPlayer.CONFIG.save();

        // 3. Send a network packet for the currently selected mode only if it's a valid provider.
        String llmMode = System.getProperty("aiplayer.llmMode", "ollama");

        switch (llmMode) {
            case "openai":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.openAIKeyField.getText());
                return;
            case "gemini":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.geminiKeyField.getText());
                return;
            case "claude":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.claudeKeyField.getText());
                return;
            case "grok":
                configNetworkManager.sendSaveAPIPacket(llmMode, this.grokKeyField.getText());
                return;
            case "custom":
                configNetworkManager.sendSaveCustomProviderPacket(this.customApiKeyField.getText(), this.customApiUrlField.getText());
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
    public void close() {
        assert this.client != null;
        this.client.setScreen(this.parent);
    }
}
