package net.shasankp000.Entity;

import carpet.CarpetSettings;
import carpet.patches.FakeClientConnection;
import carpet.utils.Messenger;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shasankp000.AIPlayer;

// Same as carpet's code for spawning fake players, only difference is that it will work even if the command executor is in offline mode

public class createFakePlayer extends ServerPlayer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    public boolean isAShadow;


    // constructor
    private createFakePlayer(MinecraftServer server, ServerLevel worldIn, GameProfile profile, ClientInformation cli, boolean shadow) {

        super(server, worldIn, profile, cli);
        isAShadow = shadow;

    }

    public static void createFake(String username, MinecraftServer server, Vec3 pos, double yaw, double pitch, ResourceKey<Level> dimensionId, GameType gamemode, boolean flying) {

        ServerLevel worldIn = server.getLevel(dimensionId);
        GameProfile gameProfile;
        boolean useMojangAuth = server.isDedicatedServer() && server.usesAuthentication();

        gameProfile = useMojangAuth ? null : null;

        Map<String, String> existingBotProfile = AIPlayer.CONFIG.getBotGameProfile();

        if (gameProfile == null) {

            System.out.println("Existing Bot Profiles: " + existingBotProfile);

            if (!existingBotProfile.containsKey(username) || existingBotProfile.isEmpty()) {
                gameProfile = new GameProfile(UUID.randomUUID(), username);
                HashMap<String, String> botProfile = new HashMap<>();
                botProfile.put(gameProfile.name(), gameProfile.id().toString());

                System.out.println("New GameProfile: " + gameProfile);

                try {
                    AIPlayer.CONFIG.setBotGameProfile(botProfile);

                    // Save the data to config as strings
                    Map<String, String> currentBotProfile = new HashMap<>();
                    for (Map.Entry<String, String> entry : botProfile.entrySet()) {
                        currentBotProfile.put(entry.getKey(), entry.getValue());
                    }

                    AIPlayer.CONFIG.setBotGameProfile(currentBotProfile);
                    AIPlayer.CONFIG.save();
                    System.out.println("Saved data to config");

                } catch (Exception e) {
                    LOGGER.error("Could not save data to config: {}", e.getMessage());
                }
            } else {
                UUID existingUUID = UUID.fromString(existingBotProfile.get(username));
                gameProfile = new GameProfile(existingUUID, username);
                System.out.println("Using existing GameProfile: " + gameProfile);
            }
        }



        if (useMojangAuth) {

            GameProfile finalGP = gameProfile;
            fetchGameProfile(gameProfile.name()).thenAccept(p -> {
                GameProfile current = p.orElse(finalGP);
                spawnFake(server, worldIn, current, pos, yaw, pitch, gamemode, flying, dimensionId);
            });
        }

        else {

            spawnFake(server, worldIn, gameProfile, pos, yaw, pitch, gamemode, flying, dimensionId);

        }

    }

    private static void spawnFake(MinecraftServer server, ServerLevel worldIn, GameProfile gameprofile, Vec3 pos, double yaw, double pitch, GameType gamemode, boolean flying, ResourceKey<Level> dimensionId) {
        createFakePlayer instance = new createFakePlayer(server, worldIn, gameprofile, ClientInformation.createDefault(), false);
        server.getPlayerList().placeNewPlayer(new FakeClientConnection(PacketFlow.SERVERBOUND), instance, new CommonListenerCookie(gameprofile, 0, instance.clientInformation(), false));
        instance.teleportTo(worldIn, pos.x, pos.y, pos.z, Set.of(), (float) yaw, (float) pitch, false);
        instance.setHealth(20.0F);
        instance.unsetRemoved();
        instance.gameMode.changeGameModeForPlayer(gamemode);
        server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), dimensionId);
        server.getPlayerList().broadcastAll(new ClientboundTeleportEntityPacket(instance.getId(), PositionMoveRotation.of(instance), Set.of(), instance.onGround()), dimensionId);
        instance.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7f);
        instance.getAbilities().flying = flying;
    }


    private static CompletableFuture<Optional<GameProfile>> fetchGameProfile(final String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == 200) {
                    LOGGER.info("Found player {} on mojang's server", name);
                    try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                        GameProfile profile = new Gson().fromJson(reader, GameProfile.class);
                        return Optional.ofNullable(profile);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Player {} was not found on mojang's servers. {}", name, e.getMessage());
                throw new RuntimeException(e);
            }
            return Optional.empty();
        });
    }


    // Code copied over from carpet.



    @Override
    public void onEquipItem(final EquipmentSlot slot, final ItemStack previous, final ItemStack stack)
    {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    public void kill()
    {
        kill(Messenger.s("Killed"));
    }

    public void kill(Component reason)
    {
        shakeOff();

        if (reason.getContents() instanceof TranslatableContents text && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.connection.disconnect(reason);
        } else {
            createCommandSourceStack().getServer().execute(() -> this.connection.disconnect(reason));
        }
    }

    @Override
    public void tick()
    {
        if (createCommandSourceStack().getServer().getTickCount() % 10 == 0)
        {
            this.connection.resetPosition();
            this.level().getChunkSource().move(this);
        }
        try
        {
            super.tick();
            this.doTick();
        }
        catch (NullPointerException ignored)
        {
            // happens with that paper port thingy - not sure what that would fix, but hey
            // the game is not going to crash violently.
        }


    }

    private void shakeOff()
    {
        if (getVehicle() instanceof Player) stopRiding();
        for (Entity passenger : getIndirectPassengers())
        {
            if (passenger instanceof Player) passenger.stopRiding();
        }
    }

    @Override
    public void die(DamageSource cause)
    {
        shakeOff();
        super.die(cause);
        setHealth(20);
        this.foodData = new FoodData();
        kill(this.getCombatTracker().getDeathMessage());
    }

    @Override
    public String getIpAddress()
    {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsListing() {
        return CarpetSettings.allowListingFakePlayers;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        doCheckFallDamage(0.0, y, 0.0, onGround);
    }

    @Override
    public ServerPlayer teleport(TeleportTransition target)
    {
        ServerPlayer entity = super.teleport(target);
        if (wonGame) {
            ServerboundClientCommandPacket p = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
            connection.handleClientCommand(p);
        }

        // If above branch was taken, *this* has been removed and replaced, the new instance has been set
        // on 'our' connection (which is now theirs, but we still have a ref).
        if (connection.player.isChangingDimension()) {
            connection.player.hasChangedDimension();
        }
        return connection.player;
    }


}
