package net.shasankp000.Commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.DangerZoneDetector.DangerZoneDetector;
import net.shasankp000.Database.QTableExporter;
import net.shasankp000.Entity.*;
import net.shasankp000.FilingSystem.LLMClientFactory;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.OllamaClient.ollamaClient;
import net.shasankp000.PathFinding.BotStance;
import net.shasankp000.PathFinding.ChartPathToBlock;
import net.shasankp000.PathFinding.GoTo;
import net.shasankp000.PathFinding.PathFinder;
import net.shasankp000.PathFinding.PathTracer;
import net.shasankp000.PathFinding.Segment;
import net.shasankp000.PathFinding.StanceController;
import net.shasankp000.PlayerUtils.*;
import net.shasankp000.ServiceLLMClients.LLMClient;
import net.shasankp000.ServiceLLMClients.LLMServiceHandler;
import net.shasankp000.WorldUitls.isFoodItem;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shasankp000.GameAI.handoff.TradeEvaluator;
import net.shasankp000.GameAI.handoff.TradeListener;
import net.shasankp000.GameAI.mood.AffectiveState;
import net.shasankp000.GameAI.mood.MoodEngine;
import net.shasankp000.GameAI.mood.MoodLabel;
import net.shasankp000.GameAI.persona.PersonaRegistry;
import net.shasankp000.GameAI.persona.PersonaTemplate;
import java.util.stream.Collectors;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import static net.shasankp000.PathFinding.PathFinder.*;
import static net.minecraft.server.command.CommandManager.literal;
import net.shasankp000.PacketHandler.InputPacketHandler;

public class modCommandRegistry {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static boolean isTrainingMode = false;
    public static String botName = "";
    public static final Logger LOGGER = LoggerFactory.getLogger("mod-command-registry");


    public record BotStopTask(MinecraftServer server, ServerCommandSource botSource,
                                  String botName) implements Runnable {

        @Override
        public void run() {

            stopMoving(server, botSource, botName);
            LOGGER.info("{} has stopped walking!", botName);


        }
    }


