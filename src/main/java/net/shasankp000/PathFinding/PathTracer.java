package net.shasankp000.PathFinding;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;

/** Compatibility facade for callers that still submit legacy segments. */
public final class PathTracer {
    private PathTracer() {}

    public static final class BotSegmentManager {
        private final MinecraftServer server;
        private final ServerCommandSource source;
        private final Queue<Segment> segments = new java.util.ArrayDeque<>();
        private boolean sprint;

        public BotSegmentManager(MinecraftServer server, ServerCommandSource source, String ignoredBotName) {
            this.server = server;
            this.source = source;
        }

        public static boolean getBotMovementStatus() { return NavigationService.isAnyNavigating(); }

        public void addSegmentJob(Segment segment) { segments.add(segment); sprint |= segment.sprint(); }

        public void startProcessing() {
            ServerPlayerEntity player = source.getPlayer();
            Segment last = null;
            for (Segment segment : segments) last = segment;
            if (player != null && last != null) {
                NavigationService.navigate(player, BlockPos.ofFloored(last.end().x, last.end().y, last.end().z),
                        NavigationOptions.of(sprint));
            }
        }

        public static String tracePathOutput(ServerCommandSource source) {
            ServerPlayerEntity bot = source == null ? null : source.getPlayer();
            if (bot == null) return "Bot not found";
            BlockPos pos = bot.getBlockPos();
            return String.format("Bot moved to position - x: %d y: %d z: %d", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public static CompletableFuture<String> tracePath(MinecraftServer server, ServerCommandSource source,
                                                      String ignoredBotName, Queue<Segment> segments,
                                                      boolean sprint) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return CompletableFuture.completedFuture("Player not found");
        Segment last = null;
        for (Segment segment : segments) last = segment;
        if (last == null) return CompletableFuture.completedFuture(BotSegmentManager.tracePathOutput(source));
        return NavigationService.navigate(player, BlockPos.ofFloored(last.end().x, last.end().y, last.end().z),
                        NavigationOptions.of(sprint))
                .thenApply(result -> result.reached() ? BotSegmentManager.tracePathOutput(source) : result.status().name());
    }

    public static void flushAllMovementTasks() {
        MinecraftServer server = net.shasankp000.AIPlayer.serverInstance;
        if (server != null) NavigationService.cancelAll(server, "All movement tasks flushed");
    }
}
