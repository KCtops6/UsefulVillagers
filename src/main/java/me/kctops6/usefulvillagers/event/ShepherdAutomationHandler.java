package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import me.kctops6.usefulvillagers.config.PvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

@Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID)
public class ShepherdAutomationHandler {

    @SubscribeEvent
    public static void onShepherdTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) return;

        if (villager.tickCount % 20 == 0 && villager.getVillagerData().getProfession() == VillagerProfession.SHEPHERD) {

            boolean performedAction = tryShearingSheep(villager);

            if (!performedAction) {
                performedAction = depositWool(villager);
            }

            if (!performedAction) {
                goToWorkstation(villager);
            }
        }
    }

    private static boolean tryShearingSheep(Villager villager) {
        // Check for shears requirement
        if (PvConfig.SHEPHERD_NEEDS_SHEARS.get() && villager.getInventory().countItem(Items.SHEARS) <= 0) {
            return false;
        }

        ServerLevel level = (ServerLevel) villager.level();
        BlockPos workPos = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
        if (workPos == null) return false;

        int range = PvConfig.HARVEST_RANGE.get();
        AABB area = new AABB(workPos).inflate(range);
        List<Sheep> sheepList = level.getEntitiesOfClass(Sheep.class, area, sheep -> sheep.readyForShearing() && !sheep.isBaby());

        if (!sheepList.isEmpty()) {
            Sheep target = sheepList.get(0);
            if (moveAndAction(villager, target.blockPosition())) {
                // Perform the shearing
                villager.swing(InteractionHand.MAIN_HAND);

                // Use the sheep's built-on onSheared method to ensure vanilla drop rates (1-3 wool)
                List<ItemStack> drops = target.onSheared(null, ItemStack.EMPTY, level, target.blockPosition(), 0);
                for (ItemStack stack : drops) {
                    ItemStack leftover = villager.getInventory().addItem(stack);
                    if (!leftover.isEmpty()) target.spawnAtLocation(leftover);
                }

                // Handle Shear durability if required
                if (PvConfig.SHEPHERD_NEEDS_SHEARS.get()) {
                    damageShears(villager);
                }
            }
            return true;
        }
        return false;
    }

    private static void damageShears(Villager villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.SHEARS)) {
                stack.setDamageValue(stack.getDamageValue() + 1);
                if (stack.getDamageValue() >= stack.getMaxDamage()) {
                    stack.shrink(1);
                }
                break;
            }
        }
    }

    private static boolean depositWool(Villager villager) {
        ServerLevel level = (ServerLevel) villager.level();
        SimpleContainer inv = villager.getInventory();

        // Only move to chest if we actually have wool
        boolean hasWool = false;
        for(int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem().getDescriptionId().contains("wool")) {
                hasWool = true;
                break;
            }
        }
        if (!hasWool) return false;

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
                        if (!stack.isEmpty() && stack.getItem().getDescriptionId().contains("wool")) {
                            ItemStack leftover = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
                            stack.setCount(leftover.getCount());
                        }
                    }
                });
                villager.swing(InteractionHand.MAIN_HAND);
            }
            return true;
        }
        return false;
    }

    private static boolean moveAndAction(Villager villager, BlockPos target) {
        double reach = PvConfig.SHEPHERD_REACH.get();
        double distSq = villager.blockPosition().distSqr(target);

        if (distSq > (reach * reach)) {
            villager.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.5D);
            return false;
        }
        villager.getNavigation().stop();
        return true;
    }

    private static void goToWorkstation(Villager villager) {
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(globalPos -> {
            BlockPos workPos = globalPos.pos();
            if (villager.blockPosition().distSqr(workPos) > 1.5) {
                villager.getNavigation().moveTo(workPos.getX(), workPos.getY(), workPos.getZ(), 0.5D);
            } else {
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        });
    }
}