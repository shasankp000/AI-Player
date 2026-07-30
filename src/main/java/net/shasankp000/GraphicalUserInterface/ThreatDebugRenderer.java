package net.shasankp000.GraphicalUserInterface;

import net.minecraft.client.Camera;
import net.shasankp000.Overlay.ThreatDebugManager;

/**
 * Threat debug world rendering is disabled until the 26.2 extraction renderer is wired up.
 */
public class ThreatDebugRenderer {
    public static void renderThreatOverlays(Camera camera) {
        if (!ThreatDebugManager.isDebugEnabled()) {
            return;
        }
    }
}
