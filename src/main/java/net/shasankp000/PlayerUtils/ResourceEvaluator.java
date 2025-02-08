package net.shasankp000.PlayerUtils;

import java.util.List;

public class ResourceEvaluator {

    public static double evaluateHotbarResourceValue(List<String> hotbarItems) {
        double totalValue = 0.0;

        for (String item : hotbarItems) {
            if (item.contains("Sword")) {
                totalValue += evaluateSword(item);
            } else if (item.contains("Axe")) {
                totalValue += evaluateAxe(item);
            } else if (item.contains("Pickaxe")) {
                totalValue += evaluatePickaxe(item);
            } else if (item.contains("Hoe")) {
                totalValue += evaluateHoe(item);
            } else if (item.contains("Shovel")) {
                totalValue += evaluateShovel(item);
            } else if (item.contains("Bow")) {
                totalValue += evaluateBow(item);
            } else if (item.contains("Crossbow")) {
                totalValue += evaluateCrossbow(item);
            } else if (item.contains("Trident")) {
                totalValue += evaluateTrident(item);
            } else if (item.contains("Apple")) {
                totalValue += evaluateApple(item);
            } else if (item.contains("Bread")) {
                totalValue += 5.0; // Moderate value food
            } else if (item.contains("Steak")) {
                totalValue += 8.0; // High-value food
            } else if (item.contains("Water Bucket")) {
                totalValue += 10.0; // Utility item with high value
            } else if (item.contains("Planks")) {
                totalValue += 3.0; // Basic building material
            } else if (item.contains("Cobblestone")) {
                totalValue += 4.0; // Durable building material
            } else if (item.contains("Log")) {
                totalValue += 5.0; // Versatile resource
            } else if (item.contains("Stone")) {
                totalValue += 4.0; // Common building material
            } else if (item.contains("Tuff")) {
                totalValue += 3.0; // Decorative building material
            } else if (item.contains("Torch")) {
                totalValue += 2.0; // Basic lighting utility
            } else if (item.contains("Crafting Table")) {
                totalValue += 8.0; // Essential crafting block
            } else if (item.contains("Furnace")) {
                totalValue += 7.0; // Essential smelting block
            } else if (item.contains("Arrow")) {
                totalValue += 1.0; // Basic ammo for ranged weapons
            }
        }

        return totalValue;
    }

    private static double evaluateSword(String sword) {
        return switch (sword) {
            case "Wooden Sword" -> 5.0;
            case "Stone Sword" -> 10.0;
            case "Iron Sword" -> 15.0;
            case "Golden Sword" -> 12.0; // Less durable but high damage
            case "Diamond Sword" -> 25.0;
            case "Netherite Sword" -> 30.0;
            default -> 0.0;
        };
    }

    private static double evaluateAxe(String axe) {
        return switch (axe) {
            case "Wooden Axe" -> 4.0;
            case "Stone Axe" -> 8.0;
            case "Iron Axe" -> 12.0;
            case "Golden Axe" -> 10.0; // Fast but less durable
            case "Diamond Axe" -> 20.0;
            case "Netherite Axe" -> 25.0;
            default -> 0.0;
        };
    }

    private static double evaluatePickaxe(String pickaxe) {
        return switch (pickaxe) {
            case "Wooden Pickaxe" -> 3.0;
            case "Stone Pickaxe" -> 6.0;
            case "Iron Pickaxe" -> 10.0;
            case "Golden Pickaxe" -> 8.0; // High speed but low durability
            case "Diamond Pickaxe" -> 18.0;
            case "Netherite Pickaxe" -> 22.0;
            default -> 0.0;
        };
    }

    private static double evaluateHoe(String hoe) {
        return switch (hoe) {
            case "Wooden Hoe" -> 2.0;
            case "Stone Hoe" -> 4.0;
            case "Iron Hoe" -> 6.0;
            case "Golden Hoe" -> 5.0; // Less durable but fast
            case "Diamond Hoe" -> 10.0;
            case "Netherite Hoe" -> 12.0;
            default -> 0.0;
        };
    }

    private static double evaluateShovel(String shovel) {
        return switch (shovel) {
            case "Wooden Shovel" -> 3.0;
            case "Stone Shovel" -> 6.0;
            case "Iron Shovel" -> 9.0;
            case "Golden Shovel" -> 7.0; // Fast but low durability
            case "Diamond Shovel" -> 15.0;
            case "Netherite Shovel" -> 18.0;
            default -> 0.0;
        };
    }

    private static double evaluateApple(String apple) {
        return switch (apple) {
            case "Apple" -> 4.0; // Normal apple
            case "Golden Apple" -> 20.0; // Rare and powerful
            case "Enchanted Golden Apple" -> 50.0; // Extremely rare and powerful
            default -> 0.0;
        };
    }

    private static double evaluateBow(String bow) {
        return switch (bow) {
            case "Bow" -> 15.0; // Basic ranged weapon
            default -> 0.0;
        };
    }

    private static double evaluateCrossbow(String crossbow) {
        return switch (crossbow) {
            case "Crossbow" -> 20.0; // Standard crossbow
            case "Arrow Loaded Crossbow" -> 25.0; // Crossbow loaded with arrow
            case "Firework Loaded Crossbow" -> 30.0; // Crossbow loaded with firework
            default -> 0.0;
        };
    }

    private static double evaluateTrident(String trident) {
        return switch (trident) {
            case "Trident" -> 28.0; // Powerful melee and ranged weapon
            default -> 0.0;
        };
    }

}

