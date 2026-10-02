package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.UsefulVillagers;
import me.kctops6.usefulvillagers.config.PvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

@Mod.EventBusSubscriber(modid = UsefulVillagers.MODID)
public class ButcherAutomationHandler {

    @SubscribeEvent
    public static void onButcherTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) return;
        if (villager.tickCount % 20 != 0 || villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return;

        if (villager.level().isNight() || villager.isSleeping()) return;

        // Pickup drops spawned from kills before processing new tasks
        collectNearbyDrops(villager);

        int level = villager.getVillagerData().getLevel();
        boolean performedAction = false;

        // PRIORITIZE DEPOSITING: Clean out inventory before slaughtering/breeding
        if (hasProductsToDeposit(villager)) {
            performedAction = depositProducts(villager);
        }

        if (!performedAction && needsBreedingMaterials(villager)) {
            performedAction = restockFromFarmerStorage(villager);
        }

        if (!performedAction) {
            performedAction = manageAnimals(villager, level);
        }

        if (!performedAction) {
            goToWorkstation(villager);
        }
    }

    private static boolean manageAnimals(Villager butcher, int level) {
        BlockPos workPos = butcher.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        // FULL INVENTORY GUARD: Stop hunting if butcher inventory has no empty slots
        if (!hasInventorySpace(butcher)) {
            return false;
        }

        ServerLevel levelObj = (ServerLevel) butcher.level();
        int workRadius = PvConfig.HARVEST_RANGE.get() * 2;
        AABB area = new AABB(workPos).inflate(workRadius);

        Class<? extends Animal>[] targets = (level >= 3)
                ? new Class[]{Cow.class, Sheep.class, Pig.class, Chicken.class, Rabbit.class}
                : new Class[]{Pig.class, Chicken.class, Rabbit.class};

        for (Class<? extends Animal> species : targets) {
            List<? extends Animal> population = levelObj.getEntitiesOfClass(species, area);
            int limit = getLimitForSpecies(species);

            boolean needsWeapon = PvConfig.BUTCHER_NEEDS_WEAPON.get();
            if (population.size() > limit && (!needsWeapon || equipWeaponIfAvailable(butcher))) {
                Animal victim = population.stream().filter(a -> !a.isBaby()).findFirst().orElse(null);
                if (victim != null) {
                    if (!moveAndInteract(butcher, victim)) return true;

                    // Deal lethal damage so the mob plays its visible death animation
                    DamageSource source = butcher.damageSources().mobAttack(butcher);
                    victim.hurt(source, Float.MAX_VALUE);

                    if (needsWeapon) consumeHeldWeaponDurability(butcher);
                    butcher.swing(InteractionHand.MAIN_HAND);

                    // Collect drops immediately if close enough
                    collectNearbyDrops(butcher);
                    return true;
                }
            } else if (population.size() >= 2 && population.size() < limit) {
                Item food = getFoodForSpecies(species);
                if (butcher.getInventory().countItem(food) < 1) continue;

                Animal parent = population.stream()
                        .filter(a -> a.getAge() == 0 && !a.isInLove() && a.canFallInLove())
                        .findFirst().orElse(null);

                if (parent != null) {
                    if (moveAndInteract(butcher, parent)) {
                        butcher.getInventory().removeItemType(food, 1);
                        parent.setInLove(null);
                        butcher.swing(InteractionHand.MAIN_HAND);
                        return true;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasInventorySpace(Villager butcher) {
        SimpleContainer inv = butcher.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasProductsToDeposit(Villager butcher) {
        SimpleContainer inv = butcher.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !isBreedingItem(stack.getItem()) && !(stack.getItem() instanceof TieredItem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equipWeaponIfAvailable(Villager butcher) {
        ItemStack held = butcher.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem) {
            return true;
        }

        SimpleContainer inv = butcher.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
                butcher.setItemInHand(InteractionHand.MAIN_HAND, stack);
                return true;
            }
        }
        return false;
    }

    private static void consumeHeldWeaponDurability(Villager butcher) {
        ItemStack held = butcher.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem) {
            if (held.isDamageableItem()) {
                held.setDamageValue(held.getDamageValue() + 1);
                if (held.getDamageValue() >= held.getMaxDamage()) {
                    butcher.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
            }
        }
    }

    private static boolean moveAndInteract(Villager butcher, Object target) {
        butcher.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);

        BlockPos pos = (target instanceof LivingEntity e) ? e.blockPosition() : (BlockPos) target;
        double reach = PvConfig.HARVEST_REACH.get();

        if (butcher.blockPosition().distSqr(pos) > (reach * reach)) {
            butcher.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 0.6D);
            return false;
        }

        butcher.getNavigation().stop();
        return true;
    }

    private static boolean restockFromFarmerStorage(Villager butcher) {
        ServerLevel level = (ServerLevel) butcher.level();
        BlockPos currentPos = butcher.blockPosition();
        BlockPos farmerWorkstation = null;

        int range = PvConfig.HARVEST_RANGE.get();
        for (BlockPos pos : BlockPos.betweenClosed(currentPos.offset(-range, -3, -range), currentPos.offset(range, 3, range))) {
            if (level.getBlockState(pos).is(Blocks.COMPOSTER)) {
                farmerWorkstation = pos.immutable();
                break;
            }
        }

        if (farmerWorkstation == null) return false;

        for (BlockPos pos : BlockPos.betweenClosed(farmerWorkstation.offset(-3, -1, -3), farmerWorkstation.offset(3, 1, 3))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                if (!moveAndInteract(butcher, pos)) return true;

                IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                if (handler != null) {
                    Item[] needed = {Items.CARROT, Items.POTATO, Items.WHEAT, Items.WHEAT_SEEDS};
                    for (Item item : needed) {
                        int has = butcher.getInventory().countItem(item);
                        if (has < 12) withdrawItem(butcher, handler, item, 12 - has);
                    }
                    butcher.swing(InteractionHand.MAIN_HAND);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean depositProducts(Villager butcher) {
        BlockPos workPos = butcher.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-3, -1, -3), workPos.offset(3, 1, 3))) {
            BlockEntity be = butcher.level().getBlockEntity(pos);
            if (be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                if (!moveAndInteract(butcher, pos)) return true;

                IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                if (handler != null) {
                    SimpleContainer inv = butcher.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (!stack.isEmpty() && !isBreedingItem(stack.getItem()) && !(stack.getItem() instanceof TieredItem)) {
                            inv.setItem(i, ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false));
                        }
                    }
                    butcher.swing(InteractionHand.MAIN_HAND);
                }
                return true;
            }
        }
        return false;
    }

    private static void collectNearbyDrops(Villager butcher) {
        AABB area = butcher.getBoundingBox().inflate(3.5);
        List<ItemEntity> items = butcher.level().getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity item : items) {
            ItemStack leftover = butcher.getInventory().addItem(item.getItem());
            item.setItem(leftover);
            if (leftover.isEmpty()) item.discard();
        }
    }

    private static void withdrawItem(Villager butcher, IItemHandler handler, Item item, int amountNeeded) {
        int taken = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) {
                ItemStack extracted = handler.extractItem(i, amountNeeded - taken, false);
                butcher.getInventory().addItem(extracted);
                taken += extracted.getCount();
                if (taken >= amountNeeded) break;
            }
        }
    }

    private static boolean needsBreedingMaterials(Villager butcher) {
        SimpleContainer inv = butcher.getInventory();
        return inv.countItem(Items.CARROT) < 4 || inv.countItem(Items.POTATO) < 4 || inv.countItem(Items.WHEAT) < 4 || inv.countItem(Items.WHEAT_SEEDS) < 4;
    }

    private static void goToWorkstation(Villager villager) {
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(gp -> {
            if (villager.blockPosition().distSqr(gp.pos()) > 2.25) {
                villager.getNavigation().moveTo(gp.pos().getX(), gp.pos().getY(), gp.pos().getZ(), 0.5D);
            }
        });
    }

    private static int getLimitForSpecies(Class<? extends Animal> species) {
        if (species == Cow.class) return PvConfig.COW_LIMIT.get();
        if (species == Sheep.class) return PvConfig.SHEEP_LIMIT.get();
        if (species == Pig.class) return PvConfig.PIG_LIMIT.get();
        if (species == Chicken.class) return PvConfig.CHICKEN_LIMIT.get();
        if (species == Rabbit.class) return PvConfig.RABBIT_LIMIT.get();
        return PvConfig.GLOBAL_ANIMAL_LIMIT.get();
    }

    private static Item getFoodForSpecies(Class<? extends Animal> species) {
        if (species == Cow.class || species == Sheep.class) return Items.WHEAT;
        if (species == Pig.class) return Items.CARROT;
        if (species == Chicken.class) return Items.WHEAT_SEEDS;
        if (species == Rabbit.class) return Items.CARROT;
        return Items.AIR;
    }

    private static boolean isBreedingItem(Item item) {
        return item == Items.WHEAT || item == Items.WHEAT_SEEDS || item == Items.CARROT || item == Items.POTATO || item == Items.BEETROOT;
    }
}