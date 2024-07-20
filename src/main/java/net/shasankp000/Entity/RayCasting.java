package net.shasankp000.Entity;

import net.minecraft.client.MinecraftClient;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class RayCasting {

    public static void run() {

        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            ServerPlayerEntity bot = server.getPlayerManager().getPlayer("Steve");

            if (bot != null) {

                Vec3d botPosition = bot.getPos();
                Vec3d botDirection = bot.getRotationVec(1.0F);
                double rayLength = 5.0;
                Vec3d rayEnd = botPosition.add(botDirection.multiply(rayLength));

                RaycastContext raycastContext = new RaycastContext(
                        botPosition,
                        rayEnd,
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.NONE,
                        bot
                );

                HitResult hitResult = bot.getWorld().raycast(raycastContext);

                if (hitResult.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHitResult = (EntityHitResult) hitResult;
                    Entity hitEntity = entityHitResult.getEntity();
                    bot.sendMessage(Text.literal("Entity detected: " + hitEntity.getName().toString()));
                } else if (hitResult.getType() == HitResult.Type.BLOCK) {
                    bot.sendMessage(Text.literal("Block detected in front."));
                } else if (hitResult.getType() == HitResult.Type.MISS) {
                    bot.sendMessage(Text.literal("Nothing detected in front."));
                }
            }

        }

    }

}
