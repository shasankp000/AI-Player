package net.shasankp000.FunctionCaller;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * State-based verifier class. Verifies tool outputs by checking sharedState directly,
 * without parsing strings. Each verifier is a lambda that takes params, sharedState, and bot entity.
 */
public class ToolVerifiers {

    // Result class for verifiers: success flag + extracted/calculated data
    public static class VerificationResult {
        public final boolean success;
        public final Map<String, Object> data;

        public VerificationResult(boolean success, Map<String, Object> data) {
            this.success = success;
            this.data = data != null ? data : new HashMap<>();
        }
    }

    // Functional interface for verifiers
    @FunctionalInterface
    public interface StateVerifier {
        VerificationResult verify(Map<String, String> params, Map<String, Object> sharedState, ServerPlayerEntity bot);
    }

    // Registry of verifiers per function (extend as needed)
    public static final Map<String, StateVerifier> VERIFIER_REGISTRY = Map.of(
            "goTo", (params, state, bot) -> {
                Object xObj = state.get("botPosition.x");
                Object yObj = state.get("botPosition.y");
                Object zObj = state.get("botPosition.z");
                if (!(xObj instanceof Number) || !(yObj instanceof Number) || !(zObj instanceof Number)) {
                    return new VerificationResult(false, Map.of("error", "Missing or invalid position in state"));
                }

                double actualX = ((Number) xObj).doubleValue();
                double actualY = ((Number) yObj).doubleValue();
                double actualZ = ((Number) zObj).doubleValue();

                double targetX = Double.parseDouble(params.getOrDefault("x", "0"));
                double targetY = Double.parseDouble(params.getOrDefault("y", "0"));
                double targetZ = Double.parseDouble(params.getOrDefault("z", "0"));

                double distSq = Math.pow(actualX - targetX, 2) + Math.pow(actualY - targetY, 2) + Math.pow(actualZ - targetZ, 2);
                boolean success = distSq <= 16.0;  // Tolerance for ~4 blocks, accounting for overshoot

                // Optional cross-check with bot's actual position
                if (bot != null) {
                    BlockPos botPos = bot.getBlockPos();
                    double botDistSq = Math.pow(botPos.getX() - targetX, 2) + Math.pow(botPos.getY() - targetY, 2) + Math.pow(botPos.getZ() - targetZ, 2);
                    success = success && botDistSq <= 16.0;
                }

                Map<String, Object> data = new HashMap<>();
                data.put("actual", Map.of("x", actualX, "y", actualY, "z", actualZ));
                data.put("distSq", distSq);
                return new VerificationResult(success, data);
            },
            "detectBlocks", (params, state, bot) -> {
                Object x = state.get("lastDetectedBlock.x");
                Object y = state.get("lastDetectedBlock.y");
                Object z = state.get("lastDetectedBlock.z");
                boolean success = x != null && y != null && z != null;
                Map<String, Object> data = success ? Map.of("coords", Map.of("x", x, "y", y, "z", z)) : null;
                return new VerificationResult(success, data);
            },
            "turn", (params, state, bot) -> {
                String actualDirection = (String) state.get("facing.direction");
                String expected = params.getOrDefault("direction", "").toLowerCase();
                boolean success = actualDirection != null && actualDirection.toLowerCase().contains(expected);
                return new VerificationResult(success, Map.of("actualDirection", actualDirection != null ? actualDirection : "unknown"));
            },
            "mineBlock", (params, state, bot) -> {
                String status = (String) state.get("lastMineStatus");
                boolean success = status != null && status.equalsIgnoreCase("success");
                return new VerificationResult(success, Map.of("status", status != null ? status : "unknown"));
            },
            "getOxygenLevel", (params, state, bot) -> {
                Object levelObj = state.get("bot.oxygenLevel");  // Based on your original code
                if (!(levelObj instanceof Number)) return new VerificationResult(false, Map.of("error", "Missing or invalid oxygen level"));
                double level = ((Number) levelObj).doubleValue();
                boolean success = level >= 0;
                // Optional cross-check with bot
                if (bot != null) success = success && bot.getAir() >= 0;
                return new VerificationResult(success, Map.of("level", level));
            },
            "getHungerLevel", (params, state, bot) -> {
                Object levelObj = state.get("bot.hungerLevel");
                if (!(levelObj instanceof Number)) return new VerificationResult(false, Map.of("error", "Missing or invalid hunger level"));
                double level = ((Number) levelObj).doubleValue();
                boolean success = level >= 0;
                if (bot != null) success = success && bot.getHungerManager().getFoodLevel() >= 0;
                return new VerificationResult(success, Map.of("level", level));
            },
            "getHealthLevel", (params, state, bot) -> {
                Object levelObj = state.get("bot.healthLevel");
                if (!(levelObj instanceof Number)) return new VerificationResult(false, Map.of("error", "Missing or invalid health level"));
                double level = ((Number) levelObj).doubleValue();
                boolean success = level >= 0;
                if (bot != null) success = success && bot.getHealth() >= 0;
                return new VerificationResult(success, Map.of("level", level));
            },
            "placeBlock", (params, state, bot) -> {
                Object xObj = state.get("lastPlacedBlock.x");
                Object yObj = state.get("lastPlacedBlock.y");
                Object zObj = state.get("lastPlacedBlock.z");
                String blockType = (String) state.get("lastPlacedBlock.type");

                if (!(xObj instanceof Number) || !(yObj instanceof Number) || !(zObj instanceof Number)) {
                    return new VerificationResult(false, Map.of("error", "Missing or invalid placement position in state"));
                }

                int targetX = ((Number) xObj).intValue();
                int targetY = ((Number) yObj).intValue();
                int targetZ = ((Number) zObj).intValue();

                // Cross-check with bot's world if available
                boolean success = true;
                if (bot != null) {
                    BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
                    var blockState = bot.getWorld().getBlockState(targetPos);
                    // Verify block is not air (successfully placed)
                    success = !blockState.isAir();
                }

                Map<String, Object> data = new HashMap<>();
                data.put("coords", Map.of("x", targetX, "y", targetY, "z", targetZ));
                data.put("blockType", blockType != null ? blockType : "unknown");
                return new VerificationResult(success, data);
            }
    );
}
