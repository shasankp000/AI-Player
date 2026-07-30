package net.shasankp000.Entity;


import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.shasankp000.ChatUtils.ChatUtils;

public class RayCasting {

    private static String checkOutput = "";

    public static String detect(ServerPlayerEntity bot) {
        detectBlocks(bot);
        return checkOutput;
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
            checkOutput = "Block detected in front at " + hitResult.getBlockPos().getX() + ", " + hitResult.getBlockPos().getY() + ", " + hitResult.getBlockPos().getZ();

            ChatUtils.sendChatMessages(bot.getCommandSource().withSilent().withMaxLevel(4), "Block detected in front at " + hitResult.getBlockPos().getX() + ", " + hitResult.getBlockPos().getY() + ", " + hitResult.getBlockPos().getZ());
            
        } else if (hitResult.getType() == HitResult.Type.MISS) {
            System.out.println("Nothing detected in front by raycast");

            checkOutput = "No block detected in front";

            ChatUtils.sendChatMessages(bot.getCommandSource().withSilent().withMaxLevel(4), "No block detected in front");
        }

    }


}