    public static void register() {
        // Register threat debug command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ThreatDebugCommand.register(dispatcher);
        });

        // Start the StanceController tick loop once at registration time.
        StanceController.start();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("bot")
                        .then(literal("spawn")
                                .then(CommandManager.argument("bot_name", StringArgumentType.string())
                                        .then(CommandManager.argument("mode", StringArgumentType.string())
                                                .executes(context -> {

                                                    String spawnMode = StringArgumentType.getString(context, "mode");

                                                    spawnBot(context, spawnMode);


                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("walk")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("till", IntegerArgumentType.integer())
                                                .executes(context -> { botWalk(context); return 1; })
                                        )
                                )
                        )
                        .then(literal("jump")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { botJump(context); return 1; })
                                )
                        )
                        .then(literal("teleport_forward")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { teleportForward(context); return 1; })
                                )
                        )
                        .then(literal("test_chat_message")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> { testChatMessage(context); return 1; })
                                )
                        )
                        .then(literal("go_to")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .then(CommandManager.argument("sprint", StringArgumentType.string())
                                                        .executes(context -> { botGo(context); return 1; })
                                                )
                                        )
                                )
                        )
                        // ----------------------------------------------------------------
                        // /bot stance <bot> <stay|follow|cancel> [targetPlayerName]
                        //
                        // stay   — bot holds its current position; auto-corrects if pushed
                        // follow — bot shadows <targetPlayerName>; re-paths when they move
                        // cancel — removes any active stance and stops ongoing path
                        // ----------------------------------------------------------------
                        .then(literal("stance")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("mode", StringArgumentType.string())
                                                // variant with optional target player name (for follow)
                                                .then(CommandManager.argument("target", StringArgumentType.string())
                                                        .executes(context -> {
                                                            botStance(context, true);
                                                            return 1;
                                                        })
                                                )
                                                // variant without target (stay / cancel)
                                                .executes(context -> {
                                                    botStance(context, false);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("send_message_to")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                                .executes(context -> {

                                                    ollamaClient.execute(context);

                                                     return 1;

                                                })
                                        )
                                )
                        )
                        .then(literal("detect_entities")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            if (bot != null) {
                                                RayCasting.detect(bot);
                                            }
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("get_block_map")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("vertical", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("horizontal", IntegerArgumentType.integer())
                                                        .executes(context -> {
                                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                            int y = IntegerArgumentType.getInteger(context, "vertical");
                                                            int x = IntegerArgumentType.getInteger(context, "horizontal");

                                                            InternalMap internalMap = new InternalMap(bot, y, x);
                                                            internalMap.updateMap();
                                                            internalMap.printMap();
                                                            return 1;
                                                        })
                                                )
                                        )

                                )

                        )
                        .then(literal("start_autoface")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            LOGGER.info("Manually starting autoface for bot: {}", bot.getName().getString());
                                            AutoFaceEntity.startAutoFace(bot);
                                            ChatUtils.sendSystemMessage(context.getSource(), "AutoFace started for " + bot.getName().getString());
                                            return 1;
                                        })
                                )
                        )

                        .then(literal("detect_blocks")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("block_type", StringArgumentType.string())
                                                .executes(context -> {

                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String blockType = StringArgumentType.getString(context, "block_type");

                                                    BlockPos outPutPos = blockDetectionUnit.detectBlocks(bot, blockType);

                                                    LOGGER.info("Detected Block: {} at x={}, y={}, z={}", blockType, outPutPos.getX(), outPutPos.getY(), outPutPos.getZ());
                                                    blockDetectionUnit.setIsBlockDetectionActive(false);

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("turn")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("direction", StringArgumentType.string())
                                                .executes(context -> {

                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    MinecraftServer server = bot.getServer();
                                                    assert server != null;
                                                    String direction = StringArgumentType.getString(context, "direction");

                                                    switch (direction) {
                                                        case "left", "right", "back" -> {
                                                            turnTool.turn(bot.getCommandSource().withSilent().withMaxLevel(4), direction);

                                                            LOGGER.info("Now facing {} which is in {} in {} axis", direction, bot.getFacing().getName(), bot.getFacing().getAxis().asString());
                                                        }
                                                        default -> {
                                                            server.execute(() -> {
                                                                ChatUtils.sendChatMessages(bot.getCommandSource().withSilent().withMaxLevel(4), "Invalid parameters! Accepted parameters: left, right, back only!");
                                                            });
                                                        }
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                        )


                        .then(literal("chart_path_to_block")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("block_type", StringArgumentType.string())
                                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                                        .executes(context -> {

                                                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                                            MinecraftServer server = bot.getServer();
                                                                            assert server != null;
                                                                            String blockType = StringArgumentType.getString(context, "block_type");
                                                                            int x = IntegerArgumentType.getInteger(context, "x");
                                                                            int y = IntegerArgumentType.getInteger(context, "y");
                                                                            int z = IntegerArgumentType.getInteger(context, "z");

                                                                            BlockPos targetPos = new BlockPos(x, y, z);

                                                                            ChartPathToBlock.chart(bot, targetPos, blockType);

                                                                            return 1;
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )

                        .then(literal("shoot_arrow")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("debug", StringArgumentType.string())
                                                .executes(context -> {

                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String debugMode = StringArgumentType.getString(context, "debug");
                                                    MinecraftServer server = bot.getServer();
                                                    assert server != null;
                                                    ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                                    // Check if bot can shoot (checks entire inventory now, not just equipped)
                                                    if (!RangedWeaponUtils.hasBowOrCrossbow(bot)) {
                                                        LOGGER.info("Bot does not have a bow or crossbow to shoot with.");
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, "I don't have a bow or crossbow!");
                                                        }
                                                        return 0;
                                                    }

                                                    // Prepare ammo first (move items to correct slots if needed)
                                                    String ammoType;
                                                    if (RangedWeaponUtils.hasCrossbow(bot)) {
                                                        ammoType = RangedWeaponUtils.prepareCrossbowAmmo(bot, server, botSource);
                                                        LOGGER.info("Prepared crossbow with ammo: {}", ammoType);
                                                    } else if (RangedWeaponUtils.hasBow(bot)) {
                                                        ammoType = RangedWeaponUtils.prepareBowAmmo(bot, server, botSource);
                                                        LOGGER.info("Prepared bow with ammo: {}", ammoType);
                                                    } else {
                                                        ammoType = RangedWeaponUtils.getAmmoType(bot);
                                                    }

                                                    if (ammoType == null) {
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, "I don't have any arrows or firework rockets!");
                                                        }

                                                        return 0;
                                                    }

                                                    // Detect nearby hostile entities AND hostile players within 40 blocks
                                                    List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 40);
                                                    List<Entity> hostileEntities = nearbyEntities.stream()
                                                            .filter(entity -> {
                                                                // Include hostile mobs
                                                                if (entity instanceof HostileEntity) {
                                                                    return true;
                                                                }
                                                                // Include hostile players (tracked by retaliation system)
                                                                if (entity instanceof net.minecraft.entity.player.PlayerEntity player &&
                                                                    !player.getUuid().equals(bot.getUuid())) {
                                                                    return net.shasankp000.PlayerUtils.PlayerRetaliationTracker.isPlayerHostile(bot, player);
                                                                }
                                                                return false;
                                                            })
                                                            .toList();

                                                    if (hostileEntities.isEmpty()) {
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, "No hostile entities or players detected within 40 blocks!");
                                                        }

                                                        return 0;
                                                    }

                                                    // Intelligent targeting: use risk analysis to prioritize threats
                                                    Entity target = selectHighestThreatTarget(bot, hostileEntities, debugMode.equals("true"), botSource);

                                                    if (target == null) {
                                                        ChatUtils.sendChatMessages(botSource, "No suitable target found for shooting!");
                                                        return 0;
                                                    }

                                                    double distance = bot.getPos().distanceTo(target.getPos());
                                                    String targetName = target.getName().getString();

                                                    if (debugMode.equals("true")) {
                                                        ChatUtils.sendChatMessages(botSource, "Target acquired: " + targetName + " at " + String.format("%.1f", distance) + " blocks");
                                                    }

                                                    // Pause autoface to prevent interruption
                                                    AutoFaceEntity.isShooting = true;
                                                    LOGGER.info("Paused autoface for shooting");

                                                    // Switch to bow/crossbow if not already equipped
                                                    int weaponSlot = RangedWeaponUtils.getBowOrCrossbowSlot(bot);
                                                    if (weaponSlot != -1 && bot.getInventory().selectedSlot != (weaponSlot - 1)) {
                                                        server.getCommandManager().executeWithPrefix(botSource, "/player " + bot.getName().getString() + " hotbar " + weaponSlot);
                                                        LOGGER.info("Switched to ranged weapon in slot {}", weaponSlot);
                                                        try {
                                                            Thread.sleep(100);
                                                        } catch (InterruptedException e) {
                                                            LOGGER.error("Weapon switch interrupted");
                                                        }
                                                    }

                                                    double projectileSpeed = ammoType.equals("firework") ? 1.5 : 3.0;
                                                    String ammoTypeName = ammoType.equals("firework") ? "firework rocket" : "arrow";

                                                    boolean isMovingFast = RangedWeaponUtils.isTargetMovingFast(target);

                                                    Vec3d aimPosition;
                                                    if (isMovingFast) {
                                                        aimPosition = RangedWeaponUtils.calculateLeadPosition(target, projectileSpeed);
                                                        LOGGER.info("Applied lead compensation for fast-moving target");
                                                    } else {
                                                        aimPosition = target.getPos().add(0, target.getHeight() * 0.6, 0);
                                                    }

                                                    float[] aimAngles = RangedWeaponUtils.calculateAimAngles(bot, aimPosition);
                                                    bot.setYaw(aimAngles[0]);
                                                    bot.setPitch(aimAngles[1]);

                                                    LOGGER.info("Aiming at {} at distance {} using {} (ballistic trajectory)",
                                                        targetName, String.format("%.1f", distance), ammoTypeName);

                                                    String weaponName = bot.getMainHandStack().getItem().getName().getString().toLowerCase();
                                                    boolean isCrossbow = weaponName.contains("crossbow");

                                                    int drawTime;
                                                    if (isCrossbow) {
                                                        drawTime = 25;
                                                    } else {
                                                        drawTime = net.shasankp000.PlayerUtils.WeaponUtils.calculateOptimalDrawTime(distance);
                                                        projectileSpeed = net.shasankp000.PlayerUtils.WeaponUtils.calculateProjectileSpeed(drawTime);
                                                        LOGGER.info("Dynamic bow draw time: {} ticks for {}m (speed: {})",
                                                            drawTime, String.format("%.1f", distance), String.format("%.2f", projectileSpeed));
                                                    }

                                                    AutoFaceEntity.setShootingTarget(target);

                                                    if (debugMode.equals("true")) {
                                                        ChatUtils.sendChatMessages(botSource, "Shooting target set to " + targetName);
                                                        ChatUtils.sendChatMessages(botSource, "Drawing " + (isCrossbow ? "crossbow" : "bow") + " with " + ammoTypeName + "...");
                                                    }

                                                    String playerName = bot.getName().getString();
                                                    server.getCommandManager().executeWithPrefix(botSource, "/player " + playerName + " use continuous");
                                                    LOGGER.info("Started drawing weapon");

                                                    Entity finalTarget = target;
                                                    String finalAmmoType = ammoType;
                                                    double finalProjectileSpeed = projectileSpeed;
                                                    scheduler.schedule(() -> {
                                                        if (finalTarget.isAlive()) {
                                                            Vec3d finalAimPosition;
                                                            if (isMovingFast) {
                                                                finalAimPosition = RangedWeaponUtils.calculateLeadPosition(finalTarget, finalProjectileSpeed);
                                                            } else {
                                                                finalAimPosition = finalTarget.getPos().add(0, finalTarget.getHeight() * 0.6, 0);
                                                            }
                                                            float[] reAimAngles = RangedWeaponUtils.calculateAimAngles(bot, finalAimPosition);
                                                            bot.setYaw(reAimAngles[0]);
                                                            bot.setPitch(reAimAngles[1]);
                                                        }

                                                        server.getCommandManager().executeWithPrefix(botSource, "/player " + playerName + " use");
                                                        if (debugMode.equals("true")) {
                                                            ChatUtils.sendChatMessages(botSource, ammoTypeName.substring(0, 1).toUpperCase() + ammoTypeName.substring(1) + " released at " + targetName + "!");
                                                        }
                                                        LOGGER.info("Shot {} at {} from {} blocks away", ammoTypeName, targetName, distance);

                                                        scheduler.schedule(() -> {
                                                            AutoFaceEntity.clearShootingTarget();
                                                            if (debugMode.equals("true")) {
                                                                ChatUtils.sendChatMessages(botSource, "Shot complete");
                                                            }
                                                            LOGGER.info("Shooting complete, autoface resumed");
                                                            BotEventHandler.completeAction(playerName);
                                                        }, 1000, TimeUnit.MILLISECONDS);

                                                    }, drawTime * 50, TimeUnit.MILLISECONDS);

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("reset_autoface")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            MinecraftServer server = bot.getServer();
                                            assert server != null;
                                            blockDetectionUnit.setIsBlockDetectionActive(false);
                                            PathTracer.flushAllMovementTasks();
                                            AutoFaceEntity.setBotExecutingTask(false);
                                            AutoFaceEntity.isBotMoving = false;

                                            server.execute(() -> {
                                                ChatUtils.sendChatMessages(bot.getCommandSource().withSilent().withMaxLevel(4), "Autoface module reset complete.");
                                            });

                                            return 1;
                                        })

                                )
                        )

                        .then(literal("plan")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("goal", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String goal = StringArgumentType.getString(context, "goal");
                                                    MinecraftServer server = bot.getServer();
                                                    assert server != null;
                                                    ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                                    net.shasankp000.GameAI.RLAgent rlAgent = BotEventHandler.getRLAgent();
                                                    if (rlAgent == null) {
                                                        LOGGER.error("RLAgent not initialized for bot {}", bot.getName().getString());
                                                        ChatUtils.sendChatMessages(botSource, "Error: AI system not ready");
                                                        return 0;
                                                    }

                                                    net.shasankp000.FunctionCaller.FunctionCallerV2.initializePlanner(bot, rlAgent);

                                                    net.shasankp000.GameAI.State currentState = BotEventHandler.getCurrentState();

                                                    ChatUtils.sendChatMessages(botSource, "Planning: " + goal + "...");

                                                    net.shasankp000.FunctionCaller.FunctionCallerV2.handleUserGoal(goal, currentState, bot, rlAgent, botSource)
                                                            .thenAccept(success -> {
                                                                server.execute(() -> {
                                                                    if (success) {
                                                                        ChatUtils.sendChatMessages(botSource, "✓ Plan executed successfully!");
                                                                        LOGGER.info("[planner] ✓ Goal '{}' completed", goal);
                                                                    } else {
                                                                        ChatUtils.sendChatMessages(botSource, "✗ Plan execution failed");
                                                                        LOGGER.warn("[planner] ✗ Goal '{}' failed", goal);
                                                                    }
                                                                });
                                                            })
                                                            .exceptionally(ex -> {
                                                                server.execute(() -> {
                                                                    ChatUtils.sendChatMessages(botSource, "Error: " + ex.getMessage());
                                                                    LOGGER.error("[planner] Exception during goal execution", ex);
                                                                });
                                                                return null;
                                                            });

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("mine_block")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("block_type", StringArgumentType.string())
                                                .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                                                        .executes(context -> {

                                                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                                            int x = IntegerArgumentType.getInteger(context, "x");
                                                                            int y = IntegerArgumentType.getInteger(context, "y");
                                                                            int z = IntegerArgumentType.getInteger(context, "z");
                                                                            MiningTool.mineBlock(bot, new BlockPos(x, y, z));

                                                                            return 1;
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )


                        .then(literal("use-key")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("key", StringArgumentType.string())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();

                                                    ServerCommandSource serverSource = server.getCommandSource();
                                                    String inputKey = StringArgumentType.getString(context, "key");

                                                    switch (inputKey) {
                                                        case "W":
                                                            InputPacketHandler.manualPacketPressWKey(context);
                                                            break;
                                                        case "S":
                                                            InputPacketHandler.manualPacketPressSKey(context);
                                                            break;
                                                        case "A":
                                                            InputPacketHandler.manualPacketPressAKey(context);
                                                            break;
                                                        case "D":
                                                            InputPacketHandler.manualPacketPressDKey(context);
                                                            break;
                                                        case "Sneak":
                                                            InputPacketHandler.manualPacketSneak(context);
                                                            break;
                                                        case "LSHIFT":
                                                            InputPacketHandler.manualPacketSneak(context);
                                                            break;
                                                        case "Sprint":
                                                            InputPacketHandler.manualPacketSprint(context);
                                                            break;
                                                        default:
                                                            ChatUtils.sendSystemMessage(serverSource, "This key is not registered.");
                                                            break;
                                                    }

                                                    return 1;
                                                })
                                        )

                                )
                        )

                        .then(literal("look")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("bot_name", StringArgumentType.string())
                                                .then(CommandManager.argument("direction", StringArgumentType.string())
                                                        .executes(context -> {

                                                            MinecraftServer server = context.getSource().getServer();

                                                            ServerCommandSource serverSource = server.getCommandSource();

                                                            String botName = StringArgumentType.getString(context, "bot_name");

                                                            ServerPlayerEntity bot = context.getSource().getServer().getPlayerManager().getPlayer(botName);

                                                            String direction = StringArgumentType.getString(context, "direction");

                                                            switch (direction) {

                                                                case("north"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.NORTH);
                                                                    break;

                                                                case("south"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.SOUTH);
                                                                    break;

                                                                case("east"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.EAST);
                                                                    break;

                                                                case("west"):
                                                                    InputPacketHandler.BotLookController.lookInDirection(bot, Direction.WEST);
                                                                    break;

                                                                default:
                                                                    ChatUtils.sendSystemMessage(serverSource, "Invalid direction.");
                                                                    break;
                                                            }

                                                            return 1;
                                                        })

                                                )
                                        )

                                )

                        )

                        .then(literal("release-all-keys")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("bot_name", StringArgumentType.string())
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();

                                                    ServerCommandSource serverSource = server.getCommandSource();

                                                    String botName = StringArgumentType.getString(context, "bot_name");

                                                    InputPacketHandler.manualPacketReleaseMovementKey(context);

                                                    ChatUtils.sendSystemMessage(serverSource, "Released all movement keys for bot: " + botName);

                                                    return 1;
                                                })
                                        )

                                )
                        )

                        .then(literal("detectDangerZone")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .then(CommandManager.argument("lavaRange", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("cliffRange", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("cliffDepth", IntegerArgumentType.integer())
                                                                .executes(context -> {

                                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                                    ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);
                                                                    MinecraftServer server = botSource.getServer();

                                                                    int lavaRange = IntegerArgumentType.getInteger(context, "lavaRange");
                                                                    int cliffRange = IntegerArgumentType.getInteger(context, "cliffRange");
                                                                    int cliffDepth = IntegerArgumentType.getInteger(context, "cliffDepth");

                                                                    server.execute(() -> {
                                                                        double dangerDistance = DangerZoneDetector.detectDangerZone(bot, lavaRange, cliffRange, cliffDepth);
                                                                        if (dangerDistance > 0) {
                                                                            System.out.println("Danger detected! Effective distance: " + dangerDistance);
                                                                            ChatUtils.sendChatMessages(botSource, "Danger detected! Effective distance to danger: " + (int) dangerDistance + " blocks");
                                                                        } else {
                                                                            System.out.println("No danger nearby.");
                                                                            ChatUtils.sendChatMessages(botSource, "No danger nearby");
                                                                        }
                                                                    });

                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )


                        .then(literal("getHotbarItems")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            List<ItemStack> hotbarItems = hotBarUtils.getHotbarItems(bot);

                                            StringBuilder messageBuilder = new StringBuilder();

                                            for (int i = 0; i < hotbarItems.size(); i++) {
                                                int slotIndex = i;

                                                ItemStack itemStack = hotbarItems.get(slotIndex);

                                                if (itemStack.isEmpty()) {
                                                    messageBuilder.append("Slot ").append(i+1).append(": EMPTY\n");
                                                } else {
                                                    messageBuilder.append("Slot ").append(i+1).append(": ")
                                                            .append(itemStack.getName().getString())
                                                            .append(" (Count: ").append(itemStack.getCount()).append(")\n");
                                                }
                                            }

                                            String finalMessage = messageBuilder.toString();

                                            ChatUtils.sendChatMessages(botSource, finalMessage);


                                            return 1;
                                        })
                                )

                        )

                        .then(literal("getSelectedItem")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            String selectedItem = hotBarUtils.getSelectedHotbarItemStack(bot).getItem().getName().getString();

                                            ChatUtils.sendChatMessages(botSource, "Currently selected item: " + selectedItem);

                                            return 1;
                                        })

                                )

                        )

                        .then(literal("getHungerLevel")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            int botHungerLevel = getPlayerHunger.getBotHungerLevel(bot);

                                            ChatUtils.sendChatMessages(botSource, "Hunger level: " + botHungerLevel);

                                            return 1;

                                        })
                                )
                        )

                        .then(literal("getOxygenLevel")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            int botHungerLevel = getPlayerOxygen.getBotOxygenLevel(bot);

                                            ChatUtils.sendChatMessages(botSource, "Oxygen level: " + botHungerLevel);

                                            return 1;
                                        })
                                )
                        )
                        .then(literal("getHealth")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            int botHealthLevel = (int) bot.getHealth();

                                            ChatUtils.sendChatMessages(botSource, "Health level: " + botHealthLevel);

                                            return 1;
                                        })
                                )
                        )

                        .then(literal("isFoodItem")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

                                            ItemStack selectedItemStack = hotBarUtils.getSelectedHotbarItemStack(bot);

                                            if (isFoodItem.checkFoodItem(selectedItemStack)) {

                                                ChatUtils.sendChatMessages(botSource, "Currently selected item: " + selectedItemStack.getItem().getName().getString() + " is a food item.");

                                            }

                                            else {

                                                ChatUtils.sendChatMessages(botSource, "Currently selected item: " + selectedItemStack.getItem().getName().getString() + " is not a food item.");

                                            }

                                            return 1;
                                        })
                                )
                        )


                        .then(literal("equipArmor")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            armorUtils.autoEquipArmor(bot);

                                            return 1;
                                        })

                                )
                        )
                        .then(literal("removeArmor")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {

                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");

                                            armorUtils.autoDeEquipArmor(bot);

                                            return 1;
                                        })

                                )
                        )

                        .then(literal("exportQTableToJSON")
                                .executes(context -> {

                                    MinecraftServer server = context.getSource().getServer();
                                    ServerCommandSource serverSource = server.getCommandSource();

                                    ChatUtils.sendSystemMessage(serverSource, "Exporting Q-table to JSON. Please wait.... ");

                                    QTableExporter.exportQTable(BotEventHandler.qTableDir + "/qtable.bin", BotEventHandler.qTableDir + "./fullQTable.json");

                                    ChatUtils.sendSystemMessage(serverSource, "Q-table has been successfully exported to a json file at: " + BotEventHandler.qTableDir + "./fullQTable.json" );

                                    return 1;
                                })
                        )

                        // Feature 2: /bot mood
                        .then(literal("mood")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            String snapshot = MoodEngine.getStatusSnapshot(bot.getName().getString());
                                            ChatUtils.sendSystemMessage(context.getSource(),
                                                    "[" + bot.getName().getString() + "] mood: " + snapshot);
                                            return 1;
                                        })
                                        .then(CommandManager.argument("mood_label", StringArgumentType.string())
                                                .executes(context -> {
                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String botName = bot.getName().getString();
                                                    String labelStr = StringArgumentType.getString(context, "mood_label").toUpperCase();
                                                    MoodLabel label;
                                                    try {
                                                        label = MoodLabel.valueOf(labelStr);
                                                    } catch (IllegalArgumentException e) {
                                                        String valid = Arrays.stream(MoodLabel.values())
                                                                .map(Enum::name)
                                                                .collect(Collectors.joining(", "));
                                                        ChatUtils.sendSystemMessage(context.getSource(),
                                                                "Unknown mood label. Valid: " + valid);
                                                        return 0;
                                                    }
                                                    float valence = switch (label) {
                                                        case ELATED, CONTENT, SERENE -> 0.8f;
                                                        case EXCITED, CALM, NEUTRAL -> 0.0f;
                                                        case AGITATED, BORED, DEPRESSED -> -0.8f;
                                                    };
                                                    float arousal = switch (label) {
                                                        case ELATED, EXCITED, AGITATED -> 0.9f;
                                                        case CONTENT, NEUTRAL, BORED -> 0.5f;
                                                        case SERENE, CALM, DEPRESSED -> 0.1f;
                                                    };
                                                    MoodEngine.set(botName, new AffectiveState(valence, arousal));
                                                    ChatUtils.sendSystemMessage(context.getSource(),
                                                            "[" + botName + "] mood set to: " + label.name());
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // Feature 3: /bot persona
                        .then(literal("persona")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            String botName = bot.getName().getString();
                                            String all = PersonaRegistry.ids().stream()
                                                    .collect(Collectors.joining(", "));
                                            ChatUtils.sendSystemMessage(context.getSource(),
                                                    "[" + botName + "] available personas: " + all);
                                            return 1;
                                        })
                                        .then(CommandManager.argument("persona_id", StringArgumentType.string())
                                                .executes(context -> {
                                                    ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                                    String botName = bot.getName().getString();
                                                    String personaId = StringArgumentType.getString(context, "persona_id");
                                                    Optional<PersonaTemplate> opt = PersonaRegistry.get(personaId);
                                                    if (opt.isEmpty()) {
                                                        String all = PersonaRegistry.ids().stream()
                                                                .collect(Collectors.joining(", "));
                                                        ChatUtils.sendSystemMessage(context.getSource(),
                                                                "Unknown persona id. Valid: " + all);
                                                        return 0;
                                                    }
                                                    PersonaTemplate template = opt.get();
                                                    PersonaRegistry.setActive(botName, personaId);
                                                    ChatUtils.sendSystemMessage(context.getSource(),
                                                            "[" + botName + "] persona set to: " + template.displayName());
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // Feature 4: /bot trade
                        .then(literal("trade")
                                .then(CommandManager.argument("bot", EntityArgumentType.player())
                                        .executes(context -> {
                                            ServerPlayerEntity bot = EntityArgumentType.getPlayer(context, "bot");
                                            ServerCommandSource src = context.getSource();
                                            ServerPlayerEntity player;
                                            try {
                                                player = src.getPlayerOrThrow();
                                            } catch (CommandSyntaxException e) {
                                                ChatUtils.sendSystemMessage(src, "This command must be run by a player.");
                                                return 0;
                                            }
                                            ItemStack botOffer = TradeEvaluator.evaluate(ItemStack.EMPTY, bot);
                                            if (botOffer.isEmpty()) {
                                                player.sendMessage(
                                                        Text.literal("§e[" + bot.getName().getString()
                                                                + "] §fI have nothing worth trading right now."),
                                                        false);
                                                return 0;
                                            }
                                            player.sendMessage(
                                                    Text.literal("§e[" + bot.getName().getString()
                                                            + "] §fI could offer: §b"
                                                            + TradeEvaluator.displayName(botOffer)
                                                            + "§f. Sneak and throw the item you want to give me!"),
                                                    false);
                                            return 1;
                                        })
                                        .then(literal("cancel")
                                                .executes(context -> {
                                                    ServerCommandSource src = context.getSource();
                                                    ServerPlayerEntity player;
                                                    try {
                                                        player = src.getPlayerOrThrow();
                                                    } catch (CommandSyntaxException e) {
                                                        ChatUtils.sendSystemMessage(src, "Must be run by a player.");
                                                        return 0;
                                                    }
                                                    TradeListener.cancelSession(player.getUuid());
                                                    ChatUtils.sendSystemMessage(src, "Trade session cancelled.");
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("stopAllMovementTasks")
                                .executes(context -> {

                                    MinecraftServer server = context.getSource().getServer();
                                    ServerCommandSource serverSource = server.getCommandSource();
                                    PathTracer.flushAllMovementTasks();

                                    ChatUtils.sendSystemMessage(serverSource, "Flushed all movement tasks");

                                    return 1;

                                })
                        )
        ));
    }


    // =========================================================================
    // /bot stance handler
    // =========================================================================

    /**
     * Handles /bot stance <bot> <mode> [target].
     *
     * <p>Modes:
     * <ul>
     *   <li><b>stay</b>   — records the bot's current block position as anchor.
     *       StanceController will re-path back whenever drift > 2.5 blocks.</li>
     *   <li><b>follow</b> — requires a {@code target} player name argument.
     *       StanceController will re-path toward the target whenever they
     *       move > 5 blocks from the last path origin.</li>
     *   <li><b>cancel</b> — clears the stance, flushes in-flight movement.</li>
     * </ul>
     *
     * @param context   the command context
     * @param hasTarget true when the optional {@code target} argument was supplied
     */
    private static void botStance(CommandContext<ServerCommandSource> context, boolean hasTarget) {
        ServerPlayerEntity bot;
        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException e) {
            context.getSource().sendMessage(Text.of("Bot not found!"));
            return;
        }

        String mode = StringArgumentType.getString(context, "mode").toLowerCase();
        String botName = bot.getName().getString();
        ServerCommandSource cmdSource = context.getSource();

        switch (mode) {
            case "stay" -> {
                BlockPos anchor = bot.getBlockPos();
                BotStance.setStay(botName, anchor);
                ChatUtils.sendSystemMessage(cmdSource,
                        botName + " is now in STAY mode. Anchor: " + anchor);
                LOGGER.info("[stance] {} -> STAY at {}", botName, anchor);
            }

            case "follow" -> {
                if (!hasTarget) {
                    ChatUtils.sendSystemMessage(cmdSource,
                            "Usage: /bot stance <bot> follow <targetPlayerName>");
                    return;
                }
                String target = StringArgumentType.getString(context, "target");
                // Validate target exists on the server
                MinecraftServer server = cmdSource.getServer();
                if (server.getPlayerManager().getPlayer(target) == null) {
                    ChatUtils.sendSystemMessage(cmdSource,
                            "Player '" + target + "' not found on this server.");
                    return;
                }
                BotStance.setFollow(botName, target);
                ChatUtils.sendSystemMessage(cmdSource,
                        botName + " is now in FOLLOW mode, shadowing '" + target + "'.");
                LOGGER.info("[stance] {} -> FOLLOW target='{}'", botName, target);
            }

            case "cancel" -> {
                StanceController.cancelStance(botName);
                ChatUtils.sendSystemMessage(cmdSource,
                        botName + "'s stance has been cancelled.");
                LOGGER.info("[stance] {} -> NONE (cancelled)", botName);
            }

            default -> ChatUtils.sendSystemMessage(cmdSource,
                    "Unknown stance mode '" + mode + "'. Use: stay | follow | cancel");
        }
    }


    private static void spawnBot(CommandContext<ServerCommandSource> context, String spawnMode) {
        LOGGER.info("========== SPAWNING BOT IN MODE: {} ==========", spawnMode);

        MinecraftServer server = context.getSource().getServer();
        BlockPos spawnPos = getBlockPos(context);

        RegistryKey<World> dimType = context.getSource().getWorld().getRegistryKey();

        Vec2f facing = context.getSource().getRotation();

        Vec3d pos = new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

        GameMode mode = GameMode.SURVIVAL;

        botName = StringArgumentType.getString(context, "bot_name");

        ServerCommandSource serverSource = server.getCommandSource();


        if (spawnMode.equals("training")) {

            createFakePlayer.createFake(
                    botName,
                    server,
                    pos,
                    facing.y,
                    facing.x,
                    dimType,
                    mode,
                    false
            );

            isTrainingMode = true;

            LOGGER.info("Spawned new bot {}!", botName);

            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

            if (bot!=null) {

                Objects.requireNonNull(bot.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE)).setBaseValue(0.0);

                RespawnHandler.registerRespawnListener(bot);

                AutoFaceEntity.startAutoFace(bot);

            }

            else {
                ChatUtils.sendSystemMessage(serverSource, "Error: " + botName + " cannot be spawned");
            }

        } else if (spawnMode.equals("play")) {

            createFakePlayer.createFake(
                    botName,
                    server,
                    pos,
                    facing.y,
                    facing.x,
                    dimType,
                    mode,
                    false
            );

            LOGGER.info("Spawned new bot {}!", botName);

            ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

            System.out.println("Preparing for connection to language model....");

            if (bot!=null) {

                Objects.requireNonNull(bot.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE)).setBaseValue(0.0);

                System.out.println("Registering respawn listener....");

                RespawnHandler.registerRespawnListener(bot);

                ollamaClient.botName = botName;

                System.out.println("Set bot's username to " + botName);

                String llmProvider = System.getProperty("aiplayer.llmMode", "ollama");

                System.out.println("Using provider");

                switch (llmProvider) {
                    case "openai", "gpt", "google", "gemini", "anthropic", "claude", "xAI", "xai", "grok", "custom":
                        LLMClient llmClient = LLMClientFactory.createClient(llmProvider);
                        assert llmClient != null;

                        ChatUtils.sendSystemMessage(serverSource, "Please wait while " + botName + " connects to " + llmClient.getProvider() + "'s servers.");
                        LLMServiceHandler.sendInitialResponse(bot.getCommandSource().withSilent().withMaxLevel(4), llmClient);

                        new Thread(() -> {
                            LOGGER.info("Waiting for LLM Service Handler to initialize...");
                            int waitCount = 0;
                            while (!LLMServiceHandler.isInitialized) {
                                try {
                                    Thread.sleep(500L);
                                    waitCount++;
                                    if (waitCount % 10 == 0) {
                                        LOGGER.warn("Still waiting for LLM initialization... ({} seconds)", waitCount / 2);
                                    }
                                    if (waitCount > 60) {
                                        LOGGER.error("LLM initialization timeout! Starting AutoFace anyway.");
                                        AutoFaceEntity.startAutoFace(bot);
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                } catch (InterruptedException e) {
                                    LOGGER.error("LLM client initialization interrupted.");
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }

                            LOGGER.info("LLM Service Handler initialized! Starting AutoFace...");
                            AutoFaceEntity.startAutoFace(bot);

                            Thread.currentThread().interrupt();

                        }).start();

                        break;

                    case "ollama":
                        ChatUtils.sendSystemMessage(serverSource, "Please wait while " + botName + " connects to the language model.");
                        ollamaClient.initializeOllamaClient();

                        new Thread(() -> {
                            LOGGER.info("Waiting for Ollama client to initialize...");
                            int waitCount = 0;
                            while (!ollamaClient.isInitialized) {
                                try {
                                    Thread.sleep(500L);
                                    waitCount++;
                                    if (waitCount % 10 == 0) {
                                        LOGGER.warn("Still waiting for Ollama initialization... ({} seconds)", waitCount / 2);
                                    }
                                    if (waitCount > 60) {
                                        LOGGER.error("Ollama initialization timeout! Starting AutoFace anyway.");
                                        AutoFaceEntity.startAutoFace(bot);
                                        Thread.currentThread().interrupt();
                                        return;
                                    }
                                } catch (InterruptedException e) {
                                    LOGGER.error("Ollama client initialization interrupted.");
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }

                            LOGGER.info("Ollama client initialized! Starting AutoFace...");
                            ollamaClient.sendInitialResponse(bot.getCommandSource().withSilent().withMaxLevel(4));
                            AutoFaceEntity.startAutoFace(bot);

                            Thread.currentThread().interrupt();

                        }).start();

                        break;

                    default:
                        LOGGER.warn("Unsupported provider detected. Defaulting to Ollama client");
                        ChatUtils.sendSystemMessage(serverSource, "Warning! Unsupported provider detected. Defaulting to Ollama client");
                        ChatUtils.sendSystemMessage(serverSource, "Please wait while " + botName + " connects to the language model.");
                        ollamaClient.initializeOllamaClient();

                        new Thread(() -> {
                            while (!ollamaClient.isInitialized) {
                                try {
                                    Thread.sleep(500L);
                                } catch (InterruptedException e) {
                                    LOGGER.error("Ollama client initialization interrupted.");
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }

                            ollamaClient.sendInitialResponse(bot.getCommandSource().withSilent().withMaxLevel(4));
                            AutoFaceEntity.startAutoFace(bot);

                            Thread.currentThread().interrupt();

                        }).start();

                        break;

                }

            }


            else {
                ChatUtils.sendSystemMessage(serverSource, "Error: " + botName + " cannot be spawned");
            }

        }
        else {
            ChatUtils.sendSystemMessage(serverSource, "Invalid spawn mode!");
            ChatUtils.sendSystemMessage(serverSource, "Usage: /bot spawn <your bot's name> <spawnMode: training or play>");
        }


    }

    private static void notImplementedMessage(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        String botName = StringArgumentType.getString(context, "bot_name");

        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botName);

        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);

            server.getCommandManager().executeWithPrefix(botSource, "/say \u00a7cThis command has not been implemented yet and is a work in progress! ");


        }


    }

    private static void teleportForward(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {
            String botName = bot.getName().getLiteralString();

            BlockPos currentPosition = bot.getBlockPos();
            BlockPos newPosition = currentPosition.add(1, 0, 0); // Move one block forward
            bot.teleport(bot.getServerWorld(), (double)newPosition.getX(), (double)newPosition.getY(), (double)newPosition.getZ(), java.util.Collections.emptySet(), bot.getYaw(), bot.getPitch(), true);

            LOGGER.info("Teleported {} 1 positive block ahead", botName);

        }

    }

    private static void botWalk(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        int travelTime = IntegerArgumentType.getInteger(context, "till");


        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            String botName = bot.getName().getLiteralString();

            ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);
            moveForward(server, botSource, botName);

            scheduler.schedule(new BotStopTask(server, botSource, botName), travelTime, TimeUnit.SECONDS);


        }

    }


    private static void botJump(CommandContext<ServerCommandSource> context) {

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}


        if (bot == null) {

            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

        else {

            String botName = bot.getName().getLiteralString();

            bot.jump();

            LOGGER.info("{} jumped!", botName);

        }

    }

    private static void testChatMessage(CommandContext<ServerCommandSource> context) {

        String response = "I am doing great! It feels good to be able to chat with you again after a long time. So, how have you been doing? Are you enjoying the game world and having fun playing Minecraft with me? Let's continue chatting about whatever topic comes to mind! I love hearing from you guys and seeing your creations in the game. Don't hesitate to share anything with me, whether it's an idea, a problem, or simply something that makes you laugh. Cheers!";

        MinecraftServer server = context.getSource().getServer();

        ServerPlayerEntity bot = null;
        try {bot = EntityArgumentType.getPlayer(context, "bot");} catch (CommandSyntaxException ignored) {}

        if (bot != null) {

            ServerCommandSource botSource = bot.getCommandSource().withMaxLevel(4).withSilent();
            ChatUtils.sendChatMessages(botSource, response);

        }
        else {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");

        }

    }

    private static void botGo(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();
        BlockPos position = BlockPosArgumentType.getBlockPos(context, "pos");
        String sprintFlag = StringArgumentType.getString(context, "sprint");

        boolean sprint;

        if (sprintFlag.equalsIgnoreCase("true")) {
            sprint = true;
        }
        else if (sprintFlag.equalsIgnoreCase("false")) {
            sprint = false;
        }
        else {
            sprint = false;
            ChatUtils.sendChatMessages(server.getCommandSource(), "Wrong argument! Command is as follows: /bot go_to <botName> <xyz> <true/false (case insensitive)>");
        }

        int x_distance = position.getX();
        int y_distance = position.getY();
        int z_distance = position.getZ();

        ServerWorld world = server.getOverworld();

        ServerPlayerEntity bot = null;
        try {
            bot = EntityArgumentType.getPlayer(context, "bot");
        } catch (CommandSyntaxException ignored) {}

        if (bot == null) {
            context.getSource().sendMessage(Text.of("The requested bot could not be found on the server!"));
            server.sendMessage(Text.literal("Error! Bot not found!"));
            LOGGER.error("The requested bot could not be found on the server!");
            return;
        }

        String botName = bot.getName().getLiteralString();
        ServerCommandSource botSource = bot.getCommandSource().withLevel(2).withSilent().withMaxLevel(4);

        server.sendMessage(Text.literal("Finding the shortest path to the target, please wait patiently if the game seems hung"));

        ServerPlayerEntity finalBot = bot;

        server.execute(() -> {
            List<PathFinder.PathNode> rawPath = PathFinder.calculatePath(finalBot.getBlockPos(), new BlockPos(x_distance, y_distance, z_distance), world);

            List<PathFinder.PathNode> finalPath = PathFinder.simplifyPath(rawPath, world);

            LOGGER.info("Path output: {}", finalPath);

            Queue<Segment> segments = convertPathToSegments(finalPath, sprint);

            LOGGER.info("Generated segments: {}", segments);

            PathTracer.tracePath(server, botSource, botName, segments, sprint);

        });
    }




    public static void moveForward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move forward");

        }

    }

    private static void moveBackward(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move backward");

        }


    }

    public static void stopMoving(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " stop");

        }


    }

    private static void moveLeft(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move left");

        }

    }

    private static void moveRight(MinecraftServer server, ServerCommandSource source, String botName) {

        if (source.getPlayer() != null) {

            server.getCommandManager().executeWithPrefix(source, "/player " + botName + " move right");

        }

    }


    private static @NotNull BlockPos getBlockPos(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();


        assert player != null;
        return new BlockPos((int) player.getX() + 5, (int) player.getY(), (int) player.getZ());
    }

    /**
     * Intelligently selects the highest threat target from a list of hostile entities.
     */
    private static Entity selectHighestThreatTarget(ServerPlayerEntity bot, List<Entity> hostileEntities, boolean debugMode, ServerCommandSource botSource) {
        if (hostileEntities.isEmpty()) {
            return null;
        }

        Entity highestThreatEntity = null;
        double highestThreat = -1.0;

        for (Entity entity : hostileEntities) {
            double distance = Math.sqrt(entity.squaredDistanceTo(bot));

            double baseThreat;
            if (entity instanceof net.minecraft.entity.player.PlayerEntity player) {
                baseThreat = net.shasankp000.PlayerUtils.PlayerRetaliationTracker.getPlayerThreatLevel(bot, player);
            } else {
                baseThreat = calculateBaseThreatForEntity(entity, distance);
            }

            double distanceModifier = 0.0;
            String entityType = entity.getName().getString().toLowerCase();

            if (entityType.contains("creeper")) {
                if (distance < 3.0) distanceModifier = 50.0;
                else if (distance < 5.0) distanceModifier = 30.0;
                else if (distance < 8.0) distanceModifier = 10.0;
                else distanceModifier = -10.0;
            } else if (entityType.contains("skeleton") || entityType.contains("witch") ||
                     entityType.contains("blaze") || entityType.contains("pillager") ||
                     (entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                if (distance < 3.0) distanceModifier = 5.0;
                else if (distance < 8.0) distanceModifier = 10.0;
                else if (distance < 15.0) distanceModifier = 5.0;
                else if (distance < 25.0) distanceModifier = 0.0;
                else distanceModifier = -8.0;
            } else {
                if (distance < 2.0) distanceModifier = 15.0;
                else if (distance < 4.0) distanceModifier = 10.0;
                else if (distance < 6.0) distanceModifier = 5.0;
                else if (distance < 10.0) distanceModifier = 0.0;
                else if (distance < 20.0) distanceModifier = -5.0;
                else distanceModifier = -10.0;
            }

            double totalThreat = baseThreat + distanceModifier;

            if (debugMode) {
                String entityCategory = entity instanceof net.minecraft.entity.player.PlayerEntity ? "HOSTILE PLAYER" : "MOB";
                LOGGER.info("Target analysis: {} ({}) at {}m - Base: {}, Distance modifier: {}, Total: {}",
                    entity.getName().getString(), entityCategory,
                    String.format("%.1f", distance), String.format("%.1f", baseThreat),
                    String.format("%.1f", distanceModifier), String.format("%.1f", totalThreat));
            }

            if (totalThreat > highestThreat) {
                highestThreat = totalThreat;
                highestThreatEntity = entity;
            }
        }

        if (highestThreatEntity != null && debugMode) {
            String targetName = highestThreatEntity.getName().getString();
            double distance = Math.sqrt(highestThreatEntity.squaredDistanceTo(bot));

            ChatUtils.sendChatMessages(botSource,
                String.format("\u00a7c\u2694 Priority Target: \u00a7e%s \u00a77(Threat: \u00a7c%.1f\u00a77, Distance: \u00a7e%.1fm\u00a77)",
                    targetName, highestThreat, distance));

            if (hostileEntities.size() > 1) {
                String reason = getTargetSelectionReason(highestThreatEntity, distance);
                ChatUtils.sendChatMessages(botSource, "\u00a77Reason: " + reason);
            }
        }

        return highestThreatEntity;
    }

    private static double calculateBaseThreatForEntity(Entity entity, double distance) {
        String entityType = entity.getName().getString().toLowerCase();
        double baseThreat = 5.0;

        if (entityType.contains("creeper")) {
            baseThreat = 50.0;
            if (distance <= 3.0) baseThreat += 30.0;
        } else if (entityType.contains("warden")) {
            baseThreat = 100.0;
        } else if (entityType.contains("ravager")) {
            baseThreat = 40.0;
        } else if (entityType.contains("skeleton") || entityType.contains("stray")) {
            baseThreat = 20.0;
        } else if (entityType.contains("witch")) {
            baseThreat = 25.0;
        } else if (entityType.contains("blaze")) {
            baseThreat = 30.0;
        } else if (entityType.contains("ghast")) {
            baseThreat = 35.0;
        } else if (entityType.contains("drowned") && distance > 5.0) {
            baseThreat = 15.0;
        } else if (entityType.contains("pillager")) {
            baseThreat = 18.0;
        } else if (entityType.contains("phantom")) {
            baseThreat = 22.0;
        } else if (entityType.contains("zombie") || entityType.contains("husk")) {
            baseThreat = 8.0;
        } else if (entityType.contains("spider") || entityType.contains("cave_spider")) {
            baseThreat = 12.0;
        } else if (entityType.contains("enderman")) {
            baseThreat = 15.0;
        } else if (entityType.contains("vindicator")) {
            baseThreat = 25.0;
        } else if (entityType.contains("piglin")) {
            baseThreat = 10.0;
        } else if (entityType.contains("slime") || entityType.contains("magma_cube")) {
            baseThreat = 6.0;
        } else if (entityType.contains("silverfish")) {
            baseThreat = 4.0;
        }

        return baseThreat;
    }

    private static String getTargetSelectionReason(Entity entity, double distance) {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity) {
            return "\u00a74Hostile player - armed and dangerous!";
        }

        String entityType = entity.getName().getString().toLowerCase();

        if (entityType.contains("creeper")) return "\u00a7cExplosive threat - must eliminate immediately!";
        if (entityType.contains("skeleton") || entityType.contains("witch") ||
            entityType.contains("blaze") || entityType.contains("ghast")) return "\u00a76Ranged attacker - dangerous at distance";
        if (entityType.contains("phantom")) return "\u00a7bAerial threat - difficult to evade";
        if (entityType.contains("warden") || entityType.contains("ravager")) return "\u00a74Extremely dangerous - maximum threat";
        if (distance < 3.0) return "\u00a7eImmediate danger - very close proximity";
        if (distance < 6.0) return "\u00a7eClose range threat";

        return "\u00a77Highest calculated threat";
    }

}
