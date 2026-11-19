package me.kctops6.usefulvillagers.ai;

import me.kctops6.usefulvillagers.config.PvConfig;
import me.kctops6.usefulvillagers.util.VillagerNavigationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.EnumSet;

public class FarmerWorkGoal extends Goal {
    private final Villager villager;
    private int cooldown = 0;

    public FarmerWorkGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().isClientSide()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.FARMER) return false; // (Use appropriate profession for Shepherd/Butcher)
        if (villager.level().isNight() || villager.isSleeping()) return false;

        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // Run once per activation, then immediately release control back to vanilla AI
        return false;
    }

    @Override
    public void start() {
        cooldown = 20; // 1 second cooldown before the goal can run again
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) villager.level();

        // Run your existing farmer logic here
        restrictSharing();
        boolean performedAction = performDiligentFarming(level);
        if (!performedAction) {
            performedAction = useInventoryBoneMeal(level) ||
                    checkComposter(level) ||
                    depositSurplus(level);
            if (!performedAction && villager.getVillagerData().getLevel() >= 2) {
                performedAction = searchAndHarvestSpecialty(level);
            }
        }
        if (!performedAction) {
            VillagerNavigationUtils.goToWorkstation(villager);
        }
    }

    private void restrictSharing() {
        SimpleContainer inv = villager.getInventory();
        Item[] foodItems = {Items.BREAD, Items.CARROT, Items.POTATO, Items.BEETROOT};
        for (Item item : foodItems) {
            if (inv.countItem(item) <= 8) {
                villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }
        }
    }

    private boolean performDiligentFarming(ServerLevel level) {
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        int range = PvConfig.HARVEST_RANGE.get();
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                if (moveAndAction(pos)) {
                    Block type = state.getBlock();
                    harvestToInventory(level, pos, state);
                    replantSameCrop(level, pos, type);
                }
                return true;
            }
        }
        return false;
    }

    private boolean useInventoryBoneMeal(ServerLevel level) {
        SimpleContainer inv = villager.getInventory();
        if (inv.countItem(Items.BONE_MEAL) > 0) {
            BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(GlobalPos::pos).orElse(villager.blockPosition());
            if (applyBoneMealToNearbyCrops(level, workPos)) {
                inv.removeItemType(Items.BONE_MEAL, 1);
                villager.swing(InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private boolean applyBoneMealToNearbyCrops(ServerLevel level, BlockPos workPos) {
        int range = PvConfig.HARVEST_RANGE.get();
        BlockPos targetCrop = null;
        double lowestGrowthPercentage = 1.0;

        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop) {
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

        if (targetCrop != null && moveAndAction(targetCrop)) {
            ItemStack fakeBoneMeal = new ItemStack(Items.BONE_MEAL);
            return net.minecraft.world.item.BoneMealItem.applyBonemeal(fakeBoneMeal, level, targetCrop, null);
        }
        return false;
    }

    private boolean checkComposter(ServerLevel level) {
        SimpleContainer inv = villager.getInventory();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos != null && level.getBlockState(workPos).is(Blocks.COMPOSTER)) {
            BlockState state = level.getBlockState(workPos);
            int fillLevel = state.getValue(ComposterBlock.LEVEL);

            if (fillLevel >= 7) {
                if (moveAndAction(workPos)) {
                    level.setBlock(workPos, state.setValue(ComposterBlock.LEVEL, 0), 3);
                    ItemStack leftover = inv.addItem(new ItemStack(Items.BONE_MEAL));
                    if (!leftover.isEmpty()) Block.popResource(level, workPos, leftover); // Fixed Item Voiding bug
                    villager.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
                return true;
            } else if (inv.countItem(Items.WHEAT_SEEDS) > 8) {
                if (moveAndAction(workPos)) {
                    inv.removeItemType(Items.WHEAT_SEEDS, 1);
                    if (level.random.nextFloat() < 0.3F) {
                        level.setBlock(workPos, state.setValue(ComposterBlock.LEVEL, fillLevel + 1), 3);
                    }
                    villager.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private boolean depositSurplus(ServerLevel level) {
        SimpleContainer inv = villager.getInventory();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-3, -1, -3), workPos.offset(3, 1, 3))) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                if (moveAndAction(pos)) {
                    level.getBlockEntity(pos).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                        for (int i = 0; i < inv.getContainerSize(); i++) {
                            ItemStack stack = inv.getItem(i);
                            if (stack.isEmpty()) continue;
                            int keep = getKeepAmount(stack.getItem());
                            if (stack.getCount() > keep) {
                                ItemStack depositStack = stack.split(stack.getCount() - keep);
                                ItemStack leftover = ItemHandlerHelper.insertItemStacked(handler, depositStack, false);
                                stack.grow(leftover.getCount());
                            }
                        }
                    });
                    villager.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean searchAndHarvestSpecialty(ServerLevel level) {
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;
        int levelInt = villager.getVillagerData().getLevel();

        int range = PvConfig.HARVEST_RANGE.get();
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = level.getBlockState(pos);
            boolean isPumpkin = (state.is(Blocks.PUMPKIN) && levelInt >= 2);
            boolean isMelon = (state.is(Blocks.MELON) && levelInt >= 3);

            if (isPumpkin || isMelon) {
                if (moveAndAction(pos)) {
                    harvestToInventory(level, pos, state);
                }
                return true;
            }
        }
        return false;
    }

    private void harvestToInventory(ServerLevel level, BlockPos pos, BlockState state) {
        villager.swing(InteractionHand.MAIN_HAND);
        for (ItemStack drop : Block.getDrops(state, level, pos, null, villager, ItemStack.EMPTY)) {
            ItemStack leftover = villager.getInventory().addItem(drop);
            if (!leftover.isEmpty()) Block.popResource(level, pos, leftover);
        }
        level.destroyBlock(pos, false);
    }

    private boolean moveAndAction(BlockPos target) {
        return VillagerNavigationUtils.moveToOrInteract(villager, target, PvConfig.HARVEST_REACH.get());
    }

    private void replantSameCrop(ServerLevel level, BlockPos pos, Block oldCrop) {
        Item seed = (oldCrop == Blocks.WHEAT) ? Items.WHEAT_SEEDS : (oldCrop == Blocks.CARROTS) ? Items.CARROT : (oldCrop == Blocks.POTATOES) ? Items.POTATO : (oldCrop == Blocks.BEETROOTS) ? Items.BEETROOT_SEEDS : Items.AIR;
        if (seed != Items.AIR) {
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                ItemStack stack = villager.getInventory().getItem(i);
                if (stack.is(seed)) {
                    level.setBlock(pos, oldCrop.defaultBlockState(), 3);
                    stack.shrink(1);
                    return;
                }
            }
        }
    }

    private int getKeepAmount(Item item) {
        return (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.CARROT || item == Items.POTATO) ? 8 : 0;
    }
}