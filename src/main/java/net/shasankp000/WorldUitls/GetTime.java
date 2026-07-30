package net.shasankp000.WorldUitls;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class GetTime {
    public static int getTimeOfWorld(ServerPlayer bot) {

        Level GameWorld = bot.level();

        long timeOfDay = GameWorld.getDefaultClockTime() % 24000; // Normalize to one day cycle

        return (int) timeOfDay;
    }

}
