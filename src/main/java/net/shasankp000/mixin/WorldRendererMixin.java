package net.shasankp000.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin to inject threat debug rendering into world rendering
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
}
