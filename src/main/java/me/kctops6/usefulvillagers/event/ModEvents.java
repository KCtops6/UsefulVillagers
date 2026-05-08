package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.menu.VillagerInventoryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

@Mod.EventBusSubscriber(modid = "productivevillagers")
public class ModEvents {

    @SubscribeEvent
    public static void onVillagerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Villager villager && event.getEntity().isCrouching()) {
            if (!event.getLevel().isClientSide) {
                NetworkHooks.openScreen((ServerPlayer) event.getEntity(),
                        new SimpleMenuProvider((id, inv, p) -> new VillagerInventoryMenu(id, inv, villager.getInventory()),
                                Component.literal("Villager Inventory")), (buf) -> {});
            }
            event.setCanceled(true);
        }
    }
}