package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID)
public class ButcherAutomationHandler {

    @SubscribeEvent
    public static void onButcherTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Villager villager && !villager.level().isClientSide) {
            if (villager.tickCount % 100 == 0 && villager.getVillagerData().getProfession() == VillagerProfession.BUTCHER) {
                int level = villager.getVillagerData().getLevel();

                // Tiered duties
                if (level >= 5) harvestSweetBerries(villager);
                processRanching(villager, level);
            }
        }
    }

    private static void processRanching(Villager butcher, int level) {
        // 1. Get the Butcher's Workstation (Job Site)
        BlockPos workPos = butcher.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos).orElse(null);

        // If no workstation is assigned, they don't know where the farm is
        if (workPos == null) return;

        // 2. Define the search area (30 blocks around the workstation)
        AABB farmArea = new AABB(workPos).inflate(30);
        List<Animal> animals = butcher.level().getEntitiesOfClass(Animal.class, farmArea);
        SimpleContainer inv = butcher.getInventory();

        for (Animal animal : animals) {
            // Only breed adults that aren't already in 'love mode'
            if (animal.getAge() != 0 || animal.isInLove()) continue;

            ItemStack foodNeeded = ItemStack.EMPTY;

            // Tier 1 (Level 1): Chickens, Rabbits, Pigs
            if (level >= 1) {
                if (animal instanceof Chicken) foodNeeded = new ItemStack(Items.WHEAT_SEEDS);
                else if (animal instanceof Pig || animal instanceof Rabbit) foodNeeded = new ItemStack(Items.CARROT);
            }

            // Tier 2 (Level 4): Cows, Sheep
            if (level >= 4) {
                if (animal instanceof Cow || animal instanceof Sheep) foodNeeded = new ItemStack(Items.WHEAT);
            }

            if (!foodNeeded.isEmpty() && inv.countItem(foodNeeded.getItem()) > 0) {
                // 3. Move to animal and interact
                if (butcher.distanceToSqr(animal) > 4.0) {
                    butcher.getNavigation().moveTo(animal, 0.5D);
                } else {
                    inv.removeItemType(foodNeeded.getItem(), 1);
                    animal.setInLove(null);
                    butcher.swing(InteractionHand.MAIN_HAND);
                }
                return; // Do one action per tick check to keep it natural
            }
        }
    }

    private static void harvestSweetBerries(Villager butcher) {
        BlockPos workPos = butcher.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos).orElse(null);

        if (workPos == null) return;

        // Berries are usually closer to the workstation (scanning 10 blocks)
        for (BlockPos pos : BlockPos.betweenClosed(workPos.offset(-10, -2, -10), workPos.offset(10, 2, 10))) {
            BlockState state = butcher.level().getBlockState(pos);
            if (state.getBlock() == Blocks.SWEET_BERRY_BUSH && state.getValue(SweetBerryBushBlock.AGE) == 3) {
                if (butcher.blockPosition().closerThan(pos, 2)) {
                    butcher.level().setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), 3);
                    butcher.getInventory().addItem(new ItemStack(Items.SWEET_BERRIES, 2));
                    butcher.swing(InteractionHand.MAIN_HAND);
                } else {
                    butcher.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 0.5D);
                }
                return;
            }
        }
    }
}