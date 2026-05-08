package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID)
public class FarmProtectionHandler {

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        // Check if the entity causing the trample is a Villager
        if (event.getEntity() instanceof Villager) {
            // Cancel the event so the farmland remains intact
            event.setCanceled(true);
        }
    }
}