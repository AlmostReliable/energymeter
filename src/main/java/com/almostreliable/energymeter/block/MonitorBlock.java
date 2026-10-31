package com.almostreliable.energymeter.block;

import com.almostreliable.energymeter.block.entity.MonitorBlockEntity;
import com.almostreliable.energymeter.block.multiblock.MultiblockType;
import com.almostreliable.energymeter.block.multiblock.OptionalDirection;
import com.almostreliable.energymeter.block.multiblock.OptionalDirectionProperty;
import com.almostreliable.energymeter.core.Config;
import com.almostreliable.energymeter.core.Constants;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MonitorBlock extends FacingEntityBlock {

    public static final BooleanProperty CONTROLLER = BooleanProperty.create(Constants.CONTROLLER_PROP);
    public static final EnumProperty<OptionalDirection> HORIZONTAL = OptionalDirectionProperty.HORIZONTAL;
    public static final EnumProperty<OptionalDirection> VERTICAL = OptionalDirectionProperty.VERTICAL;
    public static final EnumProperty<MultiblockType> TYPE = EnumProperty.create(Constants.MULTIBLOCK_TYPE_PROP, MultiblockType.class, List.of(MultiblockType.values()));

    public MonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(
            defaultBlockState()
                .setValue(CONTROLLER, false)
                .setValue(HORIZONTAL, OptionalDirection.NONE)
                .setValue(VERTICAL, OptionalDirection.NONE)
                .setValue(TYPE, MultiblockType.NORMAL)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONTROLLER, HORIZONTAL, VERTICAL, TYPE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(CONTROLLER).equals(Boolean.TRUE)) {
            return new MonitorBlockEntity(pos, state);
        }

        return null;
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (isUnbound(state)) return null;
        BlockPos controllerPos = findControllerPos(level, pos, state);
        return super.getMenuProvider(state, level, controllerPos == null ? pos : controllerPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && !player.isShiftKeyDown()) {
            if (isUnbound(state)) {
                BlockPos controllerPos = findAndSetController(level, pos, state);
                formMonitor(level, controllerPos, player);
                return InteractionResult.SUCCESS.withoutItem();
            }
        }

        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !isUnbound(state)) {
            BlockPos controllerPos = findControllerPos(level, pos, state);
            if (controllerPos != null && level.getBlockEntity(controllerPos) instanceof MonitorBlockEntity controllerBlockEntity) {
                destroyMonitor(level, controllerPos, controllerBlockEntity, player);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    private static boolean isUnbound(BlockState state) {
        return state.getValue(CONTROLLER).equals(Boolean.FALSE) &&
            state.getValue(HORIZONTAL).isNone() &&
            state.getValue(VERTICAL).isNone();
    }

    private static boolean isBindableMonitorBlock(BlockState state, Direction facingDir, Direction bottomDir) {
        return state.getBlock() instanceof MonitorBlock &&
            isUnbound(state) &&
            state.getValue(FACING) == facingDir &&
            state.getValue(BOTTOM) == bottomDir;
    }

    private static BlockPos findAndSetController(Level level, BlockPos pos, BlockState state) {
        Direction facingDir = getFacingDir(state);
        Direction bottomDir = getBottomDir(state);
        Direction leftDir = getLeftDir(state);
        int maxWidth = Config.COMMON.maxWidth.getAsInt();
        int maxHeight = Config.COMMON.maxHeight.getAsInt();

        BlockPos controllerPos = findFurthestMonitorPosInDir(level, pos, leftDir, facingDir, bottomDir, maxWidth).pos;
        controllerPos = findFurthestMonitorPosInDir(level, controllerPos, bottomDir, facingDir, bottomDir, maxHeight).pos;

        BlockState controllerState = level.getBlockState(controllerPos);
        level.setBlock(controllerPos, controllerState.setValue(CONTROLLER, true), 1 | 2);

        return controllerPos;
    }

    private static MonitorSearchResult findFurthestMonitorPosInDir(
        Level level, BlockPos startPos, Direction direction, Direction facing, Direction bottom, int max
    ) {
        int distance = 0;
        BlockPos resultPos = startPos;
        BlockPos travelPos = startPos.relative(direction);

        while (distance < max && isBindableMonitorBlock(level.getBlockState(travelPos), facing, bottom)) {
            distance++;
            resultPos = travelPos;
            travelPos = travelPos.relative(direction);
        }

        return new MonitorSearchResult(distance, resultPos);
    }

    private static void formMonitor(Level level, BlockPos controllerPos, Player player) {
        BlockState controllerState = level.getBlockState(controllerPos);

        if (!(level.getBlockEntity(controllerPos) instanceof MonitorBlockEntity monitorBlockEntity)) {
            player.sendOverlayMessage(Component.literal("controller not found").withStyle(ChatFormatting.DARK_RED));
            level.setBlock(controllerPos, controllerState.setValue(CONTROLLER, false), 1 | 2);
            return;
        }

        Direction facingDir = getFacingDir(controllerState);
        Direction bottomDir = getBottomDir(controllerState);
        Direction topDir = bottomDir.getOpposite();
        Direction rightDir = getLeftDir(controllerState).getOpposite();
        int maxWidth = Config.COMMON.maxWidth.getAsInt();
        int maxHeight = Config.COMMON.maxHeight.getAsInt();

        int top = findFurthestMonitorPosInDir(level, controllerPos, topDir, facingDir, bottomDir, maxHeight).distance;
        int right = findFurthestMonitorPosInDir(level, controllerPos, rightDir, facingDir, bottomDir, maxWidth).distance;

        if (top == 0 && right == 0) {
            player.sendOverlayMessage(Component.literal("single monitor formed").withStyle(ChatFormatting.DARK_GREEN));
            return;
        }

        Map<BlockPos, BlockState> monitorStates = new HashMap<>();

        for (int y = 0; y <= top; y++) {
            for (int x = 0; x <= right; x++) {
                BlockPos cursor = controllerPos.relative(topDir, y).relative(rightDir, x);

                BlockState monitorState = level.getBlockState(cursor);
                if (!cursor.equals(controllerPos) && !isBindableMonitorBlock(monitorState, facingDir, bottomDir)) {
                    player.sendOverlayMessage(Component.literal("invalid multiblock").withStyle(ChatFormatting.DARK_RED));
                    level.setBlock(controllerPos, controllerState.setValue(CONTROLLER, false), 1 | 2);
                    monitorBlockEntity.setRemoved();
                    return;
                }

                monitorState = setControllerOffsetProps(monitorState, cursor, controllerPos);
                monitorState = setMultiblockTypeProps(monitorState, x, y, right, top);
                monitorStates.put(cursor, monitorState);
            }
        }

        for (var entry : monitorStates.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 1 | 2);
        }
        monitorBlockEntity.setSize(right, top);
        player.sendOverlayMessage(Component.literal("multiblock formed").withStyle(ChatFormatting.DARK_GREEN));
    }

    private void destroyMonitor(Level level, BlockPos controllerPos, MonitorBlockEntity controller, Player player) {
        int width = controller.getWidth();
        int height = controller.getHeight();
        if (width == 0 && height == 0) return;

        BlockState controllerState = controller.getBlockState();
        Direction facingDir = getFacingDir(controllerState);
        Direction bottomDir = getBottomDir(controllerState);
        Direction topDir = bottomDir.getOpposite();
        Direction rightDir = getLeftDir(controllerState).getOpposite();
        BlockPos topRight = controllerPos.relative(topDir, height).relative(rightDir, width);

        for (var pos : BlockPos.betweenClosed(controllerPos, topRight)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MonitorBlock) {
                level.setBlock(pos, defaultBlockState().setValue(FACING, facingDir).setValue(BOTTOM, bottomDir), 1 | 2);
            }
        }

        player.sendOverlayMessage(Component.literal("multiblock destroyed").withStyle(ChatFormatting.DARK_GREEN));
    }

    @Nullable
    private static BlockPos findControllerPos(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MonitorBlock)) {
            return null;
        }

        OptionalDirection horizontal = state.getValue(HORIZONTAL);
        OptionalDirection vertical = state.getValue(VERTICAL);
        boolean isController = state.getValue(CONTROLLER);

        if (isController) return pos;

        var nextPos = pos.mutable();
        horizontal.relative(nextPos);
        vertical.relative(nextPos);
        BlockState nextState = level.getBlockState(nextPos);

        return findControllerPos(level, nextPos, nextState);
    }

    private static BlockState setMultiblockTypeProps(BlockState state, int x, int y, int right, int top) {
        var type = MultiblockType.fromBorders(x == 0, x == right, y == top, y == 0);
        return state.setValue(TYPE, type);
    }

    private static BlockState setControllerOffsetProps(BlockState state, BlockPos pos, BlockPos controllerPos) {
        BlockPos horizontalVec = controllerPos.subtract(pos).atY(0);
        Direction nearestDirection = horizontalVec.equals(BlockPos.ZERO) ? null : Direction.getNearest(horizontalVec, null);
        return state
            .setValue(HORIZONTAL, OptionalDirection.fromDirection(nearestDirection))
            .setValue(VERTICAL, controllerPos.getY() < pos.getY() ? OptionalDirection.DOWN : OptionalDirection.NONE);
    }

    private record MonitorSearchResult(int distance, BlockPos pos) {}
}
