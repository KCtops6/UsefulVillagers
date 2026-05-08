package me.kctops6.usefulvillagers;

import me.kctops6.usefulvillagers.config.PvConfig;
import me.kctops6.usefulvillagers.menu.ModMenus;
import me.kctops6.usefulvillagers.screen.VillagerInventoryScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ProductiveVillagers.MODID)
public class ProductiveVillagers {
    public static final String MODID = "productivevillagers";

    public ProductiveVillagers() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1. Register Config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PvConfig.SPEC);

        // 2. Register Menus
        ModMenus.MENUS.register(modEventBus);

        // 3. Register Client Setup (Screens)
        modEventBus.addListener(this::clientSetup);

        // 4. Register Gameplay Events
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.VILLAGER_INVENTORY_MENU.get(), VillagerInventoryScreen::new);
        });
    }
}