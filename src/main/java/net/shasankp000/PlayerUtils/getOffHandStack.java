package net.shasankp000.PlayerUtils;


import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public class getOffHandStack {

    public static ItemStack getOffhandItem(ServerPlayerEntity bot) {
        // The offhand slot is a specific slot in the bot's inventory
        return bot.getOffHandStack();
    }

}
