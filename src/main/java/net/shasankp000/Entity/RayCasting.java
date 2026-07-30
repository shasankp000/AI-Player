package net.shasankp000.Entity;


import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.ChatUtils.ChatUtils;

public class RayCasting {

    private static String checkOutput = "";

    public static String detect(ServerPlayer bot) {
        detectBlocks(bot);
        return checkOutput;
    }

    private static void detectBlocks(ServerPlayer bot) {

        Vec3 botPosition = bot.position();
        Direction getDirection = bot.getDirection();
        Vec3 botDirection = Vec3.atLowerCornerOf(getDirection.getUnitVec3i());
        double rayLength = 15.0;
        Vec3 rayEnd = botPosition.add(botDirection.scale(rayLength));

        ClipContext raycastContext = new ClipContext(
                botPosition,
                rayEnd,
                ClipContext.Block.COLLIDER, // Use COLLIDER for block and entity detection
                ClipContext.Fluid.ANY, // Consider all fluids
                bot
        );

        BlockHitResult hitResult = bot.level().clip(raycastContext);


        if (hitResult.getType() == HitResult.Type.BLOCK) {
            System.out.println("Block detected at: " + hitResult.getBlockPos());
            checkOutput = "Block detected in front at " + hitResult.getBlockPos().getX() + ", " + hitResult.getBlockPos().getY() + ", " + hitResult.getBlockPos().getZ();

            ChatUtils.sendChatMessages(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "Block detected in front at " + hitResult.getBlockPos().getX() + ", " + hitResult.getBlockPos().getY() + ", " + hitResult.getBlockPos().getZ());
            
        } else if (hitResult.getType() == HitResult.Type.MISS) {
            System.out.println("Nothing detected in front by raycast");

            checkOutput = "No block detected in front";

            ChatUtils.sendChatMessages(bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "No block detected in front");
        }

    }


}

