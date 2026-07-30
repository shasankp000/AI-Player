package net.shasankp000.GraphicalUserInterface;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.shasankp000.Overlay.ThinkingStateManager;

import java.util.List;
import java.util.Objects;

public class ReasoningLogScreen extends Screen {
    private final Screen parent;

    public ReasoningLogScreen(Screen parent) {
        super(Text.of("Reasoning Log"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ButtonWidget closeButton = ButtonWidget.builder(Text.of("Close"), (btn) -> this.close())
                .dimensions(this.width - 120, 40, 100, 20)
                .build();

        this.addDrawableChild(closeButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int x = 20, y = 40;
        int white = 0xFFFFFFFF;

        context.drawText(this.textRenderer, "Chain-of-Thought Reasoning:", x, y, white, true);

        int i = 1;

        int maxWidth = this.width - 40;

        for (String line : ThinkingStateManager.getReasoningLines()) {
            List<OrderedText> wrappedLines = this.textRenderer.wrapLines(Text.of(line), maxWidth);
            for (OrderedText wrapped : wrappedLines) {
                context.drawText(this.textRenderer, wrapped, x + 10, y + i * 12, white, false);
                i++;
            }
        }
    }


    @Override
    public void close() {
        Objects.requireNonNull(this.client).setScreen(this.parent);
    }
}
