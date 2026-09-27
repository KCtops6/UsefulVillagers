package me.kctops6.usefulvillagers.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class PvConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue ITEM_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue HARVEST_RANGE;
    public static final ForgeConfigSpec.DoubleValue HARVEST_REACH;
    public static final ForgeConfigSpec.BooleanValue SHEPHERD_NEEDS_SHEARS;
    public static final ForgeConfigSpec.DoubleValue SHEPHERD_REACH;
    public static final ForgeConfigSpec.IntValue GLOBAL_ANIMAL_LIMIT;

    public static final ForgeConfigSpec.IntValue COW_LIMIT;
    public static final ForgeConfigSpec.IntValue SHEEP_LIMIT;
    public static final ForgeConfigSpec.IntValue PIG_LIMIT;
    public static final ForgeConfigSpec.IntValue CHICKEN_LIMIT;
    public static final ForgeConfigSpec.IntValue RABBIT_LIMIT;
    public static final ForgeConfigSpec.BooleanValue BUTCHER_NEEDS_WEAPON;

    static {
        BUILDER.push("Farmer Automation Settings");
        ITEM_THRESHOLD = BUILDER.comment("Items needed for auto-trade (Default 64)")
                .defineInRange("itemThreshold", 64, 1, 640);
        XP_MULTIPLIER = BUILDER.comment("XP Gained multiplier (0.5 = 50%)")
                .defineInRange("xpMultiplier", 0.5, 0.0, 10.0);
        HARVEST_RANGE = BUILDER.comment("How far from their workstation farmers/shepherds can work")
                .defineInRange("harvestRange", 10, 1, 32);
        HARVEST_REACH = BUILDER.comment("The reach distance for a villager to harvest a crop")
                .defineInRange("harvestReach", 4.5, 1.0, 10.0);
        BUILDER.pop();

        BUILDER.push("Shepherd Automation Settings");
        SHEPHERD_NEEDS_SHEARS = BUILDER.comment("If true, the shepherd must have shears in his inventory to shear sheep.")
                .define("shepherdNeedsShears", true);
        SHEPHERD_REACH = BUILDER.comment("The reach distance for a shepherd to shear a sheep.")
                .defineInRange("shepherdReach", 4.5, 1.0, 10.0);
        BUILDER.pop();

        BUILDER.push("Butcher Automation Settings");
        GLOBAL_ANIMAL_LIMIT = BUILDER.comment("Global limit for animal species before a butcher starts harvesting")
                .defineInRange("globalAnimalLimit", 3, 2, 20);
        COW_LIMIT = BUILDER.defineInRange("cowLimit", 3, 2, 20);
        SHEEP_LIMIT = BUILDER.defineInRange("sheepLimit", 3, 2, 20);
        PIG_LIMIT = BUILDER.defineInRange("pigLimit", 3, 2, 20);
        CHICKEN_LIMIT = BUILDER.defineInRange("chickenLimit", 3, 2, 20);
        RABBIT_LIMIT = BUILDER.defineInRange("rabbitLimit", 3, 2, 20);
        BUTCHER_NEEDS_WEAPON = BUILDER.comment("If true, butchers must possess a sword or axe to slaughter animals.")
                .define("butcherNeedsWeapon", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}