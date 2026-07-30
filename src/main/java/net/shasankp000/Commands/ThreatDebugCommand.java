package net.shasankp000.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shasankp000.Overlay.ThreatDebugManager;

/**
 * Command to toggle threat analysis debug overlay
 */
public class ThreatDebugCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("threatdebug")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ThreatDebugCommand::toggleDebug)
                .then(CommandManager.literal("on")
                    .executes(ctx -> setDebug(ctx, true)))
                .then(CommandManager.literal("off")
                    .executes(ctx -> setDebug(ctx, false)))
                .then(CommandManager.literal("clear")
                    .executes(ThreatDebugCommand::clearDebug))
        );
    }

    private static int toggleDebug(CommandContext<ServerCommandSource> ctx) {
        ThreatDebugManager.toggleDebug();
        boolean enabled = ThreatDebugManager.isDebugEnabled();

        Text message = Text.literal("Threat Analysis Debug: ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(enabled ? "ENABLED" : "DISABLED")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED));

        ctx.getSource().sendFeedback(() -> message, true);

        if (enabled) {
            ctx.getSource().sendFeedback(() ->
                Text.literal("Threat calculations will now be displayed above entities.")
                    .formatted(Formatting.GRAY), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int setDebug(CommandContext<ServerCommandSource> ctx, boolean enable) {
        ThreatDebugManager.setDebugEnabled(enable);

        Text message = Text.literal("Threat Analysis Debug: ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal(enable ? "ENABLED" : "DISABLED")
                .formatted(enable ? Formatting.GREEN : Formatting.RED));

        ctx.getSource().sendFeedback(() -> message, true);

        if (enable) {
            ctx.getSource().sendFeedback(() ->
                Text.literal("Threat calculations will now be displayed above entities.")
                    .formatted(Formatting.GRAY), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int clearDebug(CommandContext<ServerCommandSource> ctx) {
        ThreatDebugManager.clear();

        ctx.getSource().sendFeedback(() ->
            Text.literal("Cleared all threat debug data.")
                .formatted(Formatting.GREEN), true);

        return Command.SINGLE_SUCCESS;
    }
}

