package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.UsefulVillagers;
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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = UsefulVillagers.MODID)
public class FarmerAutomationHandler {

    private static final Map<UUID, BlockPos> CHEST_CACHE = new HashMap<>();
    private static final Map<UUID, Long> CACHE_EXPIRATION = new HashMap<>();
    private static final long CACHE_TTL_TICKS = 600;

    @SubscribeEvent
    public static void onDiligenceTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) return;

        if ((villager.tickCount + villager.getId()) % 20 != 0) return;

        if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {

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
        SimpleContainer inv = villager.getInventory();
        Item[] foodItems = {Items.BREAD, Items.CARROT, Items.POTATO, Items.BEETROOT};

        for (Item item : foodItems) {
            int count = inv.countItem(item);
            if (count <= 8) {
                villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }
        }
    }

    private static void goToWorkstation(Villager villager) {
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(globalPos -> {
            BlockPos workPos = globalPos.pos();
            if (villager.blockPosition().distSqr(workPos) > 2.25) {
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
            if (applyBoneMealToNearbyCrops(villager, workPos)) {
                inv.removeItemType(Items.BONE_MEAL, 1);
                villager.swing(InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private static boolean applyBoneMealToNearbyCrops(Villager villager, BlockPos workPos) {
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
            return net.minecraft.world.item.BoneMealItem.applyBonemeal(fakeBoneMeal, level, targetCrop, null);
        }
        return false;
    }

    private static boolean checkComposter(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = villager.getInventory();

        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos != null && level.getBlockState(workPos).is(Blocks.COMPOSTER)) {
            BlockState state = level.getBlockState(workPos);
            int fillLevel = state.getValue(ComposterBlock.LEVEL);

            if (fillLevel >= 7) {
                if (moveAndAction(villager, workPos)) {
                    level.setBlock(workPos, state.setValue(ComposterBlock.LEVEL, 0), 3);
                    inv.addItem(new ItemStack(Items.BONE_MEAL));
                    villager.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
                return true;
            } else if (inv.countItem(Items.WHEAT_SEEDS) > 8) {
                if (moveAndAction(villager, workPos)) {
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

    private static boolean depositSurplus(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = villager.getInventory();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        BlockPos chestPos = getCachedChestPos(level, villager.getUUID(), workPos);

        if (chestPos != null) {
            if (moveAndAction(villager, chestPos)) {
                if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                    chest.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                        for (int i = 0; i < inv.getContainerSize(); i++) {
                            ItemStack stack = inv.getItem(i);
                            if (stack.isEmpty()) continue;
                            int keep = getKeepAmount(stack.getItem());
                            if (stack.getCount() > keep) {
                                if (isSeed(stack.getItem()) && countInHandler(handler, stack.getItem()) >= 64) continue;
                                ItemStack depositStack = stack.split(stack.getCount() - keep);
                                ItemStack leftover = ItemHandlerHelper.insertItemStacked(handler, depositStack, false);
                                stack.grow(leftover.getCount());
                            }
                        }
                    });
                    villager.swing(InteractionHand.MAIN_HAND);
                } else {
                    CHEST_CACHE.remove(villager.getUUID());
                }
            }
            return true;
        }
        return false;
    }

    private static BlockPos getCachedChestPos(ServerLevel level, UUID id, BlockPos workPos) {
        long currentTick = level.getGameTime();
        if (CHEST_CACHE.containsKey(id) && CACHE_EXPIRATION.getOrDefault(id, 0L) > currentTick) {
            return CHEST_CACHE.get(id);
        }

        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-3, -1, -3), workPos.offset(3, 1, 3))) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                BlockPos found = pos.immutable();
                CHEST_CACHE.put(id, found);
                CACHE_EXPIRATION.put(id, currentTick + CACHE_TTL_TICKS);
                return found;
            }
        }
        CHEST_CACHE.remove(id);
        return null;
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
        if (villager.blockPosition().distSqr(target) > (reach * reach)) {
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

    private static int countInHandler(IItemHandler handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.is(item)) total += s.getCount();
        }
        return total;
    }
}