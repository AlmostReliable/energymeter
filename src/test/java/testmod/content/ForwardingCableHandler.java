package testmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import testmod.TestRegistration;

import com.google.common.primitives.Ints;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

public record ForwardingCableHandler(Level level, BlockPos pos, @Nullable Direction side) implements IEnergyStorage {

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        int inserted = 0;

        for (IEnergyStorage target : findTargets()) {
            if (inserted >= amount) break;
            inserted += target.receiveEnergy(amount - inserted, simulate);
        }

        return inserted;
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return sumTargets(IEnergyStorage::getEnergyStored);
    }

    @Override
    public int getMaxEnergyStored() {
        return sumTargets(IEnergyStorage::getMaxEnergyStored);
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    private List<IEnergyStorage> findTargets() {
        List<IEnergyStorage> targets = new ArrayList<>();
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

                IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    neighborPos,
                    direction.getOpposite()
                );
                if (target != null) targets.add(target);
            }
        }

        return targets;
    }

    private int sumTargets(ToIntFunction<IEnergyStorage> energyGetter) {
        long result = 0;

        for (IEnergyStorage target : findTargets()) {
            int value = energyGetter.applyAsInt(target);
            if (value <= 0) continue;
            result += value;
        }

        return Ints.saturatedCast(result);
    }
}
