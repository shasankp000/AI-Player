package net.shasankp000.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.shasankp000.GraphicalUserInterface.NLPDownloadOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject NLP download progress bar into ALL screens.
 * This ensures the progress bar persists across title screen, options, world creation, etc.
 */
@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Render the compact progress bar at the bottom of ANY screen
        NLPDownloadOverlay.renderOnAnyScreen(context, context.guiWidth(), context.guiHeight());
    }
}
