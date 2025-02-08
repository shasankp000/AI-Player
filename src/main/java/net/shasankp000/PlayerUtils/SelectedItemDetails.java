package net.shasankp000.PlayerUtils;

import java.io.Serializable;

public class SelectedItemDetails implements Serializable {
    private static final long serialVersionUID = 1L; // Ensures compatibility between serialized versions

    private final String name;
    private final boolean isFood;
    private final boolean isBlock;

    public SelectedItemDetails(String name, boolean isFood, boolean isBlock) {
        this.name = name;
        this.isFood = isFood;
        this.isBlock = isBlock;
    }

    public String getName() {
        return name;
    }

    public boolean isFood() {
        return isFood;
    }

    public boolean isBlock() {
        return isBlock;

    }

    @Override
    public String toString() {
        return "SelectedItemDetails{" +
                "name = '" + name + '\'' +
                ", isFood = " + isFood +
                ", isBlock = " + isBlock +
                '}';
    }
}

