package net.shasankp000.Entity;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public class FaceClosestEntity {

    public static void faceClosestEntity(ServerPlayer bot, List<Entity> entities) {
        if (entities.isEmpty()) {
            return;
        }

        Entity closestEntity = null;
        double closestDistance = Double.MAX_VALUE;

        // Find the closest entity
        for (Entity entity : entities) {
            double distance = bot.distanceToSqr(entity);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEntity = entity;
            }
        }

        if (closestEntity != null) {
            // Calculate the direction to the closest entity
            Vec3 botPos = bot.position();
            Vec3 entityPos = closestEntity.position();
            Vec3 direction = entityPos.subtract(botPos).normalize();

            // Calculate yaw and pitch
            double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
            double pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

            // Set the bot's rotation
            bot.setYRot((float) yaw);
            bot.setXRot((float) pitch);

        }
    }

    /**
     * Face a specific projectile entity - used for defense (blocking/tracking)
     */
    public static void faceProjectile(ServerPlayer bot, Projectile projectile) {
        if (projectile == null || !projectile.isAlive()) {
            return;
        }

        // Calculate the direction to the projectile
        Vec3 botPos = bot.position().add(0, bot.getEyeHeight(), 0); // Account for eye height
        Vec3 projectilePos = projectile.position();
        Vec3 direction = projectilePos.subtract(botPos).normalize();

        // Calculate yaw and pitch
        double yaw = Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
        double pitch = Math.toDegrees(-Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));

        // Set the bot's rotation to face the projectile
        bot.setYRot((float) yaw);
        bot.setXRot((float) pitch);
    }
}
