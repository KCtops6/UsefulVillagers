package me.kctops6.usefulvillagers.network;

import me.kctops6.usefulvillagers.UsefulVillagers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UsefulVillagers.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static void register() {
        UsefulVillagers.LOGGER.info("Registering network channel...");

        INSTANCE.messageBuilder(OpenVillagerInvPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenVillagerInvPacket::encode)
                .decoder(OpenVillagerInvPacket::decode)
                .consumerMainThread(OpenVillagerInvPacket::handle)
                .add();
    }
}