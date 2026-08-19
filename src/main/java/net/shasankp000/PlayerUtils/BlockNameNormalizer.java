package net.shasankp000.PlayerUtils;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class BlockNameNormalizer {

    // A match only counts if it actually resembles the input; a bare length
    // penalty (e.g. numeric garbage like "0") is not a real match.
    private static final int MIN_MATCH_SCORE = 0;

    public static String normalizeBlockName(String rawInput) {
        if (rawInput == null || rawInput.isEmpty()) return rawInput;

        String cleaned = rawInput.toLowerCase()
                .replace("minecraft:", "")
                .replaceAll("[\\s\\-]", "_");

        // Raw numeric block IDs resolve directly through the block registry.
        if (cleaned.matches("[0-9]+")) {
            int rawId = Integer.parseInt(cleaned);
            for (Identifier id : Registries.BLOCK.getIds()) {
                Block block = Registries.BLOCK.get(id);
                if (block != null && block != Blocks.AIR && Registries.BLOCK.getRawId(block) == rawId) {
                    return id.toString();
                }
            }
            // No (solid) block with this raw id; leave the input unchanged so the
            // caller's lookup fails instead of matching an unrelated block.
            return "minecraft:" + cleaned;
        }

        Identifier bestMatch = null;
        int bestScore = Integer.MIN_VALUE;

        for (Identifier id : Registries.BLOCK.getIds()) {
            String path = id.getPath();

            int score = getMatchScore(cleaned, path);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = id;
            }
        }

        if (bestMatch != null && bestScore >= MIN_MATCH_SCORE) {
            System.out.println("[block-detection-unit] Best match for " + cleaned + ": " + bestMatch + " with score " + bestScore);
            return bestMatch.toString();
        }

        // If nothing scores well, default to this fallback
        return "minecraft:" + cleaned;
    }

    private static int getMatchScore(String input, String target) {
        int score = 0;

        if (target.equals(input)) score += 1000;           // Perfect match
        else if (target.startsWith(input)) score += 500;   // Starts with
        else if (target.contains(input)) score += 100;     // Substring match

        // Prefer shorter names for less ambiguity
        score -= target.length();

        return score;
    }
}


