package net.shasankp000.GameAI.handoff;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pure-logic class: given what the player offered and what the bot currently
 * carries, returns an acceptable counter-offer (or {@link ItemStack#EMPTY} if
 * the bot cannot trade).
 *
 * <p><b>Tier parity rule</b>: each item belongs to a numeric tier (1-5).
 * The bot will only agree if it can find something of equal or adjacent tier
 * in its own inventory.
 */
public final class TradeEvaluator {

    private TradeEvaluator() {}

    // ── Tier table ──────────────────────────────────────────────────────────────
    // Higher number == more valuable.
    private static final Map<Item, Integer> TIER = new HashMap<>();

    static {
        // Tier 1 — common
        for (Item it : new Item[]{
                Items.STICK, Items.WOODEN_SWORD, Items.WOODEN_PICKAXE,
                Items.OAK_PLANKS, Items.COBBLESTONE, Items.SAND, Items.GRAVEL,
                Items.WHEAT, Items.BREAD, Items.APPLE, Items.PORKCHOP,
                Items.COOKED_PORKCHOP, Items.BEEF, Items.COOKED_BEEF,
                Items.ROTTEN_FLESH, Items.BONE, Items.STRING}) {
            TIER.put(it, 1);
        }
        // Tier 2 — uncommon
        for (Item it : new Item[]{
                Items.STONE_SWORD, Items.STONE_PICKAXE, Items.IRON_INGOT,
                Items.COAL, Items.CHARCOAL, Items.IRON_SWORD, Items.IRON_PICKAXE,
                Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE,
                Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
                Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE,
                Items.ARROW, Items.FLINT, Items.FEATHER}) {
            TIER.put(it, 2);
        }
        // Tier 3 — valuable
        for (Item it : new Item[]{
                Items.GOLD_INGOT, Items.DIAMOND, Items.DIAMOND_SWORD,
                Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE,
                Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE,
                Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GHAST_TEAR}) {
            TIER.put(it, 3);
        }
        // Tier 4 — rare
        for (Item it : new Item[]{
                Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP,
                Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
                Items.ELYTRA, Items.TOTEM_OF_UNDYING,
                Items.SHULKER_SHELL, Items.NETHER_STAR}) {
            TIER.put(it, 4);
        }
        // Tier 5 — legendary
        for (Item it : new Item[]{
                Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE,
                Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
                Items.BEACON}) {
            TIER.put(it, 5);
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Returns a counter-offer the bot is willing to make, or
     * {@link ItemStack#EMPTY} if no acceptable trade is possible.
     *
     * @param offered what the player threw
     * @param bot     the bot whose inventory will be searched
     */
    public static ItemStack evaluate(ItemStack offered, ServerPlayer bot) {
        if (offered.isEmpty()) return ItemStack.EMPTY;

        int offeredTier = tierOf(offered.getItem());
        if (offeredTier == 0) return ItemStack.EMPTY; // unknown item — bot won't trade

        // Search bot's entire inventory for an item of equal/adjacent tier
        // that is NOT the same item the player offered (no identical swaps).
        ItemStack best = ItemStack.EMPTY;
        int bestTierDelta = Integer.MAX_VALUE;

        for (int slot = 0; slot < bot.getInventory().getContainerSize(); slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == offered.getItem()) continue; // same item — skip

            int botTier = tierOf(stack.getItem());
            if (botTier == 0) continue; // untiered — skip

            int delta = Math.abs(botTier - offeredTier);
            if (delta <= 1 && delta < bestTierDelta) {
                bestTierDelta = delta;
                best = stack;
            }
        }

        if (best.isEmpty()) return ItemStack.EMPTY;

        // Return a single item of the chosen type.
        ItemStack result = best.copy();
        result.setCount(1);
        return result;
    }

    /**
     * Returns how many of {@code item} the bot currently carries across
     * all inventory slots (mirrors the private helper in {@code ItemHandoffHandler}).
     */
    public static int countItemInBotInventory(ServerPlayer bot, Item item) {
        int total = 0;
        for (int slot = 0; slot < bot.getInventory().getContainerSize(); slot++) {
            ItemStack stack = bot.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Returns the tier of an item (1-5), or 0 if unknown.
     */
    public static int tierOf(Item item) {
        return TIER.getOrDefault(item, 0);
    }

    /**
     * Human-readable item name ("minecraft:diamond" → "Diamond").
     */
    public static String displayName(ItemStack stack) {
        if (stack.isEmpty()) return "nothing";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(); // e.g. "diamond"
        String[] words = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1))
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
