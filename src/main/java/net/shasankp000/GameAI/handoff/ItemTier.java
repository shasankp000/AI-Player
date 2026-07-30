package net.shasankp000.GameAI.handoff;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Classifies an {@link ItemStack} into one of five gift-value tiers.
 *
 * <pre>
 *  LEGENDARY  — netherite gear, enchanted books, nether star, elytra, totem
 *  EPIC       — diamond gear/tools, trident, ender pearls (x≥4)
 *  RARE       — iron/gold gear, bow, crossbow, useful potions
 *  COMMON     — food, wood/stone/copper gear, basic materials
 *  JUNK       — cobblestone, dirt, gravel, sand, rotten flesh, sticks
 * </pre>
 *
 * Tiers are matched by item-id substring so they work across datapacks
 * without needing Minecraft registry lookups.
 */
public enum ItemTier {

    LEGENDARY,
    EPIC,
    RARE,
    COMMON,
    JUNK;

    // -----------------------------------------------------------------------
    // Classification
    // -----------------------------------------------------------------------

    /**
     * Returns the {@link ItemTier} for the given stack.
     * Never returns {@code null}.
     */
    public static ItemTier of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return JUNK;
        String id = rawId(stack);

        if (isLegendary(id, stack)) return LEGENDARY;
        if (isEpic(id, stack))      return EPIC;
        if (isRare(id))             return RARE;
        if (isJunk(id))             return JUNK;
        return COMMON;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String rawId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath(); // e.g. "diamond_sword", "netherite_pickaxe"
    }

    private static boolean isLegendary(String id, ItemStack stack) {
        if (id.startsWith("netherite_"))      return true;
        if (id.equals("nether_star"))         return true;
        if (id.equals("elytra"))              return true;
        if (id.equals("totem_of_undying"))    return true;
        if (id.equals("enchanted_golden_apple")) return true;
        if (id.equals("heart_of_the_sea"))   return true;
        // Enchanted books count as legendary
        if (id.equals("enchanted_book"))      return true;
        // Any enchanted item is at least legendary
        if (!stack.getEnchantments().isEmpty()) return true;
        return false;
    }

    private static boolean isEpic(String id, ItemStack stack) {
        if (id.startsWith("diamond_"))        return true;
        if (id.equals("trident"))             return true;
        if (id.equals("ender_pearl"))         return stack.getCount() >= 4;
        if (id.equals("blaze_rod"))           return true;
        if (id.equals("shulker_box"))         return true;
        if (id.contains("shulker_box"))       return true; // coloured variants
        if (id.equals("beacon"))              return true;
        if (id.equals("wither_skeleton_skull")) return true;
        return false;
    }

    private static boolean isRare(String id) {
        if (id.startsWith("iron_"))           return true;
        if (id.startsWith("golden_"))         return true;
        if (id.equals("bow"))                 return true;
        if (id.equals("crossbow"))            return true;
        if (id.equals("shield"))              return true;
        if (id.equals("splash_potion"))       return true;
        if (id.equals("lingering_potion"))    return true;
        if (id.equals("potion"))              return true;
        if (id.equals("experience_bottle"))   return true;
        if (id.equals("saddle"))              return true;
        if (id.equals("name_tag"))            return true;
        if (id.equals("ender_eye"))           return true;
        return false;
    }

    private static boolean isJunk(String id) {
        if (id.equals("cobblestone"))         return true;
        if (id.equals("cobbled_deepslate"))   return true;
        if (id.equals("dirt"))                return true;
        if (id.equals("gravel"))              return true;
        if (id.equals("sand"))                return true;
        if (id.equals("red_sand"))            return true;
        if (id.equals("rotten_flesh"))        return true;
        if (id.equals("stick"))               return true;
        if (id.equals("bone"))                return true;
        if (id.equals("spider_eye"))          return true;
        if (id.equals("poisonous_potato"))    return true;
        if (id.equals("gunpowder"))           return true;
        if (id.equals("gravel"))              return true;
        return false;
    }
}
