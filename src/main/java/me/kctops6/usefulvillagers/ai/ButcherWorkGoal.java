package me.kctops6.usefulvillagers.ai;

import me.kctops6.usefulvillagers.config.PvConfig;
import me.kctops6.usefulvillagers.util.VillagerNavigationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.EnumSet;
import java.util.List;

public class ButcherWorkGoal extends Goal {
    private final Villager villager;
    private int cooldown = 0;

    public ButcherWorkGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (villager.level().isClientSide()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        if (villager.level().isNight() || villager.isSleeping()) return false;

        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        cooldown = 20;
    }

    @Override
    public void tick() {
        collectNearbyDrops();

        int level = villager.getVillagerData().getLevel();
        boolean performedAction = false;

        if (hasProductsToDeposit()) {
            performedAction = depositProducts();
        }

        if (!performedAction && needsBreedingMaterials()) {
            performedAction = restockFromFarmerStorage();
        }

        if (!performedAction) {
            performedAction = manageAnimals(level);
        }

        if (!performedAction) {
            VillagerNavigationUtils.goToWorkstation(villager);
        }
    }

    private boolean manageAnimals(int level) {
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;
        if (!hasInventorySpace()) return false;
        ServerLevel levelObj = (ServerLevel) villager.level();
        int workRadius = PvConfig.HARVEST_RANGE.get() * 2;
        AABB area = new AABB(workPos).inflate(workRadius);

        Class<?>[] targets = (level >= 3)
                ? new Class<?>[]{Cow.class, Sheep.class, Pig.class, Chicken.class, Rabbit.class}
                : new Class<?>[]{Pig.class, Chicken.class, Rabbit.class};

        for (Class<?> speciesClass : targets) {
            @SuppressWarnings("unchecked")
            Class<? extends Animal> species = (Class<? extends Animal>) speciesClass;
            List<? extends Animal> population = levelObj.getEntitiesOfClass(species, area);
            int limit = getLimitForSpecies(species);

            boolean needsWeapon = PvConfig.BUTCHER_NEEDS_WEAPON.get();
            if (population.size() > limit && (!needsWeapon || equipWeaponIfAvailable())) {
                Animal victim = population.stream().filter(a -> !a.isBaby()).findFirst().orElse(null);
                if (victim != null) {
                    if (!moveAndInteract(victim)) return true;

                    DamageSource source = villager.damageSources().mobAttack(villager);
                    victim.hurt(source, Float.MAX_VALUE);

                    if (needsWeapon) consumeHeldWeaponDurability();
                    villager.swing(InteractionHand.MAIN_HAND);

                    collectNearbyDrops();
                    return true;
                }
            } else if (population.size() >= 2 && population.size() < limit) {
                Item food = getFoodForSpecies(species);
                if (villager.getInventory().countItem(food) < 1) continue;

                Animal parent = population.stream()
                        .filter(a -> a.getAge() == 0 && !a.isInLove() && a.canFallInLove())
                        .findFirst().orElse(null);

                if (parent != null) {
                    if (moveAndInteract(parent)) {
                        villager.getInventory().removeItemType(food, 1);
                        parent.setInLove(null);
                        villager.swing(InteractionHand.MAIN_HAND);
                        return true;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasInventorySpace() {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProductsToDeposit() {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && isBreedingItem(stack.getItem()) && !(stack.getItem() instanceof TieredItem)) {
                return true;
            }
        }
        return false;
    }

    private boolean equipWeaponIfAvailable() {
        ItemStack held = villager.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem) {
            return true;
        }

        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, stack);
                return true;
            }
        }
        return false;
    }

    private void consumeHeldWeaponDurability() {
        ItemStack held = villager.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem) {
            if (held.isDamageableItem()) {
                held.setDamageValue(held.getDamageValue() + 1);
                if (held.getDamageValue() >= held.getMaxDamage()) {
                    villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
            }
        }
    }

