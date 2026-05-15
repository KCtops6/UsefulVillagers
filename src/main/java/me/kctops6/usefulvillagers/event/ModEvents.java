package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import me.kctops6.usefulvillagers.client.ModKeyBindings;
import me.kctops6.usefulvillagers.network.OpenVillagerInvPacket;
import me.kctops6.usefulvillagers.network.PacketHandler; // Ensure you have a packet handler class
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ModEvents {

    // Register the keybinding with the game
    @Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.VILLAGER_INV_KEY);
        }
    }

    // Listen for the key press
    @Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (ModKeyBindings.VILLAGER_INV_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Villager villager) {
                    // Send packet to server to open the UI
                    PacketHandler.sendToServer(new OpenVillagerInvPacket(villager.getId()));
                }
            }
        }
    }
}