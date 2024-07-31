package net.shasankp000.Entity;


import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class RayCasting {

    public static void detect(ServerPlayerEntity bot) {
        detectBlocks(bot);
    }

    private static void detectBlocks(ServerPlayerEntity bot) {
        Vec3d botPosition = bot.getPos();
        Direction getDirection = bot.getHorizontalFacing();
        Vec3d botDirection = Vec3d.of(getDirection.getVector());
        double rayLength = 15.0;
        Vec3d rayEnd = botPosition.add(botDirection.multiply(rayLength));

        RaycastContext raycastContext = new RaycastContext(
                botPosition,
                rayEnd,
                RaycastContext.ShapeType.COLLIDER, // Use COLLIDER for block and entity detection
                RaycastContext.FluidHandling.ANY, // Consider all fluids
                bot
        );

        BlockHitResult hitResult = bot.getWorld().raycast(raycastContext);


        if (hitResult.getType() == HitResult.Type.BLOCK) {
            System.out.println("Block detected at: " + hitResult.getBlockPos());
            bot.sendMessage(Text.literal("Block detected in front."));
        } else if (hitResult.getType() == HitResult.Type.MISS) {
            System.out.println("Nothing detected in front by raycast");
            bot.sendMessage(Text.literal("Nothing detected in front."));
        }
    }


}

