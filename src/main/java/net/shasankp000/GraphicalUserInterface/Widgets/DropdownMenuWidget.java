package net.shasankp000.GraphicalUserInterface.Widgets;

// This file is still heavily work in progress. At least it doesn't crash.

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.shasankp000.FilingSystem.AIPlayerConfigModel;

import java.util.List;

public class DropdownMenuWidget extends ClickableWidget {
    private List<String> options;
    private boolean isOpen;
    private int selectedIndex;
    private int width;
    private int height;

    public DropdownMenuWidget(int x, int y, int width, int height, Text message, List<String> options) {
        super(x, y, width, height, message);
        this.options = options;
        this.isOpen = false;
        this.selectedIndex = -1; // No selection initially
        this.width = width;
        this.height = height;
    }


    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render the main button
        drawCenteredText(context, MinecraftClient.getInstance().textRenderer, getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0xFFFFFF);

        // Render the dropdown menu if open
        if (isOpen) {
            for (int i = 0; i < options.size(); i++) {
                int optionY = this.getY() + this.height * (i + 1);
                context.fill(this.getX(), optionY, this.getX() + this.width, optionY + this.height, 0x80000000);
                drawCenteredText(context, MinecraftClient.getInstance().textRenderer, Text.of(options.get(i)), this.getX() + this.width / 2, optionY + (this.height - 8) / 2, 0xFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isHovered()) {
            isOpen = !isOpen;
            return true;
        }
        if (isOpen) {
            for (int i = 0; i < options.size(); i++) {
                int optionY = this.getY() + this.height * (i + 1);
                if (mouseY >= optionY && mouseY < optionY + this.height) {
                    selectedIndex = i;
                    setMessage(Text.of(options.get(i)));
                    isOpen = false;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // nothing to do there
        return;
    }


    public String getSelectedOption() {
        return selectedIndex >= 0 ? options.get(selectedIndex) : null;
    }

    private void drawCenteredText(DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y, int color) {
        int textWidth = textRenderer.getWidth(text);
        context.drawText(textRenderer, text, centerX - textWidth / 2, y, color, true);
    }
}
