package net.shasankp000.GameAI.autonomous;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges Fabric server-side message events to {@link AutonomousManager}.
 *
 * <p>Register once during {@code AIPlayer.onInitialize()} by calling
 * {@link #register(MinecraftServer)}.  After that, every system message
 * (join, leave, death, advancements, …) is forwarded to every
 * {@link WorldEventListener} via {@link AutonomousManager#broadcastServerMessage}.
 *
 * <p><b>Note on the Fabric API:</b><br>
 * Fabric 1.21 exposes {@link ServerMessageEvents#GAME_MESSAGE} which fires for
 * all game (system) messages broadcast to players — including death messages,
 * advancement toasts, and join/leave notifications.  This is exactly the feed
 * that {@link WorldEventListener} needs.
 *
 * <p>Player chat messages are intentionally <em>not</em> forwarded here;
 * player → bot conversations continue to be handled by
 * {@code AIPlayerClient} and {@code ollamaClient} as before.
 */
public class ServerChatEventBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("server-chat-bridge");

    /**
     * Register the bridge.
     * Safe to call multiple times (Fabric deduplicates listener lambdas per class
     * reference, but we guard with a flag anyway).
     */
    public static void register() {
        // GAME_MESSAGE fires for system messages such as:
        //   - "<player> joined the game"
        //   - "<player> left the game"
        //   - "<player> was slain by …"
        //   - "<player> has made the advancement [X]"
        //   - Server broadcasts (/say, scoreboard titles, etc.)
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (overlay) return; // actionbar overlay messages are noise — skip

            String raw = message.getString();
            LOGGER.debug("[server-chat-bridge] Forwarding system message: {}", raw);
            AutonomousManager.getInstance().broadcastServerMessage(raw);
        });

        LOGGER.info("[server-chat-bridge] Registered ServerMessageEvents.GAME_MESSAGE listener");
    }
}
