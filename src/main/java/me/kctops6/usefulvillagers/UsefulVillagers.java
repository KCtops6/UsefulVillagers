package me.kctops6.usefulvillagers;

import me.kctops6.usefulvillagers.config.PvConfig;
import me.kctops6.usefulvillagers.menu.ModMenus;
import me.kctops6.usefulvillagers.network.PacketHandler;
import me.kctops6.usefulvillagers.screen.VillagerInventoryScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(UsefulVillagers.MODID)
public class UsefulVillagers {
    public static final String MODID = "usefulvillagers";
    public static final Logger LOGGER = LogManager.getLogger();

    public UsefulVillagers() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        PacketHandler.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PvConfig.SPEC);
        ModMenus.MENUS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Useful Villagers Common Setup");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.VILLAGER_INVENTORY_MENU.get(), VillagerInventoryScreen::new);
        });
    }
}