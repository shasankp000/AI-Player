package net.shasankp000.GraphicalUserInterface;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.shasankp000.Overlay.ThinkingStateManager;

import java.util.List;
import java.util.Objects;

public class ReasoningLogScreen extends Screen {
    private final Screen parent;

    public ReasoningLogScreen(Screen parent) {
        super(Component.nullToEmpty("Reasoning Log"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Button closeButton = Button.builder(Component.nullToEmpty("Close"), (btn) -> this.onClose())
                .bounds(this.width - 120, 40, 100, 20)
                .build();

        this.addRenderableWidget(closeButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int x = 20, y = 40;
        int white = 0xFFFFFFFF;

        context.text(this.font, "Chain-of-Thought Reasoning:", x, y, white, true);

        int i = 1;

        int maxWidth = this.width - 40;

        for (String line : ThinkingStateManager.getReasoningLines()) {
            List<FormattedCharSequence> wrappedLines = this.font.split(Component.nullToEmpty(line), maxWidth);
            for (FormattedCharSequence wrapped : wrappedLines) {
                context.text(this.font, wrapped, x + 10, y + i * 12, white, false);
                i++;
            }
        }
    }


    @Override
    public void onClose() {
        Objects.requireNonNull(this.minecraft).gui.setScreen(this.parent);
    }
}
