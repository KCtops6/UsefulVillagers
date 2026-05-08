package me.kctops6.usefulvillagers.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class PvConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue ITEM_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue HARVEST_RANGE;

    static {
        BUILDER.push("Farmer Automation Settings");
        ITEM_THRESHOLD = BUILDER.comment("Items needed for auto-trade (Default 64)")
                .defineInRange("itemThreshold", 64, 1, 640);
        XP_MULTIPLIER = BUILDER.comment("XP Gained multiplier (0.5 = 50%)")
                .defineInRange("xpMultiplier", 0.5, 0.0, 10.0);
        HARVEST_RANGE = BUILDER.comment("How far from their workstation farmers can look for pumpkins/melons")
                .defineInRange("harvestRange", 10, 1, 32);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}