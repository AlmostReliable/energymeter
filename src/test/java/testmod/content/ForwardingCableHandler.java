package testmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import testmod.TestRegistration;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;

public record ForwardingCableHandler(Level level, BlockPos pos, @Nullable Direction side) implements EnergyHandler {

    @Override
    public int insert(int amount, TransactionContext transaction) {
        int inserted = 0;

        for (EnergyHandler target : findTargets()) {
            if (inserted >= amount) break;
            inserted += target.insert(amount - inserted, transaction);
        }

        return inserted;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public long getAmountAsLong() {
        return sumTargets(EnergyHandler::getAmountAsLong);
    }

    @Override
    public long getCapacityAsLong() {
        return sumTargets(EnergyHandler::getCapacityAsLong);
    }

    private List<EnergyHandler> findTargets() {
        List<EnergyHandler> targets = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(pos);
        queue.add(pos);

        while (!queue.isEmpty()) {
            BlockPos cablePos = queue.poll();

            for (Direction direction : Direction.values()) {
                // skip the block that inserts into the network
                if (cablePos.equals(pos) && direction == side) continue;

                BlockPos neighborPos = cablePos.relative(direction);
                if (level.getBlockState(neighborPos).is(TestRegistration.ENERGY_CABLE_BLOCK.get())) {
                    if (visited.add(neighborPos)) queue.add(neighborPos);
                    continue;
                }

                EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, neighborPos, direction.getOpposite());
                if (target != null) targets.add(target);
            }
        }

        return targets;
    }

    private long sumTargets(ToLongFunction<EnergyHandler> energyGetter) {
        long result = 0;

        for (EnergyHandler target : findTargets()) {
            long value = energyGetter.applyAsLong(target);
            if (value <= 0) continue;
            if (value > Long.MAX_VALUE - result) return Long.MAX_VALUE;
            result += value;
        }

        return result;
    }
}
