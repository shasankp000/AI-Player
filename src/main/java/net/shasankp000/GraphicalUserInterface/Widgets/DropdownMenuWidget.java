package net.shasankp000.GraphicalUserInterface.Widgets;

// This file is still heavily work in progress. At least it doesn't crash.

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DropdownMenuWidget extends ClickableWidget {
    private static final Logger LOGGER = LoggerFactory.getLogger("DropdownWidget");
    private List<String> options;
    private boolean isOpen;
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private final int rowHeight = 14; 
    private final int maxVisibleOptions = 10; 

    public DropdownMenuWidget(int x, int y, int width, int height, Text message, List<String> options) {
        super(x, y, width, height, message);
        this.options = (options != null) ? options : new ArrayList<>();
        this.isOpen = false;
    }

    public boolean isExpanded() {
        return this.isOpen;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Draw main box
        int buttonColor = this.isHovered() ? 0xFF404040 : 0xFF202020;
        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFFFFFF); 
        context.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, buttonColor);

        String label = (selectedIndex >= 0 && selectedIndex < options.size()) ? options.get(selectedIndex) : getMessage().getString();
        context.drawCenteredTextWithShadow(tr, label, this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0xFFFFFF);

        if (isOpen && !options.isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 500); 

            int listSize = Math.min(options.size(), maxVisibleOptions);
            int totalListHeight = listSize * rowHeight;

            context.fill(this.getX(), this.getY() + this.height, this.getX() + this.width, this.getY() + this.height + totalListHeight, 0xFF101010);
            context.drawBorder(this.getX(), this.getY() + this.height, this.width, totalListHeight, 0xFFFFFFFF);

            for (int i = 0; i < listSize; i++) {
                int optionY = this.getY() + this.height + (i * rowHeight);
                boolean isMouseOverOption = mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                                            mouseY >= optionY && mouseY < optionY + rowHeight;

                // Highlight if mouse hovered OR keyboard hovered
                if (isMouseOverOption || i == hoveredIndex) {
                    context.fill(this.getX() + 1, optionY, this.getX() + this.width - 1, optionY + rowHeight, 0xFF3366FF);
                }

                context.drawText(tr, options.get(i), this.getX() + 5, optionY + 3, 0xFFFFFF, false);
            }
            context.getMatrices().pop();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) return false;

        boolean clickedMain = mouseX >= this.getX() && mouseX < this.getX() + this.width && 
                             mouseY >= this.getY() && mouseY < this.getY() + this.height;

        if (isOpen) {
            int listSize = Math.min(options.size(), maxVisibleOptions);
            for (int i = 0; i < listSize; i++) {
                int optionY = this.getY() + this.height + (i * rowHeight);
                
                if (mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                    mouseY >= optionY && mouseY < optionY + rowHeight) {
                    
                    this.selectedIndex = i;
                    this.isOpen = false;
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    return true; 
                }
            }
            this.isOpen = false;
            return true;
        }

        if (clickedMain) {
            this.isOpen = !this.isOpen;
            if (this.isOpen) this.hoveredIndex = 0; 
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active || !this.visible || !this.isOpen) return super.keyPressed(keyCode, scanCode, modifiers);

        int listSize = Math.min(options.size(), maxVisibleOptions);

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.hoveredIndex = (this.hoveredIndex + 1) % listSize;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_UP) {
            this.hoveredIndex = (this.hoveredIndex - 1 + listSize) % listSize;
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (hoveredIndex >= 0 && hoveredIndex < options.size()) {
                this.selectedIndex = hoveredIndex;
                this.isOpen = false;
                this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.isOpen = false;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, getMessage());
    }

    public void updateOptions(List<String> newOptions) {
        this.options = new ArrayList<>(newOptions);
        this.hoveredIndex = -1; // Reset hover on search
    }

    public String getSelectedOption() {
        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            return options.get(selectedIndex);
        }
        return "";
    }
}