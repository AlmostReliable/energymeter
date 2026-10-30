package com.almostreliable.energymeter.block;

import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.core.Constants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import org.jspecify.annotations.Nullable;

public class MeterBlock extends FacingEntityBlock {

    public static final BooleanProperty REDSTONE = BooleanProperty.create(Constants.REDSTONE_ID);

    public MeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(REDSTONE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REDSTONE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeterBlockEntity(pos, state);
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess ticks,
        BlockPos pos,
        Direction directionToNeighbour,
        BlockPos neighbourPos,
        BlockState neighbourState,
        RandomSource random
    ) {
        if (level instanceof ServerLevel serverLevel && serverLevel.getBlockEntity(pos) instanceof MeterBlockEntity meterBlockEntity) {
            meterBlockEntity.onNeighborBlockChange(directionToNeighbour);
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(REDSTONE);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!isRedstoneFace(state, direction)) return 0;

        return level.getBlockEntity(pos) instanceof MeterBlockEntity meterBlockEntity ? meterBlockEntity.getRedstoneHandler().getOutputSignal() : 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && isRedstoneFace(state, direction);
    }

    private static boolean isRedstoneFace(BlockState state, Direction direction) {
        return state.getValue(REDSTONE) && direction.getOpposite() != getFacingDir(state);
    }
}
