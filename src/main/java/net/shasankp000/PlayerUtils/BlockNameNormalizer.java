package net.shasankp000.PlayerUtils;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class BlockNameNormalizer {

    public static String normalizeBlockName(String rawInput) {
        if (rawInput == null || rawInput.isEmpty()) return rawInput;

        String cleaned = rawInput.toLowerCase()
                .replace("minecraft:", "")
                .replaceAll("[\\s\\-]", "_");

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

        if (bestMatch != null) {
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


