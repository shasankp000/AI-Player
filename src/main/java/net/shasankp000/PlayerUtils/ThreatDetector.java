package net.shasankp000.PlayerUtils;

import net.shasankp000.Entity.EntityDetails;
import net.shasankp000.GameAI.State;

import java.util.List;

public class ThreatDetector {

    public static double calculateThreatLevel(State currentState) {
        double threatLevel = 0.0;

        List<EntityDetails> nearbyEntities = currentState.getNearbyEntities();

        List<EntityDetails> hostileEntities = nearbyEntities.stream()
                .filter(EntityDetails::isHostile)
                .toList();

        // Example threat calculations
        int numberOfHostiles = hostileEntities.size();
        double closestHostileDistance = currentState.getDistanceToHostileEntity();
        String currentDimension = currentState.getDimensionType();

        // Add threat based on number of hostile entities
        threatLevel += Math.min(1.0, numberOfHostiles / 10.0); // Cap at 1.0 for 10+ hostiles

        // Add threat based on proximity to the closest hostile
        if (closestHostileDistance < 5.0) {
            threatLevel += 0.5; // Immediate danger
        } else if (closestHostileDistance < 15.0) {
            threatLevel += 0.3; // Moderate danger
        }

        // Add threat based on biome
        if (currentDimension.contains("Nether") || currentDimension.contains("nether") || currentDimension.contains("End") || currentDimension.contains("end")) {
            threatLevel += 0.4; // Dangerous biome
        }

        // Clamp threat level between 0 and 1
        return Math.min(1.0, threatLevel);
    }


}
