package me.kctops6.usefulvillagers.screen;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import com.mojang.blaze3d.systems.RenderSystem;
import me.kctops6.usefulvillagers.menu.VillagerInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class VillagerInventoryScreen extends AbstractContainerScreen<VillagerInventoryMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ProductiveVillagers.MODID, "textures/gui/villager_inventory.png");

    public VillagerInventoryScreen(VillagerInventoryMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 133; // Adjusted for a smaller GUI
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        renderTooltip(graphics, mouseX, mouseY);
    }
}