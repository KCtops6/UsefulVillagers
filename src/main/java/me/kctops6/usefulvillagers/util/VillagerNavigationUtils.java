package me.kctops6.usefulvillagers.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

public class VillagerNavigationUtils {

    /**
     * Navigates a villager to their assigned job site workstation.
     */
    public static void goToWorkstation(Villager villager) {
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

    /**
     * Checks distance to a target position and handles navigation or stopping based on reach distance.
     *
     * @return true if the villager is within interaction reach, false if moving towards it.
     */
    public static boolean moveToOrInteract(Villager villager, BlockPos target, double reachDistance) {
        if (villager.blockPosition().distSqr(target) > (reachDistance * reachDistance)) {
            villager.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.6D);
            return false;
        }
        villager.getNavigation().stop();
        return true;
    }
}