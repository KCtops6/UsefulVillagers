package me.kctops6.usefulvillagers.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class VillagerInventoryMenu extends AbstractContainerMenu {
    private final Container villagerInventory;

    public VillagerInventoryMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(8));
    }

    public VillagerInventoryMenu(int containerId, Inventory playerInventory, Container villagerInv) {
        super(ModMenus.VILLAGER_INVENTORY_MENU.get(), containerId);
        this.villagerInventory = villagerInv;

        // 1. Villager Inventory Slots (8 slots in one row)
        // Adjusted X to start at 17 to center 8 slots in the standard 176-wide GUI
        for (int i = 0; i < 8; i++) {
            this.addSlot(new Slot(villagerInventory, i, 17 + i * 18, 20));
        }

        // 2. Player Main Inventory (3 rows of 9)
        // row * 18 ensures each row is shifted down by 18 pixels
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 51 + row * 18));
            }
        }

        // 3. Player Hotbar (1 row of 9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 109));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            // If the item is in the Villager's inventory (0-7)
            if (index < 8) {
                // Try to move to player inventory (8-44)
                if (!this.moveItemStackTo(itemstack1, 8, 44, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // If the item is in the Player's inventory (8-44)
            else {
                // Try to move to villager inventory (0-8)
                if (!this.moveItemStackTo(itemstack1, 0, 8, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}