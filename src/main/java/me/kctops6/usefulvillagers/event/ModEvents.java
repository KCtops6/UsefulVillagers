package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.UsefulVillagers;
import me.kctops6.usefulvillagers.client.ModKeyBindings;
import me.kctops6.usefulvillagers.menu.ModMenus;
import me.kctops6.usefulvillagers.network.OpenVillagerInvPacket;
import me.kctops6.usefulvillagers.network.PacketHandler;
import me.kctops6.usefulvillagers.screen.VillagerInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ModEvents {

    @Mod.EventBusSubscriber(modid = UsefulVillagers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModBusEvents {

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.VILLAGER_INV_KEY);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(ModMenus.VILLAGER_INVENTORY_MENU.get(), VillagerInventoryScreen::new);
            });
        }
    }

    @Mod.EventBusSubscriber(modid = UsefulVillagers.MODID, value = Dist.CLIENT)
    public static class ClientForgeEvents {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (ModKeyBindings.VILLAGER_INV_KEY.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Villager villager) {
                    PacketHandler.sendToServer(new OpenVillagerInvPacket(villager.getId()));
                }
            }
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof MerchantScreen merchantScreen) {
                int x = merchantScreen.getGuiLeft() + merchantScreen.getXSize() + 4;
                int y = merchantScreen.getGuiTop() + 4;

                Button chestButton = Button.builder(
                                Component.empty(),
                                btn -> {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.crosshairPickEntity instanceof AbstractVillager villager) {
                                        PacketHandler.sendToServer(new OpenVillagerInvPacket(villager.getId()));
                                    }
                                })
                        .bounds(x, y, 20, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("key.usefulvillagers.open_inventory")))
                        .build();

                event.addListener(chestButton);
            }
        }
    }
}