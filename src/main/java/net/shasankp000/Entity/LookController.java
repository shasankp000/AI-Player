package net.shasankp000.Entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class LookController {

    public static String faceBlock(ServerPlayerEntity bot, BlockPos targetPos) {
        Vec3d botEyePos = bot.getEyePos();
        Vec3d targetVec = new Vec3d(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5
        );

        Vec3d diff = targetVec.subtract(botEyePos);
        double dx = diff.x;
        double dy = diff.y;
        double dz = diff.z;

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(-dx, dz) * (180 / Math.PI));
        float pitch = (float) (-Math.atan2(dy, distanceXZ) * (180 / Math.PI));

        bot.setYaw(yaw);
        bot.setPitch(pitch);

        System.out.printf("Facing block at %s with Yaw: %.2f Pitch: %.2f%n", targetPos, yaw, pitch);

        return "Facing block at " + targetPos + " with Yaw: " + yaw + " and Pitch: " + pitch;
    }

    public static void faceEntity(ServerPlayerEntity bot, Entity target) {
        Vec3d botPos = bot.getPos();
        Vec3d targetPos = target.getPos();
        Vec3d direction = targetPos.subtract(botPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        double pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

        bot.setYaw((float) yaw);
        bot.setPitch((float) pitch);

        System.out.printf("Facing entity %s at Yaw: %.2f Pitch: %.2f%n", target.getName().getString(), yaw, pitch);
    }
}

