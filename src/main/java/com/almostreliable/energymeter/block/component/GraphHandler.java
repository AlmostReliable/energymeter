package com.almostreliable.energymeter.block.component;

import com.almostreliable.energymeter.network.menu.DataHandler;
import com.almostreliable.energymeter.util.NumberFormatter;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import com.google.common.collect.EvictingQueue;

public class GraphHandler implements DataHandler {

    public static final int HISTORY_SIZE = 11;

    private final EvictingQueue<Double> energyRateHistory = EvictingQueue.create(HISTORY_SIZE);

    private PointSnapshot snapshot = PointSnapshot.EMPTY;
    private long lastSnapShotTime;
    private boolean newRateAfterSnapshot;
    private boolean paused;
    private boolean needsSyncing;
    private float progress;

    public void clear() {
        energyRateHistory.clear();
        snapshot = PointSnapshot.EMPTY;
        lastSnapShotTime = 0;
        newRateAfterSnapshot = false;
        paused = false;
        needsSyncing = true;
    }

    public void togglePause() {
        paused = !paused;
    }

    public void trackEnergyRate(double energyRate) {
        energyRateHistory.add(energyRate);
        newRateAfterSnapshot = true;
    }

    public void tick(long gameTime, long measureInterval) {
        if (paused) return;
        recomputePointSnapshot(gameTime);
        recomputeProgress(gameTime, measureInterval);
    }

    private void recomputePointSnapshot(long gameTime) {
        if (!newRateAfterSnapshot) return;

        double maxEnergyRate = 0;
        for (var val : energyRateHistory) {
            if (val > maxEnergyRate) {
                maxEnergyRate = val;
            }
        }
        var energyRateUpperBound = maxEnergyRate * 1.1;
        var yMax = maxEnergyRate > 0 ? energyRateUpperBound : 1.0;
        var yLabel = energyRateUpperBound > 0 ? NumberFormatter.formatEnergy(energyRateUpperBound).getEnergyWithUnit() : "";

        var size = energyRateHistory.size();
        var graphPoints = new GraphPoint[size];
        var pointIndex = 0;
        for (var energyRate : energyRateHistory) {
            var xNormalized = size == 1 ? 0.5f : (float) pointIndex / (size - 1);
            var yNormalized = (float) (energyRate / yMax);
            graphPoints[pointIndex] = new GraphPoint(
                xNormalized,
                yNormalized,
                NumberFormatter.formatEnergy(energyRate).getEnergyWithUnit()
            );
            pointIndex++;
        }

        snapshot = new PointSnapshot(graphPoints, yMax, yLabel);
        lastSnapShotTime = gameTime;
        newRateAfterSnapshot = false;
        needsSyncing = true;
    }

    private void recomputeProgress(long gameTime, long measureInterval) {
        float newProgress = (gameTime - lastSnapShotTime) / (float) measureInterval;
        progress = Mth.clamp(newProgress, 0, 1);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(snapshot != PointSnapshot.EMPTY);
        if (snapshot != PointSnapshot.EMPTY) {
            snapshot.encode(buffer);
        }
        needsSyncing = false;
    }

    @Override
    public void decode(FriendlyByteBuf buffer) {
        boolean hasSnapshot = buffer.readBoolean();
        if (hasSnapshot) {
            snapshot = PointSnapshot.decode(buffer);
        }
    }

    @Override
    public boolean hasChanged() {
        return needsSyncing;
    }

    public GraphPoint[] getPoints() {
        return snapshot.points;
    }

    public double getYMax() {
        return snapshot.yMax;
    }

    public String getYLabel() {
        return snapshot.yLabel;
    }

    public float getProgress() {
        return progress;
    }

    public boolean isPaused() {
        return paused;
    }

    public record GraphPoint(float x, float y, String label) {

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeFloat(x);
            buffer.writeFloat(y);
            buffer.writeUtf(label);
        }

        private static GraphPoint decode(FriendlyByteBuf buffer) {
            return new GraphPoint(buffer.readFloat(), buffer.readFloat(), buffer.readUtf());
        }
    }

    private record PointSnapshot(GraphPoint[] points, double yMax, String yLabel) {

        private static final PointSnapshot EMPTY = new PointSnapshot(new GraphPoint[0], 1.0, "");

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeInt(points.length);
            for (var point : points) {
                point.encode(buffer);
            }
            buffer.writeDouble(yMax);
            buffer.writeUtf(yLabel);
        }

        private static PointSnapshot decode(FriendlyByteBuf buffer) {
            int count = buffer.readInt();
            var points = new GraphPoint[count];
            for (int i = 0; i < count; i++) {
                points[i] = GraphPoint.decode(buffer);
            }

            var yMax = buffer.readDouble();
            var yLabel = buffer.readUtf();
            return new PointSnapshot(points, yMax, yLabel);
        }
    }
}
