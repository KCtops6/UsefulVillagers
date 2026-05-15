package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import me.kctops6.usefulvillagers.config.PvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
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

@Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID)
public class ButcherAutomationHandler {

    private static final int WORK_RADIUS = 60;

    @SubscribeEvent
    public static void onButcherTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) return;
        if (villager.tickCount % 20 != 0 || villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return;

        if (villager.level().isNight()) return;

        int level = villager.getVillagerData().getLevel();
        boolean performedAction = false;

        // 1. Check if we need food and restock if possible
        if (needsBreedingMaterials(villager)) {
            performedAction = restockFromFarmerStorage(villager);
        }

        // 2. Manage Animals (Breeding and Slaughtering)
        if (!performedAction) {
            performedAction = manageAnimals(villager, level);
        }

        // 3. Deposit finished products
        if (!performedAction) {
            performedAction = depositProducts(villager);
        }

        // 4. Return to workstation if idle
        if (!performedAction) {
            goToWorkstation(villager);
        }
    }

    private static boolean manageAnimals(Villager butcher, int level) {
        BlockPos workPos = butcher.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        ServerLevel levelObj = (ServerLevel) butcher.level();
        AABB area = new AABB(workPos).inflate(WORK_RADIUS);

        Class<? extends Animal>[] targets = (level >= 3)
                ? new Class[]{Cow.class, Sheep.class, Pig.class, Chicken.class, Rabbit.class}
                : new Class[]{Pig.class, Chicken.class, Rabbit.class};

        for (Class<? extends Animal> species : targets) {
            List<? extends Animal> population = levelObj.getEntitiesOfClass(species, area);
            int limit = getLimitForSpecies(species);

            // --- SLAUGHTER LOGIC ---
            boolean needsWeapon = PvConfig.BUTCHER_NEEDS_WEAPON.get();
            if (population.size() > limit && (!needsWeapon || isHoldingWeapon(butcher))) {
                Animal victim = population.stream().filter(a -> !a.isBaby()).findFirst().orElse(null);
                if (victim != null) {
                    if (!moveAndInteract(butcher, victim)) return true; // Moving to target

                    victim.hurt(butcher.damageSources().mobAttack(butcher), 100F);
                    if (needsWeapon) consumeWeaponDurability(butcher);
                    collectNearbyDrops(butcher);
                    butcher.swing(InteractionHand.MAIN_HAND);
                    return true;
                }
            }

            // --- BREEDING LOGIC (BED REQUIREMENT REMOVED) ---
            else if (population.size() >= 2 && population.size() < limit) {
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
                    return true; // Return true to keep pathing to this animal
                }
            }
        }
        return false;
    }

    private static void consumeWeaponDurability(Villager butcher) {
        SimpleContainer inv = butcher.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
                if (stack.isDamageableItem()) {
                    stack.setDamageValue(stack.getDamageValue() + 1);
                    if (stack.getDamageValue() >= stack.getMaxDamage()) {
                        stack.shrink(1);
                    }
                }
                break;
            }
        }
    }

    private static boolean moveAndInteract(Villager butcher, Object target) {
        BlockPos pos = (target instanceof LivingEntity e) ? e.blockPosition() : (BlockPos) target;
        double distSq = butcher.blockPosition().distSqr(pos);

        if (distSq > 4.5) { // Interaction range
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

        for (BlockPos pos : BlockPos.betweenClosed(currentPos.offset(-25, -3, -25), currentPos.offset(25, 3, 25))) {
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
                Item[] needed = {Items.CARROT, Items.POTATO, Items.WHEAT, Items.WHEAT_SEEDS};
                for (Item item : needed) {
                    int has = butcher.getInventory().countItem(item);
                    if (has < 12) withdrawItem(butcher, handler, item, 12 - has);
                }
                butcher.swing(InteractionHand.MAIN_HAND);
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
                SimpleContainer inv = butcher.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty() && !isBreedingItem(stack.getItem()) && !(stack.getItem() instanceof TieredItem)) {
                        inv.setItem(i, ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false));
                    }
                }
                butcher.swing(InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private static void collectNearbyDrops(Villager butcher) {
        AABB area = butcher.getBoundingBox().inflate(2.5);
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

    private static boolean isHoldingWeapon(Villager butcher) {
        SimpleContainer inv = butcher.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            Item item = inv.getItem(i).getItem();
            if (item instanceof SwordItem || item instanceof AxeItem) return true;
        }
        return false;
    }

    private static void goToWorkstation(Villager villager) {
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(gp -> {
            if (villager.blockPosition().distSqr(gp.pos()) > 4) {
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