package net.shasankp000.Commands;

import carpet.CarpetSettings;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;



public class spawnFakePlayer {

    public static void register()  {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("spawnbot")
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer(); // gets the minecraft server
                    BlockPos spawnPos = getBlockPos(context);

                    RegistryKey<World> dimType = context.getSource().getWorld().getRegistryKey();

                    Vec2f facing = context.getSource().getRotation();

                    Vec3d pos = new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

                    CarpetSettings.allowSpawningOfflinePlayers = true;

                    GameMode mode = GameMode.SURVIVAL;

                    EntityPlayerMPFake.createFake(
                            "Steve",
                            server,
                            pos,
                            facing.y,
                            facing.x,
                            dimType,
                            mode,
                            false
                            );


                    return 1;
                })));


    }

    private static @NotNull BlockPos getBlockPos(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer(); // gets the player who executed the command


        // Set spawn location for the second player
        assert player != null;
        return new BlockPos((int) player.getX() + 5, (int) player.getY(), (int) player.getZ());
    }

}
