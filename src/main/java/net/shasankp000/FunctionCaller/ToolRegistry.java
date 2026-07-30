package net.shasankp000.FunctionCaller;

import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ToolRegistry {

    public static final List<Tool> TOOLS = List.of(

            new Tool(
                    "goTo",
                    """
                    Uses the path finder and path tracer to navigate to a given x y z coordinate.
                    Stops within ~3 blocks of the target due to inertia-based carpet bot control.
                    """,
                    List.of(
                            new Tool.Parameter("x", "X coordinate to go to."),
                            new Tool.Parameter("y", "Y coordinate to go to."),
                            new Tool.Parameter("z", "Z coordinate to go to."),
                            new Tool.Parameter("sprint", "Boolean flag for sprint mode.")
                    ),
                    Set.of("lastTarget.x", "lastTarget.y", "lastTarget.z"),
                    (sharedState, paramMap, result) -> {
                        sharedState.put("lastTarget.x", paramMap.get("x"));
                        sharedState.put("lastTarget.y", paramMap.get("y"));
                        sharedState.put("lastTarget.z", paramMap.get("z"));
                    }
            ),

            new Tool(
                    "detectBlocks",
                    """
                    Raycasts to find the first block of the given type in front of the bot.
                    If found, stores its coordinates in shared state for chaining.
                    """,
                    List.of(
                            new Tool.Parameter("blockType", "Target block's name.")
                    ),
                    Set.of("lastDetectedBlock.x", "lastDetectedBlock.y", "lastDetectedBlock.z"),
                    (sharedState, paramMap, result) -> {
                        if (result instanceof BlockPos pos) {
                            sharedState.put("lastDetectedBlock.x", pos.getX());
                            sharedState.put("lastDetectedBlock.y", pos.getY());
                            sharedState.put("lastDetectedBlock.z", pos.getZ());
                        }
                    }
            ),

            new Tool(
                    "turn",
                    "Turns the bot to face a new direction based on the parameters (a side of the bot). Unlike the look method, this method only rotates the bot to face 3 of it's sides, i.e the left, right or it's back.",
                    List.of(
                            new Tool.Parameter("direction", "Direction to turn. Valid: 'left', 'right', 'back'.")
                    ),
                    Set.of("facing.direction", "facing.facing", "facing.axis"),
                    (sharedState, paramMap, result) -> {
                        if (result instanceof String output) {
                            Matcher matcher = Pattern.compile("Now facing (\\\\w+) which is in (\\\\w+).*in (\\\\w+) axis", Pattern.CASE_INSENSITIVE).matcher(output);
                            if (matcher.find()) {
                                sharedState.put("facing.direction", matcher.group(1));
                                sharedState.put("facing.facing", matcher.group(2));
                                sharedState.put("facing.axis", matcher.group(3));
                            }
                        }
                    }
            ),

            new Tool(
                    "look",
                    """
                    Rotates the bot to look in a specified cardinal direction (north, south, east, west).
                    """,
                    List.of(
                            new Tool.Parameter("cardinalDirection", "Direction to look. Valid: 'north', 'south', 'east', 'west'.")
                    ),
                    Set.of("facing.direction", "facing.facing", "facing.axis"),
                    (sharedState, paramMap, result) -> {
                        if (result instanceof String output) {
                            Matcher matcher = Pattern.compile("Now facing (\\w+) which is in (\\w+).*in (\\w+) axis", Pattern.CASE_INSENSITIVE).matcher(output);
                            if (matcher.find()) {
                                sharedState.put("facing.direction", matcher.group(1));
                                sharedState.put("facing.facing", matcher.group(2));
                                sharedState.put("facing.axis", matcher.group(3));
                            }
                        }
                    }
            ),


            new Tool(
                    "mineBlock",
                    """
                    Mines the block at the given coordinates.
                    """,
                    List.of(
                            new Tool.Parameter("targetX", "Target block X coordinate."),
                            new Tool.Parameter("targetY", "Target block Y coordinate."),
                            new Tool.Parameter("targetZ", "Target block Z coordinate.")
                    ),
                    Set.of("lastMinedBlock.x", "lastMinedBlock.y", "lastMinedBlock.z"),
                    (sharedState, paramMap, result) -> {
                        sharedState.put("lastMinedBlock.x", paramMap.get("targetX"));
                        sharedState.put("lastMinedBlock.y", paramMap.get("targetY"));
                        sharedState.put("lastMinedBlock.z", paramMap.get("targetZ"));
                    }
            ),

            new Tool(
                    "getOxygenLevel",
                    """
                    Retrieves the bot's current oxygen (air) level.
                    """,
                    List.of(
                            new Tool.Parameter("None", "No parameters needed.")
                    ),
                    Set.of("bot.oxygenLevel"),
                    (sharedState, paramMap, result) -> {
                        if (result instanceof Number level) {
                            sharedState.put("bot.oxygenLevel", level);
                        } else if (result instanceof String s) {
                            sharedState.put("bot.oxygenLevel", s); // fallback
                        }
                    }
            ),

            // Add other tools below in same pattern...

            new Tool(
                    "getHungerLevel",
                    """
                    Gets the bot's hunger level.
                    """,
                    List.of(
                            new Tool.Parameter("None", "No parameters needed.")
                    ),
                    Set.of("bot.hungerLevel"),
                    (sharedState, paramMap, result) -> {
                        if (result instanceof Number level) {
                            sharedState.put("bot.hungerLevel", level);
                        } else if (result instanceof String s) {
                            sharedState.put("bot.hungerLevel", s);
                        }
                    }
            ),

            new Tool(
                    "getHealthLevel",
                    """
                    Gets the bot's health level (hearts).
                    """,
                    List.of(
                            new Tool.Parameter("None", "No parameters needed.")
                    ),
                    Set.of("bot.healthLevel"),
                    (sharedState, paramMap, result) -> {
                        if (result instanceof Number level) {
                            sharedState.put("bot.healthLevel", level);
                        } else if (result instanceof String s) {
                            sharedState.put("bot.healthLevel", s);
                        }
                    }
            ),

            new Tool(
                "webSearch",
                    """
                    Searches the web for the input query via an automatically pre-configured provider. Meant to be used as a standalone method and not in a pipeline.
                    """,
                    List.of(
                            new Tool.Parameter("Query", "You need to understand the user's input and put in a search query accordingly in here.")
                    ),
                    Set.of("webSearchQuery.result"),
                    ((sharedState, paramMap, searchResult) -> {
                        if (searchResult instanceof String result) {
                            sharedState.put("webSearchQuery", result);
                        }
                    })
            ),

            new Tool(
                    "placeBlock",
                    """
                    Places a block of the specified type at the given x, y, z coordinates.
                    The bot must have the block in its inventory and must be within 5 blocks of the target position.
                    Automatically handles inventory management (moves block to hotbar if needed).
                    Verifies that the target position is empty and finds a suitable adjacent block to place against.
                    """,
                    List.of(
                            new Tool.Parameter("targetX", "X coordinate where the block should be placed."),
                            new Tool.Parameter("targetY", "Y coordinate where the block should be placed."),
                            new Tool.Parameter("targetZ", "Z coordinate where the block should be placed."),
                            new Tool.Parameter("blockType", "Type of block to place (e.g., 'stone', 'dirt', 'minecraft:oak_planks').")
                    ),
                    Set.of("lastPlacedBlock.x", "lastPlacedBlock.y", "lastPlacedBlock.z", "lastPlacedBlock.type"),
                    (sharedState, paramMap, result) -> {
                        sharedState.put("lastPlacedBlock.x", paramMap.get("targetX"));
                        sharedState.put("lastPlacedBlock.y", paramMap.get("targetY"));
                        sharedState.put("lastPlacedBlock.z", paramMap.get("targetZ"));
                        sharedState.put("lastPlacedBlock.type", paramMap.get("blockType"));
                    }
            ),

            new Tool(
                    "searchBlocks",
                    """
                    Efficiently searches for blocks in an expanding radius around the bot.
                    Uses incremental search shells to avoid lag. Caches searched positions to prevent re-scanning.
                    Returns the nearest matching block's coordinates, which can be used with goTo and mineBlock.
                    """,
                    List.of(
                            new Tool.Parameter("blockType", "Target block type (e.g., 'oak_log', 'minecraft:iron_ore')."),
                            new Tool.Parameter("initialRadius", "Starting search radius in blocks (e.g., 10)."),
                            new Tool.Parameter("maxRadius", "Maximum search radius in blocks (e.g., 100)."),
                            new Tool.Parameter("radiusIncrement", "How much to expand each iteration (e.g., 20).")
                    ),
                    Set.of("foundBlock.x", "foundBlock.y", "foundBlock.z", "foundBlock.type"),
                    (sharedState, paramMap, result) -> {
                        if (result instanceof BlockPos pos) {
                            sharedState.put("foundBlock.x", pos.getX());
                            sharedState.put("foundBlock.y", pos.getY());
                            sharedState.put("foundBlock.z", pos.getZ());
                            sharedState.put("foundBlock.type", paramMap.get("blockType"));
                        }
                    }
            )
    );

}
