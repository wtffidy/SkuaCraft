package io.github.wtffidy.skuacraft.block.entity;

import io.github.wtffidy.skuacraft.SkuaCraft;
import io.github.wtffidy.skuacraft.item.SkuaConsoleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SkuaRackBlockEntity extends BlockEntity implements Container {
    public static final int SLOT_COUNT = 6;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public SkuaRackBlockEntity(BlockPos position, BlockState state) {
        super(SkuaCraft.SERVER_RACK_ENTITY.get(), position, state);
    }

    public boolean insertController(ItemStack controller) {
        if (controller.isEmpty() || !(controller.getItem() instanceof SkuaConsoleItem)) {
            return false;
        }

        String accountId = SkuaConsoleItem.assignAccountId(controller);
        for (ItemStack stored : items) {
            if (!stored.isEmpty() && accountId.equals(SkuaConsoleItem.getAccountId(stored))) {
                return false;
            }
        }

        for (int slot = 0; slot < items.size(); slot++) {
            if (items.get(slot).isEmpty()) {
                items.set(slot, controller.copyWithCount(1));
                setChanged();
                return true;
            }
        }

        return false;
    }

    public ItemStack ejectFirstController() {
        for (int slot = 0; slot < items.size(); slot++) {
            if (!items.get(slot).isEmpty()) {
                return ejectController(slot);
            }
        }

        return ItemStack.EMPTY;
    }

    public ItemStack ejectController(int slot) {
        if (slot < 0 || slot >= items.size() || items.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack ejected = ContainerHelper.takeItem(items, slot);
        setChanged();
        return ejected;
    }

    public List<AccountSlot> getAccountSlots() {
        java.util.ArrayList<AccountSlot> slots = new java.util.ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            String accountId = stack.isEmpty() ? "" : SkuaConsoleItem.getAccountId(stack);
            slots.add(new AccountSlot(slot, accountId));
        }

        return List.copyOf(slots);
    }

    public List<String> getAccountIds() {
        Set<String> accountIds = new LinkedHashSet<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.getItem() instanceof SkuaConsoleItem) {
                accountIds.add(SkuaConsoleItem.getAccountId(stack));
            }
        }

        return List.copyOf(accountIds);
    }

    public record AccountSlot(int slot, String accountId) {
        public boolean isEmpty() {
            return accountId == null || accountId.isBlank();
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }

        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }

        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }

        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < items.size() && stack.getItem() instanceof SkuaConsoleItem;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
