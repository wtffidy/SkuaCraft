package io.github.wtffidy.skuacraft.network;

import io.github.wtffidy.skuacraft.block.entity.SkuaRackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RackSelectPacket(BlockPos position, int slot) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeVarInt(slot);
    }

    public static RackSelectPacket decode(FriendlyByteBuf buffer) {
        return new RackSelectPacket(buffer.readBlockPos(), buffer.readVarInt());
    }

    public static void handle(RackSelectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || packet.slot < 0 || packet.slot >= SkuaRackBlockEntity.SLOT_COUNT) {
                return;
            }

            if (!(player.level().getBlockEntity(packet.position) instanceof SkuaRackBlockEntity rack)
                    || !rack.isOwner(player) || !rack.stillValid(player)) {
                return;
            }

            rack.setSelectedSlot(packet.slot);
        });
        context.setPacketHandled(true);
    }
}
