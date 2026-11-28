package io.github.wtffidy.skuacraft.network;

import io.github.wtffidy.skuacraft.block.entity.SkuaRackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RackEjectPacket(BlockPos position, int slot) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeVarInt(slot);
    }

    public static RackEjectPacket decode(FriendlyByteBuf buffer) {
        return new RackEjectPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(RackEjectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || packet.slot < 0 || packet.slot >= SkuaRackBlockEntity.SLOT_COUNT) {
                return;
            }

            if (!(player.level().getBlockEntity(packet.position) instanceof SkuaRackBlockEntity rack)
                    || !rack.stillValid(player)) {
                return;
            }

            ItemStack ejected = rack.ejectController(packet.slot);
            if (!ejected.isEmpty() && !player.getInventory().add(ejected)) {
                player.drop(ejected, false);
            }
        });
        context.setPacketHandled(true);
    }
}
