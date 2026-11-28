package io.github.wtffidy.skuacraft.block;

import io.github.wtffidy.skuacraft.SkuaCraft;
import io.github.wtffidy.skuacraft.block.entity.SkuaRackBlockEntity;
import io.github.wtffidy.skuacraft.client.SkuaRackClientHooks;
import io.github.wtffidy.skuacraft.item.SkuaConsoleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

public final class SkuaRackBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SkuaRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new SkuaRackBlockEntity(position, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide || type != SkuaCraft.SERVER_RACK_ENTITY.get()) {
            return null;
        }

        return (world, position, blockState, blockEntity) ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> SkuaRackClientHooks.tick((SkuaRackBlockEntity) blockEntity));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(position) instanceof SkuaRackBlockEntity rack)) {
            return InteractionResult.PASS;
        }

        ItemStack heldStack = player.getItemInHand(hand);

        if (heldStack.getItem() instanceof SkuaConsoleItem) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            if (!rack.insertController(heldStack)) {
                player.displayClientMessage(
                        Component.literal("This Skua rack is full or already contains that controller."),
                        true
                );
                return InteractionResult.FAIL;
            }

            if (!player.getAbilities().instabuild) {
                heldStack.shrink(1);
            }

            player.displayClientMessage(Component.literal("Skua controller inserted."), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown() && heldStack.isEmpty()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }

            ItemStack ejected = rack.ejectFirstController();
            if (ejected.isEmpty()) {
                player.displayClientMessage(Component.literal("The Skua rack is empty."), true);
                return InteractionResult.FAIL;
            }

            if (!player.getInventory().add(ejected)) {
                player.drop(ejected, false);
            }

            player.displayClientMessage(Component.literal("Skua controller ejected. Its session remains running."), true);
            return InteractionResult.CONSUME;
        }

        if (heldStack.isEmpty()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkuaRackClientHooks.openRack(position));
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof SkuaRackBlockEntity rack) {
                Containers.dropContents(level, position, rack);
            }
        }

        super.onRemove(state, level, position, newState, isMoving);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }
}
