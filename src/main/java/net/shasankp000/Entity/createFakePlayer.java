package net.shasankp000.Entity;

import carpet.CarpetSettings;
import carpet.patches.FakeClientConnection;
import carpet.utils.Messenger;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.UserCache;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import net.shasankp000.FilingSystem.AIPlayerConfigModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.shasankp000.AIPlayer;

// Same as carpet's code for spawning fake players, only difference is that it will work even if the command executor is in offline mode

public class createFakePlayer extends ServerPlayerEntity {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    public boolean isAShadow;


    // constructor
    private createFakePlayer(MinecraftServer server, ServerWorld worldIn, GameProfile profile, SyncedClientOptions cli, boolean shadow) {

        super(server, worldIn, profile, cli);
        isAShadow = shadow;

    }

    public static void createFake(String username, MinecraftServer server, Vec3d pos, double yaw, double pitch, RegistryKey<World> dimensionId, GameMode gamemode, boolean flying) {

        ServerWorld worldIn = server.getWorld(dimensionId);
        UserCache.setUseRemote(false);
        GameProfile gameProfile;
        boolean useMojangAuth = server.isDedicated() && server.isOnlineMode();
        AIPlayerConfigModel aiPlayerConfigModel = new AIPlayerConfigModel();

        try {
            gameProfile = useMojangAuth ? server.getUserCache().findByName(username).orElse(null) : null;
        }
        finally {
            UserCache.setUseRemote(useMojangAuth);
        }

        Map<String, String> existingBotProfile = AIPlayer.CONFIG.BotGameProfile();

        if (gameProfile == null) {

            System.out.println("Existing Bot Profiles: " + existingBotProfile);

            if (!existingBotProfile.containsKey(username) || existingBotProfile.isEmpty()) {
                gameProfile = new GameProfile(UUID.randomUUID(), username);
                HashMap<String, String> botProfile = new HashMap<>();
                botProfile.put(gameProfile.getName(), gameProfile.getId().toString());

                System.out.println("New GameProfile: " + gameProfile);

                try {
                    aiPlayerConfigModel.setBotGameProfile(botProfile);

                    // Save the data to config as strings
                    Map<String, String> currentBotProfile = new HashMap<>();
                    for (Map.Entry<String, String> entry : botProfile.entrySet()) {
                        currentBotProfile.put(entry.getKey(), entry.getValue());
                    }

                    AIPlayer.CONFIG.BotGameProfile(currentBotProfile);
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
            fetchGameProfile(gameProfile.getName()).thenAccept(p -> {
                GameProfile current = p.orElse(finalGP);
                spawnFake(server, worldIn, current, pos, yaw, pitch, gamemode, flying, dimensionId);
            });
        }

        else {

            spawnFake(server, worldIn, gameProfile, pos, yaw, pitch, gamemode, flying, dimensionId);

        }

    }

    private static void spawnFake(MinecraftServer server, ServerWorld worldIn, GameProfile gameprofile, Vec3d pos, double yaw, double pitch, GameMode gamemode, boolean flying, RegistryKey<World> dimensionId) {
        createFakePlayer instance = new createFakePlayer(server, worldIn, gameprofile, SyncedClientOptions.createDefault(), false);
//        instance.fixStartingPosition = () -> {
//            instance.refreshPositionAndAngles(pos.x, pos.y, pos.z, (float) yaw, (float) pitch);
//            System.out.println("Fixed " + instance.getName().getString() + "'s starting position");
//            server.getPlayerManager().broadcast(Text.literal("Fixed " + instance.getName().getString() + "'s starting position"), true);
//        };
        server.getPlayerManager().onPlayerConnect(new FakeClientConnection(NetworkSide.SERVERBOUND), instance, new ConnectedClientData(gameprofile, 0, instance.getClientOptions(), false));
        instance.teleport(worldIn, pos.x, pos.y, pos.z, (float) yaw, (float) pitch);
        instance.setHealth(20.0F);
        instance.unsetRemoved();
        instance.interactionManager.changeGameMode(gamemode);
        server.getPlayerManager().sendToDimension(new EntitySetHeadYawS2CPacket(instance, (byte) (instance.headYaw * 256 / 360)), dimensionId);
        server.getPlayerManager().sendToDimension(new EntityPositionS2CPacket(instance), dimensionId);
        instance.dataTracker.set(PLAYER_MODEL_PARTS, (byte) 0x7f);
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
    public void onEquipStack(final EquipmentSlot slot, final ItemStack previous, final ItemStack stack)
    {
        if (!isUsingItem()) super.onEquipStack(slot, previous, stack);
    }

    @Override
    public void kill()
    {
        kill(Messenger.s("Killed"));
    }

    public void kill(Text reason)
    {
        shakeOff();

        if (reason.getContent() instanceof TranslatableTextContent text && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.networkHandler.onDisconnected(reason);
        } else {
            this.server.send(new ServerTask(this.server.getTicks(), () -> {
                this.networkHandler.onDisconnected(reason);
            }));
        }
    }

    @Override
    public void tick()
    {
        if (Objects.requireNonNull(this.getServer()).getTicks() % 10 == 0)
        {
            this.networkHandler.syncWithPlayerPosition();
            this.getServerWorld().getChunkManager().updatePosition(this);
        }
        try
        {
            super.tick();
            this.playerTick();
        }
        catch (NullPointerException ignored)
        {
            // happens with that paper port thingy - not sure what that would fix, but hey
            // the game is not going to crash violently.
        }


    }

    private void shakeOff()
    {
        if (getVehicle() instanceof PlayerEntity) stopRiding();
        for (Entity passenger : getPassengersDeep())
        {
            if (passenger instanceof PlayerEntity) passenger.stopRiding();
        }
    }

    @Override
    public void onDeath(DamageSource cause)
    {
        shakeOff();
        super.onDeath(cause);
        setHealth(20);
        this.hungerManager = new HungerManager();
        kill(this.getDamageTracker().getDeathMessage());
    }

    @Override
    public String getIp()
    {
        return "127.0.0.1";
    }

    @Override
    public boolean allowsServerListing() {
        return CarpetSettings.allowListingFakePlayers;
    }

    @Override
    protected void fall(double y, boolean onGround, BlockState state, BlockPos pos) {
        handleFall(0.0, y, 0.0, onGround);
    }

    @Override
    public Entity moveToWorld(ServerWorld serverLevel)
    {
        super.moveToWorld(serverLevel);
        if (notInAnyWorld) {
            ClientStatusC2SPacket p = new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN);
            networkHandler.onClientStatus(p);
        }

        // If above branch was taken, *this* has been removed and replaced, the new instance has been set
        // on 'our' connection (which is now theirs, but we still have a ref).
        if (networkHandler.player.isInTeleportationState()) {
            networkHandler.player.onTeleportationDone();
        }
        return networkHandler.player;
    }


}
