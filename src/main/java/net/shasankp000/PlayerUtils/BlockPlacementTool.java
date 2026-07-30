package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.Entity.LookController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class BlockPlacementTool {

    private static final Logger LOGGER = LoggerFactory.getLogger("block-placement-tool");
    private static final double MAX_PLACEMENT_DISTANCE = 5.0;

    /**
     * Places a block at the specified coordinates.
     *
     * @param bot The bot entity
     * @param targetPos The position where the block should be placed
     * @param blockType The type of block to place (e.g., "minecraft:stone", "stone", "dirt")
     * @return CompletableFuture with result message
     */
    public static CompletableFuture<String> placeBlock(ServerPlayer bot, BlockPos targetPos, String blockType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Normalize block type (add minecraft: prefix if missing)
                String normalizedBlockType = normalizeBlockType(blockType);
                LOGGER.info("Attempting to place {} at {}", normalizedBlockType, targetPos);

                // Step 2: Check if bot is within placement range
                Vec3 botPos = bot.position();
                double distance = Math.sqrt(targetPos.distToCenterSqr(botPos));
                if (distance > MAX_PLACEMENT_DISTANCE) {
                    String error = String.format("❌ Too far from target position! Distance: %.2f blocks (max: %.2f)",
                            distance, MAX_PLACEMENT_DISTANCE);
                    LOGGER.warn(error);
                    return error;
                }

                // Step 3: Find the block item in inventory
                ItemStack blockItem = findBlockInInventory(bot, normalizedBlockType);
                if (blockItem == null || blockItem.isEmpty()) {
                    String error = "❌ Block not found in inventory: " + normalizedBlockType;
                    LOGGER.warn(error);
                    return error;
                }

                // Step 4: Switch to the block in hotbar (or move it to hotbar)
                int hotbarSlot = ensureBlockInHotbar(bot, normalizedBlockType);
                if (hotbarSlot == -1) {
                    String error = "❌ Could not move block to hotbar: " + normalizedBlockType;
                    LOGGER.warn(error);
                    return error;
                }

                // Switch to that hotbar slot
                bot.getInventory().setSelectedSlot(hotbarSlot);
                LOGGER.info("Switched to hotbar slot {} with {}", hotbarSlot, normalizedBlockType);

                // Step 5: Check if target position is valid for placement
                Level world = bot.level();
                BlockState targetState = world.getBlockState(targetPos);
                if (!targetState.isAir() && !targetState.canBeReplaced()) {
                    String error = "❌ Target position is already occupied by: " + targetState.getBlock().getName().getString();
                    LOGGER.warn(error);
                    return error;
                }

                // Step 6: Find a suitable face to place against
                Direction placementDirection = findPlacementDirection(world, targetPos);
                if (placementDirection == null) {
                    String error = "❌ No suitable surface found to place block against at " + targetPos;
                    LOGGER.warn(error);
                    return error;
                }

                BlockPos adjacentPos = targetPos.relative(placementDirection);

                // Step 7: Look at the target position
                LookController.faceBlock(bot, adjacentPos);

                // Step 8: Perform block placement
                Vec3 hitVec = Vec3.atCenterOf(adjacentPos).add(
                        placementDirection.getStepX() * 0.5,
                        placementDirection.getStepY() * 0.5,
                        placementDirection.getStepZ() * 0.5
                );

                BlockHitResult hitResult = new BlockHitResult(
                        hitVec,
                        placementDirection.getOpposite(),
                        adjacentPos,
                        false
                );

                // Use the block item
                ItemStack handStack = bot.getItemInHand(InteractionHand.MAIN_HAND);
                if (handStack.getItem() instanceof BlockItem) {
                    bot.gameMode.useItemOn(bot, world, handStack, InteractionHand.MAIN_HAND, hitResult);

                    // Verify placement
                    BlockState placedState = world.getBlockState(targetPos);
                    if (!placedState.isAir()) {
                        String success = String.format("✅ Successfully placed %s at x:%d y:%d z:%d",
                                normalizedBlockType, targetPos.getX(), targetPos.getY(), targetPos.getZ());
                        LOGGER.info(success);
                        return success;
                    } else {
                        String error = "⚠️ Block placement appeared to succeed but block is not present at target position";
                        LOGGER.warn(error);
                        return error;
                    }
                } else {
                    String error = "❌ Selected item is not a valid block item";
                    LOGGER.error(error);
                    return error;
                }

            } catch (Exception e) {
                String error = "❌ Failed to place block: " + e.getMessage();
                LOGGER.error(error, e);
                return error;
            }
        });
    }

    /**
     * Normalizes block type name by adding "minecraft:" prefix if missing
     */
    private static String normalizeBlockType(String blockType) {
        if (blockType == null || blockType.isEmpty()) {
            return "minecraft:stone";
        }
        return blockType.contains(":") ? blockType : "minecraft:" + blockType;
    }

    /**
     * Finds a block item in the bot's inventory
     */
    private static ItemStack findBlockInInventory(ServerPlayer bot, String blockType) {
        Identifier blockId = Identifier.tryParse(blockType);
        if (blockId == null) {
            LOGGER.warn("Invalid block type format: {}", blockType);
            return null;
        }
        Block targetBlock = BuiltInRegistries.BLOCK.getValue(blockId);

        // Search entire inventory (0-35 for player inventory, 0-8 for hotbar)
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == targetBlock) {
                    return stack;
                }
            }
        }
        return null;
    }

    /**
     * Ensures the block is in the hotbar, returns the hotbar slot index
     * If already in hotbar, returns that slot. Otherwise, tries to move it to an empty slot.
     */
    private static int ensureBlockInHotbar(ServerPlayer bot, String blockType) {
        Identifier blockId = Identifier.tryParse(blockType);
        if (blockId == null) {
            LOGGER.warn("Invalid block type format: {}", blockType);
            return -1;
        }
        Block targetBlock = BuiltInRegistries.BLOCK.getValue(blockId);

        // Check if already in hotbar (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == targetBlock) {
                    return i;
                }
            }
        }

        // Find the stack in main inventory (slots 9-35)
        int inventorySlot = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == targetBlock) {
                    inventorySlot = i;
                    break;
                }
            }
        }

        if (inventorySlot == -1) {
            LOGGER.warn("Block not found in inventory: {}", blockType);
            return -1;
        }

        // Find an empty hotbar slot
        int emptyHotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (bot.getInventory().getItem(i).isEmpty()) {
                emptyHotbarSlot = i;
                break;
            }
        }

        if (emptyHotbarSlot == -1) {
            // No empty hotbar slot, use slot 8 as fallback (replace whatever is there)
            emptyHotbarSlot = 8;
            LOGGER.info("No empty hotbar slot found, using slot 8 as fallback");
        }

        // Move the stack from inventory to hotbar
        ItemStack stackToMove = bot.getInventory().getItem(inventorySlot);
        bot.getInventory().setItem(emptyHotbarSlot, stackToMove.copy());
        bot.getInventory().setItem(inventorySlot, ItemStack.EMPTY);

        LOGGER.info("Moved {} from inventory slot {} to hotbar slot {}", blockType, inventorySlot, emptyHotbarSlot);
        return emptyHotbarSlot;
    }

    /**
     * Finds a suitable direction to place a block against an adjacent block
     * Returns the direction from the target position to the adjacent block
     */
    private static Direction findPlacementDirection(Level world, BlockPos targetPos) {
        // Check all 6 directions for a solid block to place against
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = targetPos.relative(direction);
            BlockState adjacentState = world.getBlockState(adjacentPos);

            // Check if the adjacent block is solid (can be placed against)
            if (!adjacentState.isAir() && adjacentState.isRedstoneConductor(world, adjacentPos)) {
                return direction;
            }
        }
        return null;
    }
}

