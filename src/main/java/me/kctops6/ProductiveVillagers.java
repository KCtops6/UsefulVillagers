package me.kctops6.usefulvillagers;

import me.kctops6.usefulvillagers.config.PvConfig;
import me.kctops6.usefulvillagers.menu.ModMenus;
import me.kctops6.usefulvillagers.network.OpenVillagerInvPacket;
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

@Mod(ProductiveVillagers.MODID)
public class ProductiveVillagers {
    public static final String MODID = "productivevillagers";
    public static final Logger LOGGER = LogManager.getLogger();

    public ProductiveVillagers() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1. Initialize Network immediately to prevent "Locked Channel" crashes
        PacketHandler.register();
        PacketHandler.INSTANCE.messageBuilder(OpenVillagerInvPacket.class, 0)
                .encoder(OpenVillagerInvPacket::encode)
                .decoder(OpenVillagerInvPacket::decode)
                .consumerMainThread(OpenVillagerInvPacket::handle)
                .add();

        // 2. Register Config and Menus
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PvConfig.SPEC);
        ModMenus.MENUS.register(modEventBus);

        // 3. Register Lifecycle Listeners
        modEventBus.addListener(this::commonSetup); // Now resolved by method below
        modEventBus.addListener(this::clientSetup);

        // 4. Register Gameplay Events
        MinecraftForge.EVENT_BUS.register(this);
    }

    // This is the method that was missing, causing your compiler error
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Productive Villagers Common Setup");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.VILLAGER_INVENTORY_MENU.get(), VillagerInventoryScreen::new);
        });
    }
}