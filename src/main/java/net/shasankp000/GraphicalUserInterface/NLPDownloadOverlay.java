package net.shasankp000.GraphicalUserInterface;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.shasankp000.Overlay.NLPDownloadProgressManager;

/**
 * Compact progress bar shown at the bottom of the screen during NLP model download.
 * Persists across all screens and is non-intrusive.
 */
public class NLPDownloadOverlay {

    private static final int PROGRESS_BAR_HEIGHT = 3;
    private static final int TEXT_PADDING = 4;
    private static final int MARGIN_BOTTOM = 2;
    private static final int MARGIN_SIDE = 20;

    /**
     * Render compact progress bar at bottom of screen (center-aligned)
     */
    public static void render(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        if (!NLPDownloadProgressManager.hasActivity()) {
            return; // Don't render if no download activity (includes auto-dismiss)
        }

        Minecraft client = Minecraft.getInstance();

        // Current task text - compact format
        String taskText = NLPDownloadProgressManager.getCurrentTask();
        float percentage = NLPDownloadProgressManager.getProgressPercentage();
        int currentStep = NLPDownloadProgressManager.getCurrentStep();
        int totalSteps = NLPDownloadProgressManager.getTotalSteps();

        // Calculate maximum bar width
        int maxBarWidth = screenWidth - (MARGIN_SIDE * 2);

        // Shorten task text if too long
        int maxTextWidth = maxBarWidth - 100; // Leave space for percentage
        String displayText = taskText;
        if (client.font.width(displayText) > maxTextWidth) {
            while (client.font.width(displayText + "...") > maxTextWidth && displayText.length() > 10) {
                displayText = displayText.substring(0, displayText.length() - 1);
            }
            displayText += "...";
        }

        // Format: "Task... (X/Y - Z%)"
        String fullText = String.format("%s (%d/%d - %.0f%%)", displayText, currentStep, totalSteps, percentage);

        ChatFormatting textColor = NLPDownloadProgressManager.hasError() ? ChatFormatting.RED :
                               NLPDownloadProgressManager.isCompleted() ? ChatFormatting.GREEN :
                               ChatFormatting.WHITE;

        Component text = Component.literal(fullText).withStyle(textColor);

        // Calculate text width for centering
        int textWidth = client.font.width(fullText);

        // Center the text
        int textX = (screenWidth - textWidth) / 2;
        int textY = screenHeight - PROGRESS_BAR_HEIGHT - MARGIN_BOTTOM - TEXT_PADDING - client.font.lineHeight;

        // Draw semi-transparent dark background for text (centered)
        int bgX1 = textX - 2;
        int bgY1 = textY - 1;
        int bgX2 = textX + textWidth + 2;
        int bgY2 = textY + client.font.lineHeight + 1;
        context.fill(bgX1, bgY1, bgX2, bgY2, 0xC0000000);

        // Draw text (centered)
        context.text(client.font, text, textX, textY, 0xFFFFFF);

        // Progress bar - centered and slightly narrower for better aesthetics
        int barWidth = Math.min(maxBarWidth, 600); // Max 600px wide for centering
        int barX = (screenWidth - barWidth) / 2; // Center the bar
        int barY = screenHeight - PROGRESS_BAR_HEIGHT - MARGIN_BOTTOM;

        // Progress bar background (dark gray with transparency)
        context.fill(barX, barY, barX + barWidth, barY + PROGRESS_BAR_HEIGHT, 0xE0000000);

        // Progress bar fill
        if (totalSteps > 0) {
            int fillWidth = (int) ((barWidth * percentage) / 100f);

            int fillColor;
            if (NLPDownloadProgressManager.hasError()) {
                fillColor = 0xFFFF4444; // Bright Red
            } else if (NLPDownloadProgressManager.isCompleted()) {
                fillColor = 0xFF44FF44; // Bright Green
            } else {
                fillColor = 0xFF4A90E2; // Blue
            }

            context.fill(barX, barY, barX + fillWidth, barY + PROGRESS_BAR_HEIGHT, fillColor);
        }
    }

    /**
     * Render on any screen - this allows persistence across all screens
     */
    public static void renderOnAnyScreen(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        render(context, screenWidth, screenHeight);
    }
}

