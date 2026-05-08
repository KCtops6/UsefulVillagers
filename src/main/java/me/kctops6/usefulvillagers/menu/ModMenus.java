package me.kctops6.usefulvillagers.menu;

import me.kctops6.usefulvillagers.ProductiveVillagers;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ProductiveVillagers.MODID);

    public static final RegistryObject<MenuType<VillagerInventoryMenu>> VILLAGER_INVENTORY_MENU =
            MENUS.register("villager_inventory", () -> IForgeMenuType.create((windowId, inv, data) ->
                    new VillagerInventoryMenu(windowId, inv)));
}