package io.github.wtffidy.skuacraft.item;

import io.github.wtffidy.skuacraft.client.PlayerHostLauncher;
import io.github.wtffidy.skuacraft.client.SkuaClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.UUID;

public final class SkuaConsoleItem extends Item {
    private static final String PROFILE_ID_KEY = "SkuaAccountId";

    public SkuaConsoleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && entity instanceof Player && !stack.getOrCreateTag().hasUUID(PROFILE_ID_KEY)) {
            // The server assigns the persistent profile ID so it survives inventory sync,
            // saves, and moving the controller between a handheld and a rack.
            stack.getOrCreateTag().putUUID(PROFILE_ID_KEY, UUID.randomUUID());
        }

        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String accountId = stack.hasTag() && stack.getTag().hasUUID(PROFILE_ID_KEY)
                ? stack.getTag().getUUID(PROFILE_ID_KEY).toString()
                : PlayerHostLauncher.DEFAULT_ACCOUNT_ID;

        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkuaClientHooks.openConsole(accountId));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
