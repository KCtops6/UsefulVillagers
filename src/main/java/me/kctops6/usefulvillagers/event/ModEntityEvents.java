package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.UsefulVillagers;
import me.kctops6.usefulvillagers.ai.ButcherWorkGoal;
import me.kctops6.usefulvillagers.ai.FarmerWorkGoal;
import me.kctops6.usefulvillagers.ai.ShepherdWorkGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UsefulVillagers.MODID)
public class ModEntityEvents {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            boolean hasGoal = villager.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof FarmerWorkGoal);

            if (!hasGoal) {
                villager.goalSelector.addGoal(3, new FarmerWorkGoal(villager));
                villager.goalSelector.addGoal(3, new ShepherdWorkGoal(villager));
                villager.goalSelector.addGoal(3, new ButcherWorkGoal(villager));
            }
        }
    }
}