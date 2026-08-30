package net.shasankp000.PathFinding;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/** Compatibility facade for callers that still submit legacy segments. */
public final class PathTracer {
    private PathTracer() {}

    public static final class BotSegmentManager {
        private final MinecraftServer server;
        private final CommandSourceStack source;
        private final Queue<Segment> segments = new ArrayDeque<>();
        private boolean sprint;

        public BotSegmentManager(MinecraftServer server, CommandSourceStack source, String ignoredBotName) {
            this.server = server;
            this.source = source;
        }

        public static boolean getBotMovementStatus() { return NavigationService.isAnyNavigating(); }
        public void addSegmentJob(Segment segment) { segments.add(segment); sprint |= segment.sprint(); }

        public void startProcessing() {
            ServerPlayer player = source.getPlayer();
            Segment last = null;
            for (Segment segment : segments) last = segment;
            if (player != null && last != null) {
                NavigationService.navigate(player, last.end(), NavigationOptions.of(sprint));
            }
        }

        public static String tracePathOutput(CommandSourceStack source) {
            ServerPlayer bot = source == null ? null : source.getPlayer();
            if (bot == null) return "Bot not found";
            BlockPos pos = bot.blockPosition();
            return String.format("Bot moved to position - x: %d y: %d z: %d", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public static CompletableFuture<String> tracePath(MinecraftServer server, CommandSourceStack source,
                                                       String ignoredBotName, Queue<Segment> segments,
                                                       boolean sprint) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return CompletableFuture.completedFuture("Player not found");
        Segment last = null;
        for (Segment segment : segments) last = segment;
        if (last == null) return CompletableFuture.completedFuture(BotSegmentManager.tracePathOutput(source));
        return NavigationService.navigate(player, last.end(), NavigationOptions.of(sprint))
                .thenApply(result -> result.reached() ? BotSegmentManager.tracePathOutput(source) : result.status().name());
    }

    public static void flushAllMovementTasks() {
        MinecraftServer server = net.shasankp000.AIPlayer.serverInstance;
        if (server != null) NavigationService.cancelAll(server, "All movement tasks flushed");
    }
}
