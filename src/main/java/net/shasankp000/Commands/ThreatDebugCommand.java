package net.shasankp000.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.shasankp000.Overlay.ThreatDebugManager;

/**
 * Command to toggle threat analysis debug overlay
 */
public class ThreatDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("threatdebug")
                .requires(source -> true)
                .executes(ThreatDebugCommand::toggleDebug)
                .then(Commands.literal("on")
                    .executes(ctx -> setDebug(ctx, true)))
                .then(Commands.literal("off")
                    .executes(ctx -> setDebug(ctx, false)))
                .then(Commands.literal("clear")
                    .executes(ThreatDebugCommand::clearDebug))
        );
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        ThreatDebugManager.toggleDebug();
        boolean enabled = ThreatDebugManager.isDebugEnabled();

        Component message = Component.literal("Threat Analysis Debug: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(enabled ? "ENABLED" : "DISABLED")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));

        ctx.getSource().sendSuccess(() -> message, true);

        if (enabled) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("Threat calculations will now be displayed above entities.")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int setDebug(CommandContext<CommandSourceStack> ctx, boolean enable) {
        ThreatDebugManager.setDebugEnabled(enable);

        Component message = Component.literal("Threat Analysis Debug: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(enable ? "ENABLED" : "DISABLED")
                .withStyle(enable ? ChatFormatting.GREEN : ChatFormatting.RED));

        ctx.getSource().sendSuccess(() -> message, true);

        if (enable) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("Threat calculations will now be displayed above entities.")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int clearDebug(CommandContext<CommandSourceStack> ctx) {
        ThreatDebugManager.clear();

        ctx.getSource().sendSuccess(() ->
            Component.literal("Cleared all threat debug data.")
                .withStyle(ChatFormatting.GREEN), true);

        return Command.SINGLE_SUCCESS;
    }
}

