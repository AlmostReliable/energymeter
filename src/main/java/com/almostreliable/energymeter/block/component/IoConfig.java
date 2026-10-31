package com.almostreliable.energymeter.block.component;

import com.almostreliable.energymeter.network.menu.DataHandler;
import com.almostreliable.energymeter.util.EnumExtension;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class IoConfig implements DataHandler {

    public static final int MAX_PRIORITY = 4;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final IoSetting[] IO_SETTINGS = IoSetting.values();

    private final Map<Direction, IoSettingWithPriority> directionToSetting = new EnumMap<>(Direction.class);
    private final Runnable changeListener;
    private boolean changed;

    public IoConfig(Runnable changeListener) {
        this.changeListener = changeListener;
        for (Direction direction : DIRECTIONS) {
            directionToSetting.put(direction, IoSettingWithPriority.OFF);
        }
    }

    public IoConfig() {
        this(() -> {});
    }

    public IoSettingWithPriority getSetting(Direction direction) {
        return directionToSetting.get(direction);
    }

    public void setSetting(Direction direction, IoSettingWithPriority setting) {
        if (getSetting(direction).equals(setting)) return;

        directionToSetting.put(direction, setting);
        changed = true;
        changeListener.run();
    }

    public void resetSettings() {
        for (Direction direction : DIRECTIONS) {
            directionToSetting.put(direction, IoSettingWithPriority.OFF);
        }
        changed = true;
        changeListener.run();
    }

    public void forEachOutput(Consumer<Direction> consumer) {
        for (int priority = MAX_PRIORITY; priority >= 1; priority--) {
            for (Direction direction : DIRECTIONS) {
                IoSettingWithPriority entry = directionToSetting.get(direction);
                if (entry.setting.isOutput && entry.priority == priority) {
                    consumer.accept(direction);
                }
            }
        }
    }

    public boolean hasInput() {
        for (IoSettingWithPriority entry : directionToSetting.values()) {
            if (entry.setting.isInput) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOutput() {
        for (IoSettingWithPriority entry : directionToSetting.values()) {
            if (entry.setting.isOutput) {
                return true;
            }
        }
        return false;
    }

    public void serialize(ValueOutput output) {
        for (Direction direction : DIRECTIONS) {
            IoSettingWithPriority setting = directionToSetting.get(direction);
            setting.serialize(output.child(direction.name()));
        }
    }

    public void deserialize(ValueInput input) {
        for (Direction direction : DIRECTIONS) {
            input.child(direction.name()).ifPresent(directionInput -> {
                IoSettingWithPriority setting = IoSettingWithPriority.deserialize(directionInput);
                directionToSetting.put(direction, setting);
            });
        }
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        for (Direction direction : DIRECTIONS) {
            IoSettingWithPriority setting = directionToSetting.get(direction);
            setting.encode(buffer);
        }
        changed = false;
    }

    @Override
    public void decode(FriendlyByteBuf buffer) {
        for (Direction direction : DIRECTIONS) {
            IoSettingWithPriority setting = IoSettingWithPriority.decode(buffer);
            directionToSetting.put(direction, setting);
        }
    }

    @Override
    public boolean hasChanged() {
        return changed;
    }

    public record IoSettingWithPriority(IoSetting setting, int priority) {

        public static final IoSettingWithPriority OFF = new IoSettingWithPriority(IoSetting.OFF, 1);
        public static final IoSettingWithPriority IN = new IoSettingWithPriority(IoSetting.IN, 1);
        public static final IoSettingWithPriority OUT_DEFAULT = new IoSettingWithPriority(IoSetting.OUT, 1);

        public static IoSettingWithPriority priorityOutput(int priority) {
            if (priority <= 1) {
                return OUT_DEFAULT;
            }

            return new IoSettingWithPriority(IoSetting.OUT, priority);
        }

        public boolean isDisabled() {
            return this == OFF || (!setting.isInput && !setting.isOutput);
        }

        public boolean isInput() {
            return this == IN || setting.isInput;
        }

        public boolean isOutput() {
            return this == OUT_DEFAULT || setting.isOutput;
        }

        public void serialize(ValueOutput output) {
            output.putString("setting", setting.name());
            output.putInt("priority", priority);
        }

        public static IoSettingWithPriority deserialize(ValueInput input) {
            IoSetting setting = IoSetting.valueOf(input.getStringOr("setting", IoSetting.OFF.name()));
            int priority = input.getIntOr("priority", 1);
            return new IoSettingWithPriority(setting, priority);
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putString("setting", setting.name());
            tag.putInt("priority", priority);
            return tag;
        }

        public static IoSettingWithPriority deserialize(CompoundTag tag) {
            IoSetting setting = IoSetting.valueOf(tag.getStringOr("setting", IoSetting.OFF.name()));
            int priority = tag.getIntOr("priority", 1);
            return new IoSettingWithPriority(setting, priority);
        }

        private void encode(FriendlyByteBuf buffer) {
            buffer.writeByte(setting.ordinal());
            buffer.writeByte(priority);
        }

        private static IoSettingWithPriority decode(FriendlyByteBuf buffer) {
            IoSetting setting = IO_SETTINGS[buffer.readByte()];
            int priority = buffer.readByte();
            return new IoSettingWithPriority(setting, priority);
        }

        public IoSettingWithPriority next() {
            return new IoSettingWithPriority(setting.next(), priority);
        }

        public IoSettingWithPriority previous() {
            return new IoSettingWithPriority(setting.previous(), priority);
        }
    }

    public enum IoSetting implements EnumExtension<IoSetting> {

        OFF(false, false),
        IN(true, false),
        OUT(false, true);

        private final boolean isInput;
        private final boolean isOutput;

        IoSetting(boolean isInput, boolean isOutput) {
            this.isInput = isInput;
            this.isOutput = isOutput;
        }
    }
}
