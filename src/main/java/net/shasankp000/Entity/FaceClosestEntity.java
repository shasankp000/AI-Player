package net.shasankp000.Entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class FaceClosestEntity {

    public static void faceClosestEntity(ServerPlayerEntity bot, List<Entity> entities) {
        if (entities.isEmpty()) {
            return;
        }

        Entity closestEntity = null;
        double closestDistance = Double.MAX_VALUE;

        // Find the closest entity
        for (Entity entity : entities) {
            double distance = bot.squaredDistanceTo(entity);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEntity = entity;
            }
        }

        if (closestEntity != null) {
            // Calculate the direction to the closest entity
            Vec3d botPos = bot.getPos();
            Vec3d entityPos = closestEntity.getPos();
            Vec3d direction = entityPos.subtract(botPos).normalize();

            // Calculate yaw and pitch
            double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
            double pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

            // Set the bot's rotation
            bot.setYaw((float) yaw);
            bot.setPitch((float) pitch);

        }
    }
}
