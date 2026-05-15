package me.kctops6.usefulvillagers.network;

import me.kctops6.usefulvillagers.menu.VillagerInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenVillagerInvPacket {
    private final int entityId;

    public OpenVillagerInvPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenVillagerInvPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.entityId);
    }

    public static OpenVillagerInvPacket decode(FriendlyByteBuf buffer) {
        return new OpenVillagerInvPacket(buffer.readInt());
    }

    public static void handle(OpenVillagerInvPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.level().getEntity(msg.entityId) instanceof Villager villager) {
                // Security check: only open if the player is close to the villager
                if (player.distanceToSqr(villager) < 64.0) {
                    NetworkHooks.openScreen(player, new SimpleMenuProvider(
                            (id, inv, p) -> new VillagerInventoryMenu(id, inv, villager.getInventory()),
                            Component.literal("Villager Inventory")
                    ), (buf) -> {});
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}