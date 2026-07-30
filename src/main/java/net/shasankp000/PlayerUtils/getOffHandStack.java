package net.shasankp000.PlayerUtils;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class getOffHandStack {

    public static ItemStack getOffhandItem(ServerPlayer bot) {
        // The offhand slot is a specific slot in the bot's inventory
        return bot.getOffhandItem();
    }

}
