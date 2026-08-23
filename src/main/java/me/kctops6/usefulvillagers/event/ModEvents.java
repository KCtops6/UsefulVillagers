package me.kctops6.usefulvillagers.event;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import me.kctops6.usefulvillagers.client.ModKeyBindings;
import me.kctops6.usefulvillagers.network.OpenVillagerInvPacket;
import me.kctops6.usefulvillagers.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

public class ModEvents {

    @Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.VILLAGER_INV_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = ProductiveVillagers.MODID, value = Dist.CLIENT)
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

                Button chestButton = new Button.Builder(Component.empty(), btn -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.crosshairPickEntity instanceof AbstractVillager villager) {
                        PacketHandler.sendToServer(new OpenVillagerInvPacket(villager.getId()));
                    }
                })
                        .bounds(x, y, 20, 20)
                        .createNarration(supplier -> Component.empty())
                        .build();

                // Custom button subclass that renders the normal widget frame + chest item icon on top
                event.addListener(new Button(
                        chestButton.getX(),
                        chestButton.getY(),
                        chestButton.getWidth(),
                        chestButton.getHeight(),
                        chestButton.getMessage(),
                        btn -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.crosshairPickEntity instanceof AbstractVillager villager) {
                                PacketHandler.sendToServer(new OpenVillagerInvPacket(villager.getId()));
                            }
                        },
                        supplier -> Component.empty()
                ) {
                    @Override
                    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                        super.renderWidget(graphics, mouseX, mouseY, partialTick);
                        graphics.renderItem(new ItemStack(Items.CHEST), getX() + 2, getY() + 2);
                    }
                });
            }
        }
    }
}