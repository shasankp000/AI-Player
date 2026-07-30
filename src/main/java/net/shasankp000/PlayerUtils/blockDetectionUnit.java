package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    public static BlockPos detectBlocks(ServerPlayer bot, String blockType) {
        String normalized = BlockNameNormalizer.normalizeBlockName(blockType);
        logger.info("Normalized block name: {} → {}", blockType, normalized);

        Vec3 botPosition = bot.position();
        Direction getDirection = bot.getDirection();
        Vec3 botDirection = Vec3.atLowerCornerOf(getDirection.getUnitVec3i());
        double rayLength = 15.0;
        Vec3 rayEnd = botPosition.add(botDirection.scale(rayLength));
        BlockPos outputBlockpos = null;

        ClipContext raycastContext = new ClipContext(
                botPosition,
                rayEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY,
                bot
        );

        BlockHitResult hitResult = bot.level().clip(raycastContext);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hitResult.getBlockPos();
            BlockState hitBlockState = bot.level().getBlockState(hitPos);
            Block hitBlock = hitBlockState.getBlock();
            Identifier hitBlockId = BuiltInRegistries.BLOCK.getKey(hitBlock);

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
