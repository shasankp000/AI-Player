package net.shasankp000.Entity;

import java.io.Serial;
import java.io.Serializable;

public class EntityDetails implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final double x, y, z;
    private final boolean isHostile;
    private final String directionToBot; // New parameter

    public EntityDetails(String name, double x, double y, double z, boolean isHostile, String directionToBot) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.isHostile = isHostile;
        this.directionToBot = directionToBot;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean isHostile() {
        return isHostile;
    }

    public String getDirectionToBot() {
        return directionToBot;
    }

    @Override
    public String toString() {
        return "EntityDetails{" +
                "name='" + name + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", isHostile=" + isHostile +
                ", directionToBot='" + directionToBot + '\'' +
                '}';
    }
}