    private boolean moveAndInteract(Object target) {
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        BlockPos pos = (target instanceof LivingEntity e) ? e.blockPosition() : (BlockPos) target;
        return VillagerNavigationUtils.moveToOrInteract(villager, pos, PvConfig.HARVEST_REACH.get());
    }

    private boolean restockFromFarmerStorage() {
        ServerLevel level = (ServerLevel) villager.level();
        BlockPos currentPos = villager.blockPosition();
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
            if (be != null) {
                final boolean[] handled = {false};
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    if (!moveAndInteract(pos)) {
                        handled[0] = true;
                        return;
                    }
                    Item[] needed = {Items.CARROT, Items.POTATO, Items.WHEAT, Items.WHEAT_SEEDS};
                    for (Item item : needed) {
                        int has = villager.getInventory().countItem(item);
                        if (has < 12) withdrawItem(handler, item, 12 - has);
                    }
                    villager.swing(InteractionHand.MAIN_HAND);
                    handled[0] = true;
                });
                if (handled[0]) return true;
            }
        }
        return false;
    }

    private boolean depositProducts() {
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-3, -1, -3), workPos.offset(3, 1, 3))) {
            BlockEntity be = villager.level().getBlockEntity(pos);
            if (be != null) {
                final boolean[] deposited = {false};
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    if (!moveAndInteract(pos)) {
                        deposited[0] = true;
                        return;
                    }
                    SimpleContainer inv = villager.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (!stack.isEmpty() && isBreedingItem(stack.getItem()) && !(stack.getItem() instanceof TieredItem)) {
                            inv.setItem(i, ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false));
                        }
                    }
                    villager.swing(InteractionHand.MAIN_HAND);
                    deposited[0] = true;
                });
                if (deposited[0]) return true;
            }
        }
        return false;
    }

    private void collectNearbyDrops() {
        AABB area = villager.getBoundingBox().inflate(3.5);
        List<ItemEntity> items = villager.level().getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity item : items) {
            ItemStack leftover = villager.getInventory().addItem(item.getItem());
            item.setItem(leftover);
            if (leftover.isEmpty()) item.discard();
        }
    }

    private void withdrawItem(IItemHandler handler, Item item, int amountNeeded) {
        int taken = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) {
                ItemStack extracted = handler.extractItem(i, amountNeeded - taken, false);
                villager.getInventory().addItem(extracted);
                taken += extracted.getCount();
                if (taken >= amountNeeded) break;
            }
        }
    }

    private boolean needsBreedingMaterials() {
        SimpleContainer inv = villager.getInventory();
        return inv.countItem(Items.CARROT) < 4 || inv.countItem(Items.POTATO) < 4 || inv.countItem(Items.WHEAT) < 4 || inv.countItem(Items.WHEAT_SEEDS) < 4;
    }

    private int getLimitForSpecies(Class<? extends Animal> species) {
        if (species == Cow.class) return PvConfig.COW_LIMIT.get();
        if (species == Sheep.class) return PvConfig.SHEEP_LIMIT.get();
        if (species == Pig.class) return PvConfig.PIG_LIMIT.get();
        if (species == Chicken.class) return PvConfig.CHICKEN_LIMIT.get();
        if (species == Rabbit.class) return PvConfig.RABBIT_LIMIT.get();
        return PvConfig.GLOBAL_ANIMAL_LIMIT.get();
    }

    private Item getFoodForSpecies(Class<? extends Animal> species) {
        if (species == Cow.class || species == Sheep.class) return Items.WHEAT;
        if (species == Pig.class) return Items.CARROT;
        if (species == Chicken.class) return Items.WHEAT_SEEDS;
        if (species == Rabbit.class) return Items.CARROT;
        return Items.AIR;
    }

    private boolean isBreedingItem(Item item) {
        return item != Items.WHEAT && item != Items.WHEAT_SEEDS && item != Items.CARROT && item != Items.POTATO && item != Items.BEETROOT;
    }
}