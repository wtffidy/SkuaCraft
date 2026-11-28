package io.github.wtffidy.skuacraft.network;

import io.github.wtffidy.skuacraft.item.SkuaConsoleItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RenameConsolePacket(InteractionHand hand, String profileName) {
    private static final int MAX_PROFILE_NAME_LENGTH = 24;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
        buffer.writeUtf(profileName, MAX_PROFILE_NAME_LENGTH);
    }

    public static RenameConsolePacket decode(FriendlyByteBuf buffer) {
        return new RenameConsolePacket(
                buffer.readEnum(InteractionHand.class),
                buffer.readUtf(MAX_PROFILE_NAME_LENGTH)
        );
    }

    public static void handle(RenameConsolePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getItemInHand(packet.hand());
            if (!(stack.getItem() instanceof SkuaConsoleItem)) {
                return;
            }

            String profileName = sanitizeProfileName(packet.profileName());
            if (profileName.isBlank()) {
                return;
            }

            stack.setHoverName(Component.literal(profileName));
            player.displayClientMessage(
                    Component.literal("Skua profile renamed to " + profileName + "."),
                    true
            );
        });
        context.setPacketHandled(true);
    }

    private static String sanitizeProfileName(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value.replaceAll("[\\p{Cc}]", "").strip();
        int codePointCount = sanitized.codePointCount(0, sanitized.length());
        if (codePointCount > MAX_PROFILE_NAME_LENGTH) {
            int endIndex = sanitized.offsetByCodePoints(0, MAX_PROFILE_NAME_LENGTH);
            sanitized = sanitized.substring(0, endIndex);
        }

        return sanitized;
    }
}
