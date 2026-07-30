package net.shasankp000.PlayerUtils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class blockDetectionUnit {
    private static final Logger logger = LoggerFactory.getLogger("block-detection-unit");

    private static boolean isBlockDetectionActive = false;

    public static boolean getBlockDetectionStatus() {
        return isBlockDetectionActive;
    }

    public static void setIsBlockDetectionActive(boolean value) {
        isBlockDetectionActive = value;
    }



    /**
     * Detect a block in the bot's facing direction, but only return it if it matches the given blockType.
     * @param bot The bot/player entity.
     * @param blockType The block type to detect (e.g., "minecraft:oak_log"). Use Minecraft's registry IDs.
     * @return BlockPos of the matching block if found, otherwise null.
     */
    public static BlockPos detectBlocks(ServerPlayerEntity bot, String blockType) {
        String normalized = BlockNameNormalizer.normalizeBlockName(blockType);
        logger.info("Normalized block name: {} → {}", blockType, normalized);

        Vec3d botPosition = bot.getPos();
        Direction getDirection = bot.getHorizontalFacing();
        Vec3d botDirection = Vec3d.of(getDirection.getVector());
        double rayLength = 15.0;
        Vec3d rayEnd = botPosition.add(botDirection.multiply(rayLength));
        BlockPos outputBlockpos = null;

        RaycastContext raycastContext = new RaycastContext(
                botPosition,
                rayEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY,
                bot
        );

        BlockHitResult hitResult = bot.getWorld().raycast(raycastContext);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hitResult.getBlockPos();
            BlockState hitBlockState = bot.getWorld().getBlockState(hitPos);
            Block hitBlock = hitBlockState.getBlock();
            Identifier hitBlockId = Registries.BLOCK.getId(hitBlock);

            System.out.println("Raycast hit block: " + hitBlockId);

            if (hitBlockId.toString().equals(normalized)) {
                System.out.println("Block type matches: " + normalized);
                outputBlockpos = hitPos;
                setIsBlockDetectionActive(true);
            } else {
                System.out.println("Block type does not match. Expected: " + normalized + ", Found: " + hitBlockId);
            }
        } else if (hitResult.getType() == HitResult.Type.MISS) {
            System.out.println("Nothing detected in front by raycast");
        }

        return outputBlockpos;
    }
}
