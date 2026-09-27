package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.UsefulVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UsefulVillagers.MODID)
public class FarmProtectionHandler {

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Villager) {
            event.setCanceled(true);
        }
    }
}