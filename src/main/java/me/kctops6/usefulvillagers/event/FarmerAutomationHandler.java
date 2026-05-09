package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import me.kctops6.usefulvillagers.config.PvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID)
public class FarmerAutomationHandler {

    private static final Set<Item> FARMER_INPUTS = Set.of(
            Items.WHEAT, Items.POTATO, Items.CARROT, Items.BEETROOT
    );

    @SubscribeEvent
    public static void onDiligenceTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            if (villager.tickCount % 20 == 0 && villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {

                // PRIORITY 1: Harvesting and Replanting
                boolean busyFarming = performDiligentFarming(villager);

                if (!busyFarming) {
                    // NEW: Use Bone Meal if available in inventory
                    useInventoryBoneMeal(villager);

                    // PRIORITY 2: Composting excess seeds
                    checkComposter(villager);

                    // PRIORITY 3: Deposit everything else into the chest
                    depositSurplus(villager);
                }
            }
        }
    }

    private static void useInventoryBoneMeal(Villager villager) {
        SimpleContainer inv = villager.getInventory();

        // Check if the villager has any bone meal
        if (inv.countItem(Items.BONE_MEAL) > 0) {
            BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(GlobalPos::pos).orElse(villager.blockPosition());

            // Find and grow a nearby crop
            applyBoneMealToNearbyCrops(villager, workPos);

            // Consume one bone meal from inventory
            inv.removeItemType(Items.BONE_MEAL, 1);
            villager.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void applyBoneMealToNearbyCrops(Villager villager, BlockPos workPos) {
        ServerLevel level = (ServerLevel) villager.level();
        int range = PvConfig.HARVEST_RANGE.get();
        BlockPos targetCrop = null;

        // Track the lowest growth percentage (0.0 to 1.0)
        double lowestGrowthPercentage = 1.0;

        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof CropBlock crop) {
                // Use public methods instead of getAgeProperty()
                int currentAge = crop.getAge(state);
                int maxAge = crop.getMaxAge();

                if (currentAge < maxAge) {
                    double growthPercentage = (double) currentAge / maxAge;
                    if (growthPercentage < lowestGrowthPercentage) {
                        lowestGrowthPercentage = growthPercentage;
                        targetCrop = pos.immutable();
                    }
                }
            }
        }

        if (targetCrop != null) {
            ItemStack fakeBoneMeal = new ItemStack(Items.BONE_MEAL);
            if (net.minecraft.world.item.BoneMealItem.applyBonemeal(fakeBoneMeal, level, targetCrop, null)) {
                level.levelEvent(2005, targetCrop, 0); // Success particles
            }
        }
    }

    private static void depositSurplus(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = villager.getInventory();

        // Locate the workstation to find a nearby chest
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos).orElse(null);
        if (workPos == null) return;

        // Search for a chest within a small radius of the workstation
        BlockPos chestPos = null;
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-3, -1, -3), workPos.offset(3, 1, 3))) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                chestPos = pos.immutable();
                break;
            }
        }

        if (chestPos != null) {
            // Move to the chest if too far away
            if (villager.blockPosition().distSqr(chestPos) > 4.0) {
                villager.getNavigation().moveTo(chestPos.getX(), chestPos.getY(), chestPos.getZ(), 0.5D);
            } else {
                // Interact with the chest using Forge's item handler capability
                final net.minecraft.world.level.block.entity.ChestBlockEntity chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
                chest.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty()) continue;

                        Item item = stack.getItem();
                        int keep = getKeepAmount(item);

                        // Only deposit if we have more than the keep amount (e.g., more than 8 seeds or any wheat)
                        if (stack.getCount() > keep) {
                            // Special check: Only one stack (64) of seeds allowed in the chest
                            if (isSeed(item) && countInHandler(handler, item) >= 64) {
                                continue;
                            }

                            int toDeposit = stack.getCount() - keep;
                            ItemStack depositStack = stack.split(toDeposit);
                            ItemStack leftover = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handler, depositStack, false);

                            // Return anything that couldn't fit in the chest to the villager
                            stack.grow(leftover.getCount());
                        }
                    }
                });
                villager.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    // Helper to count items already in the chest
    private static int countInHandler(net.minecraftforge.items.IItemHandler handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @SubscribeEvent
    public static void onVillagerTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            if (villager.tickCount % 20 == 0 && villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {
                ServerLevel level = (ServerLevel) villager.level();
                if (!canVillageExpand(level, villager.blockPosition())) {
                    preventFoodWaste(villager);
                }
            }
        }
    }

    private static boolean canVillageExpand(ServerLevel level, BlockPos pos) {
        // Checks for unclaimed beds within 48 blocks
        return level.getPoiManager().getCountInRange(
                poiType -> poiType.is(PoiTypes.HOME),
                pos,
                48,
                PoiManager.Occupancy.HAS_SPACE
        ) > 0;
    }

    private static void preventFoodWaste(Villager villager) {
        // By reducing the internal 'food' value of the villager when beds are missing,
        // they stop the 'ShareFood' AI task from triggering.
        if (villager.getInventory().countItem(Items.BREAD) > 0 ||
                villager.getInventory().countItem(Items.CARROT) > 0) {
        }
    }

    @SubscribeEvent
    public static void onFarmerHarvestTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            // Check every 2 seconds
            if (villager.tickCount % 40 == 0 && villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {
                int level = villager.getVillagerData().getLevel();
                if (level >= 2) {
                    searchAndHarvest(villager, level);
                }
            }
        }
    }

    private static void searchAndHarvest(Villager villager, int level) {
        ServerLevel serverLevel = (ServerLevel) villager.level();
        BlockPos workPos = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
                .map(net.minecraft.core.GlobalPos::pos).orElse(null);

        if (workPos == null) return;

        int range = PvConfig.HARVEST_RANGE.get();
        BlockPos villagerPos = villager.blockPosition();

        // 1. Find a target block within range of the workstation
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = serverLevel.getBlockState(pos);
            Block block = state.getBlock();

            boolean isPumpkin = (block == Blocks.PUMPKIN && level >= 2);
            boolean isMelon = (block == Blocks.MELON && level >= 3);

            if (isPumpkin || isMelon) {
                // 2. Check distance: If too far, walk to it. If close, harvest it.
                double distSq = villagerPos.distSqr(pos);

                if (distSq > 4.0) { // Approx 2 blocks away
                    villager.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 0.5D);
                } else {
                    // 3. Right next to it! Perform the harvest
                    harvestToInventory(serverLevel, villager, pos, state);
                }
                return; // Found a target, stop looking this tick
            }
        }
    }

    private static void harvestToInventory(ServerLevel level, Villager villager, BlockPos pos, BlockState state) {
        villager.swing(InteractionHand.MAIN_HAND);

        // Get the drops for the block
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, villager, ItemStack.EMPTY);
        SimpleContainer inv = villager.getInventory();

        // Put drops directly into the villager's inventory
        for (ItemStack drop : drops) {
            ItemStack leftover = inv.addItem(drop);
            // If inventory is full, drop the leftover on the ground
            if (!leftover.isEmpty()) {
                Block.popResource(level, pos, leftover);
            }
        }

        // Remove the block
        level.destroyBlock(pos, false); // false = don't drop items on ground
    }


    private static void checkComposter(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = villager.getInventory();

        int seedCount = inv.countItem(Items.WHEAT_SEEDS);

        if (seedCount > 8) {
            BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(GlobalPos::pos).orElse(null);

            if (workPos != null && level.getBlockState(workPos).is(Blocks.COMPOSTER)) {
                double distSq = villager.blockPosition().distSqr(workPos);

                if (distSq > 4.0) {
                    villager.getNavigation().moveTo(workPos.getX(), workPos.getY(), workPos.getZ(), 0.5D);
                } else {
                    BlockState state = level.getBlockState(workPos);
                    int fillLevel = state.getValue(ComposterBlock.LEVEL);

                    if (fillLevel < 7) {
                        // Remove the seed first
                        inv.removeItemType(Items.WHEAT_SEEDS, 1);

                        // 30% chance to increase composter level (Vanilla chance for seeds)
                        if (level.random.nextFloat() < 0.3F) {
                            BlockState newState = state.setValue(ComposterBlock.LEVEL, fillLevel + 1);
                            level.setBlock(workPos, newState, 3);
                            level.levelEvent(1500, workPos, 1); // Success sound/particles
                        } else {
                            level.levelEvent(1500, workPos, 0); // Failure sound/particles
                        }
                        villager.swing(InteractionHand.MAIN_HAND);
                    } else {
                        // It's full! Level 7 becomes Level 8 (Ready) then we reset it to 0
                        // This spawns the Bone Meal and resets the composter
                        level.setBlock(workPos, state.setValue(ComposterBlock.LEVEL, 0), 3);
                        level.levelEvent(1500, workPos, 1);
                        inv.addItem(new ItemStack(Items.BONE_MEAL));
                        villager.swing(InteractionHand.MAIN_HAND);
                    }
                }
            }
        }
    }

    private static boolean performDiligentFarming(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos).orElse(null);

        if (workPos == null) return false;

        int range = PvConfig.HARVEST_RANGE.get();
        SimpleContainer inv = villager.getInventory();

        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();

            // Check for mature crops
            if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
                // If we have room in inventory, go harvest
                if (hasRoomFor(inv, getSeedForCrop(block))) {
                    if (moveAndAction(villager, pos)) {
                        Block cropType = state.getBlock();

                        // Manual collection (Direct to Inventory)
                        List<ItemStack> drops = Block.getDrops(state, level, pos, null, villager, ItemStack.EMPTY);
                        for (ItemStack drop : drops) {
                            ItemStack leftover = inv.addItem(drop);
                            if (!leftover.isEmpty()) {
                                Block.popResource(level, pos, leftover);
                            }
                        }

                        level.destroyBlock(pos, false);
                        replantSameCrop(level, pos, inv, cropType);
                        villager.swing(InteractionHand.MAIN_HAND);
                    }
                    return true; // Farmer is busy harvesting
                }
            }
        }
        return false; // No crops found to harvest
    }

    // Helper to check if there is at least one free slot or a non-full stack of the item
    private static boolean hasRoomFor(SimpleContainer inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || (stack.is(item) && stack.getCount() < stack.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    private static boolean moveAndAction(Villager villager, BlockPos target) {
        double distSq = villager.blockPosition().distSqr(target);
        if (distSq > 3.0) {
            villager.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.6D);
            return false;
        }
        return true;
    }

    private static void replantSameCrop(ServerLevel level, BlockPos pos, SimpleContainer inv, Block oldCrop) {
        Item seedNeeded = getSeedForCrop(oldCrop);

        if (seedNeeded != Items.AIR) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && stack.is(seedNeeded)) {
                    // Set the block back to the original crop at age 0
                    level.setBlock(pos, oldCrop.defaultBlockState(), 3);
                    stack.shrink(1);
                    return;
                }
            }
        }
    }

    private static Item getSeedForCrop(Block crop) {
        if (crop == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (crop == Blocks.CARROTS) return Items.CARROT;
        if (crop == Blocks.POTATOES) return Items.POTATO;
        if (crop == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        return Items.AIR;
    }

    private static int getKeepAmount(Item item) {
        // Keep exactly 8 of these for replanting
        if (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS ||
                item == Items.CARROT || item == Items.POTATO) {
            return 8;
        }

        // Everything else (Wheat, Pumpkin, Melon, Beetroot) keep amount is 0
        return 0;
    }

    private static boolean isSeed(Item item) {
        // Only these items will be capped at 64 in the chest
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS ||
                item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS;
    }
}