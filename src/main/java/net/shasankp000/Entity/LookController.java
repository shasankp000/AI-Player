package net.shasankp000.Entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class LookController {

    public static String faceBlock(ServerPlayer bot, BlockPos targetPos) {
        Vec3 botEyePos = bot.getEyePosition();
        Vec3 targetVec = new Vec3(
                targetPos.getX() + 0.5,
                targetPos.getY() + 0.5,
                targetPos.getZ() + 0.5
        );

        Vec3 diff = targetVec.subtract(botEyePos);
        double dx = diff.x;
        double dy = diff.y;
        double dz = diff.z;

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(-dx, dz) * (180 / Math.PI));
        float pitch = (float) (-Math.atan2(dy, distanceXZ) * (180 / Math.PI));

        bot.setYRot(yaw);
        bot.setXRot(pitch);

        System.out.printf("Facing block at %s with Yaw: %.2f Pitch: %.2f%n", targetPos, yaw, pitch);

        return "Facing block at " + targetPos + " with Yaw: " + yaw + " and Pitch: " + pitch;
    }

    public static void faceEntity(ServerPlayer bot, Entity target) {
        Vec3 botPos = bot.position();
        Vec3 targetPos = target.position();
        Vec3 direction = targetPos.subtract(botPos).normalize();

        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        double pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

        bot.setYRot((float) yaw);
        bot.setXRot((float) pitch);

        System.out.printf("Facing entity %s at Yaw: %.2f Pitch: %.2f%n", target.getName().getString(), yaw, pitch);
    }
}

