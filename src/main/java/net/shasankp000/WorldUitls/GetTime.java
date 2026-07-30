package net.shasankp000.WorldUitls;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

public class GetTime {
    public static int getTimeOfWorld(ServerPlayerEntity bot) {

        World GameWorld = bot.getServerWorld();

        long timeOfDay = GameWorld.getTimeOfDay() % 24000; // Normalize to one day cycle

        return (int) timeOfDay;
    }

}
