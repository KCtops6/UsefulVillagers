package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import me.kctops6.usefulvillagers.config.PvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID)
public class FarmerAutomationHandler {

    @SubscribeEvent
    public static void onDiligenceTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) return;

        if (villager.tickCount % 20 == 0 && villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {

            // Prevent standard vanilla sharing if under 8 items
            restrictSharing(villager);

            boolean performedAction = performDiligentFarming(villager);

            if (!performedAction) {
                performedAction = useInventoryBoneMeal(villager) ||
                        checkComposter(villager) ||
                        depositSurplus(villager);

                if (!performedAction) {
                    int level = villager.getVillagerData().getLevel();
                    if (level >= 2) {
                        performedAction = searchAndHarvestSpecialty(villager, level);
                    }
                }

                if (!performedAction) {
                    goToWorkstation(villager);
                }
            }
        }
    }

    private static void restrictSharing(Villager villager) {
        // We override the behavior that allows villagers to share food by checking inventory counts
        SimpleContainer inv = villager.getInventory();
        Item[] foodItems = {Items.BREAD, Items.CARROT, Items.POTATO, Items.BEETROOT};

        for (Item item : foodItems) {
            int count = inv.countItem(item);
            // If they have 8 or less, we ensure they don't have a "wants to share" state
            if (count <= 8) {
                villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }
        }
    }

    private static void goToWorkstation(Villager villager) {
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(globalPos -> {
            BlockPos workPos = globalPos.pos();
            double distSq = villager.blockPosition().distSqr(workPos);
            if (distSq > 1.5) {
                villager.getNavigation().moveTo(workPos.getX(), workPos.getY(), workPos.getZ(), 0.5D);
            } else {
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        });
    }

    private static boolean useInventoryBoneMeal(Villager villager) {
        SimpleContainer inv = villager.getInventory();
        if (inv.countItem(Items.BONE_MEAL) > 0) {
            BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                    .map(GlobalPos::pos).orElse(villager.blockPosition());
            applyBoneMealToNearbyCrops(villager, workPos);
            inv.removeItemType(Items.BONE_MEAL, 1);
            villager.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private static void applyBoneMealToNearbyCrops(Villager villager, BlockPos workPos) {
        ServerLevel level = (ServerLevel) villager.level();
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

        if (targetCrop != null && moveAndAction(villager, targetCrop)) {
            ItemStack fakeBoneMeal = new ItemStack(Items.BONE_MEAL);
            if (net.minecraft.world.item.BoneMealItem.applyBonemeal(fakeBoneMeal, level, targetCrop, null)) {
                level.levelEvent(2005, targetCrop, 0);
            }
        }
    }

    private static boolean checkComposter(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = villager.getInventory();
        if (inv.countItem(Items.WHEAT_SEEDS) > 8) {
            BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
            if (workPos != null && level.getBlockState(workPos).is(Blocks.COMPOSTER)) {
                if (moveAndAction(villager, workPos)) {
                    BlockState state = level.getBlockState(workPos);
                    int fillLevel = state.getValue(ComposterBlock.LEVEL);
                    if (fillLevel < 7) {
                        inv.removeItemType(Items.WHEAT_SEEDS, 1);
                        if (level.random.nextFloat() < 0.3F) {
                            level.setBlock(workPos, state.setValue(ComposterBlock.LEVEL, fillLevel + 1), 3);
                        }
                    } else {
                        level.setBlock(workPos, state.setValue(ComposterBlock.LEVEL, 0), 3);
                        inv.addItem(new ItemStack(Items.BONE_MEAL));
                    }
                    villager.swing(InteractionHand.MAIN_HAND);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean depositSurplus(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = villager.getInventory();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        BlockPos chestPos = null;
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-3, -1, -3), workPos.offset(3, 1, 3))) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                chestPos = pos.immutable();
                break;
            }
        }

        if (chestPos != null) {
            if (moveAndAction(villager, chestPos)) {
                final net.minecraft.world.level.block.entity.ChestBlockEntity chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
                chest.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty()) continue;
                        int keep = getKeepAmount(stack.getItem());
                        if (stack.getCount() > keep) {
                            if (isSeed(stack.getItem()) && countInHandler(handler, stack.getItem()) >= 64) continue;
                            ItemStack depositStack = stack.split(stack.getCount() - keep);
                            ItemStack leftover = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handler, depositStack, false);
                            stack.grow(leftover.getCount());
                        }
                    }
                });
                villager.swing(InteractionHand.MAIN_HAND);
            }
            return true;
        }
        return false;
    }

    private static boolean searchAndHarvestSpecialty(Villager villager, int level) {
        ServerLevel serverLevel = (ServerLevel) villager.level();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        int range = PvConfig.HARVEST_RANGE.get();
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = serverLevel.getBlockState(pos);
            boolean isPumpkin = (state.is(Blocks.PUMPKIN) && level >= 2);
            boolean isMelon = (state.is(Blocks.MELON) && level >= 3);

            if (isPumpkin || isMelon) {
                if (moveAndAction(villager, pos)) {
                    harvestToInventory(serverLevel, villager, pos, state);
                }
                return true;
            }
        }
        return false;
    }

    private static void harvestToInventory(ServerLevel level, Villager villager, BlockPos pos, BlockState state) {
        villager.swing(InteractionHand.MAIN_HAND);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, villager, ItemStack.EMPTY);
        for (ItemStack drop : drops) {
            ItemStack leftover = villager.getInventory().addItem(drop);
            if (!leftover.isEmpty()) Block.popResource(level, pos, leftover);
        }
        level.destroyBlock(pos, false);
    }

    private static boolean performDiligentFarming(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        int range = PvConfig.HARVEST_RANGE.get();
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-range, -1, -range), workPos.offset(range, 1, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                if (moveAndAction(villager, pos)) {
                    Block type = state.getBlock();
                    harvestToInventory(level, villager, pos, state);
                    replantSameCrop(level, pos, villager.getInventory(), type);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean moveAndAction(Villager villager, BlockPos target) {
        double reach = PvConfig.HARVEST_REACH.get();
        double distSq = villager.blockPosition().distSqr(target);
        if (distSq > (reach * reach)) {
            villager.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.6D);
            return false;
        }
        villager.getNavigation().stop();
        return true;
    }

    private static void replantSameCrop(ServerLevel level, BlockPos pos, SimpleContainer inv, Block oldCrop) {
        Item seed = (oldCrop == Blocks.WHEAT) ? Items.WHEAT_SEEDS : (oldCrop == Blocks.CARROTS) ? Items.CARROT : (oldCrop == Blocks.POTATOES) ? Items.POTATO : (oldCrop == Blocks.BEETROOTS) ? Items.BEETROOT_SEEDS : Items.AIR;
        if (seed != Items.AIR) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(seed)) {
                    level.setBlock(pos, oldCrop.defaultBlockState(), 3);
                    stack.shrink(1);
                    return;
                }
            }
        }
    }

    private static int getKeepAmount(Item item) {
        return (item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.CARROT || item == Items.POTATO) ? 8 : 0;
    }

    private static boolean isSeed(Item item) {
        return item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS;
    }

    private static int countInHandler(net.minecraftforge.items.IItemHandler handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.is(item)) total += s.getCount();
        }
        return total;
    }
}