package com.almostreliable.energymeter.data;

import com.almostreliable.energymeter.ModConstants;
import com.almostreliable.energymeter.block.component.IoConfig.IoSetting;
import com.almostreliable.energymeter.block.component.RedstoneHandler.ControlBehavior;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneFunction;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneScale;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.ConnectionStatus;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.MeasureMode;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;
import com.almostreliable.energymeter.client.screen.MeterScreen.TabType;
import com.almostreliable.energymeter.client.screen.widget.IoConfigButton.BlockSide;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.data.LanguageProvider;

import com.google.common.base.CaseFormat;
import org.apache.commons.lang3.StringUtils;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public final class EnergyMeterLang extends LanguageProvider {

    // @formatter:off

    // items
    public static final LangEntry ITEM_GUIDE = LangEntry.item("guide", "Energy Meter Guide");

    // header labels
    public static final LangEntry HEADER_CURRENT = LangEntry.label("header_current", "Current");
    public static final LangEntry HEADER_TOTAL = LangEntry.label("header_total", "Total");
    public static final LangEntry HEADER_STATUS = LangEntry.label("header_status", "Status");
    public static final LangEntry HEADER_IO = LangEntry.label("header_io", "I/O");
    public static final LangEntry HEADER_MODES = LangEntry.label("header_modes", "Modes");
    public static final LangEntry HEADER_SETTINGS = LangEntry.label("header_settings", "Settings");
    public static final LangEntry HEADER_FUNCTION = LangEntry.label("header_function", "Function");
    public static final LangEntry HEADER_OUTPUT = LangEntry.label("header_output", "Output");
    public static final LangEntry HEADER_INPUT = LangEntry.label("header_input", "Input");
    public static final LangEntry HEADER_BEHAVIOR = LangEntry.label("header_behavior", "Behavior");

    // sub header labels
    public static final LangEntry SUB_HEADER_TRANSFERRING = LangEntry.label("sub_header_transferring", "Transferring");
    public static final LangEntry SUB_HEADER_MEASURING = LangEntry.label("sub_header_measuring", "Measuring");

    // setting labels
    public static final LangEntry SETTING_LIMIT = LangEntry.label("setting_limit", "Limit");
    public static final LangEntry SETTING_TOLERANCE = LangEntry.label("setting_tolerance", "Tolerance");
    public static final LangEntry SETTING_INTERVAL = LangEntry.label("setting_interval", "Interval");
    public static final LangEntry SETTING_LOWER_BOUND = LangEntry.label("setting_lower_bound", "Lower");
    public static final LangEntry SETTING_UPPER_BOUND = LangEntry.label("setting_upper_bound", "Upper");
    public static final LangEntry SETTING_OFF_BELOW = LangEntry.label("setting_off_below", "Off below");
    public static final LangEntry SETTING_ON_AT = LangEntry.label("setting_on_at", "On at");
    public static final LangEntry SETTING_SCALE = LangEntry.label("setting_scale", "Scale");
    public static final LangEntry SETTING_INVERT = LangEntry.label("setting_invert", "Invert");

    // graph labels
    public static final LangEntry GRAPH_NO_DATA = LangEntry.label("graph_no_data", "No data to display");
    public static final LangEntry GRAPH_PAUSED = LangEntry.label("graph_paused", "PAUSED");
    public static final LangEntry GRAPH_INTERVAL = LangEntry.label("graph_interval", "Interval");

    // redstone labels
    public static final LangEntry REDSTONE_SIGNAL = LangEntry.label("redstone_signal", "Signal");
    public static final LangEntry REDSTONE_STEP = LangEntry.label("redstone_step", "Per level");
    public static final LangEntry REDSTONE_ACTS_AS_THRESHOLD = LangEntry.label("redstone_acts_as_threshold", "Acts as threshold");
    public static final LangEntry REDSTONE_TRANSFER = LangEntry.label("redstone_transfer", "Transfer");
    public static final LangEntry REDSTONE_TRANSFER_ALLOWED = LangEntry.label("redstone_transfer_allowed", "Allowed");
    public static final LangEntry REDSTONE_TRANSFER_BLOCKED = LangEntry.label("redstone_transfer_blocked", "Blocked");
    public static final LangEntry REDSTONE_TRANSFER_UNLIMITED = LangEntry.label("redstone_transfer_unlimited", "Unlimited");

    // button labels
    public static final LangEntry BUTTON_RESET_TOTAL = LangEntry.button("reset_total", "Reset Total");

    // button tooltips
    public static final LangEntry BUTTON_RESET_TOTAL_TOOLTIP = LangEntry.tooltip("button_reset_total", "Pressing this button will reset the total energy transferred to zero. This is a destructive action and can't be undone.");
    public static final LangEntry BUTTON_GUIDE_MISSING_TOOLTIP = LangEntry.tooltip("button_guide_missing", "Install GuideME to see the guide.");
    public static final LangEntry BUTTON_GUIDE_OPEN_TOOLTIP = LangEntry.tooltip("button_guide_open", "Open guide");

    // text box tooltips
    public static final LangEntry TEXTBOX_MAX_TOOLTIP = LangEntry.tooltip("textbox_max", "Capped at max");

    // key tooltips
    public static final LangEntry KEY_DIRECTION = LangEntry.tooltip("key_direction", "Direction");
    public static final LangEntry KEY_CURRENT_SETTING = LangEntry.tooltip("key_current_setting", "Current Setting");
    public static final LangEntry KEY_OUTPUT_PRIORITY = LangEntry.tooltip("key_output_priority", "Output Priority");

    // control key tooltips
    public static final LangEntry CONTROL_KEY_LMB = LangEntry.tooltip("control_key_lmb", "Click");
    public static final LangEntry CONTROL_KEY_RMB = LangEntry.tooltip("control_key_rmb", "Right-Click");
    public static final LangEntry CONTROL_KEY_SHIFT = LangEntry.tooltip("control_key_shift", "Shift");

    // control description tooltips
    public static final LangEntry CYCLE_NEXT_SETTING = LangEntry.tooltip("control_desc_cycle_next_setting", "Next setting");
    public static final LangEntry CYCLE_PREVIOUS_SETTING = LangEntry.tooltip("control_desc_cycle_previous_setting", "Previous setting");
    public static final LangEntry SELECT_SETTING = LangEntry.tooltip("control_desc_select_setting", "Select setting");
    public static final LangEntry RESET_SETTING = LangEntry.tooltip("control_desc_reset_setting", "Reset setting");
    public static final LangEntry RESET_ALL_SETTINGS = LangEntry.tooltip("control_desc_reset_all_settings", "Reset all settings");

    // enums
    public static final Map<BlockSide, LangEntry> BLOCK_SIDES = LangEntry.enumValues(BlockSide.class, BlockSide.values());
    public static final Map<Direction, LangEntry> DIRECTIONS = LangEntry.enumValues(Direction.class, Direction.values());
    public static final Map<IoSetting, LangEntry> IO_SETTINGS = LangEntry.enumValues(IoSetting.class, IoSetting.values());
    public static final Map<TabType, LangEntry> TAB_TYPE = LangEntry.enumValues(TabType.class, TabType.values());
    public static final Map<TransferMode, LangEntry> TRANSFER_MODES = LangEntry.enumValues(TransferMode.class, TransferMode.values());
    public static final Map<MeasureMode, LangEntry> MEASURE_MODES = LangEntry.enumValues(MeasureMode.class, MeasureMode.values());
    public static final Map<ConnectionStatus, LangEntry> CONNECTION_STATUSES = LangEntry.enumValues(ConnectionStatus.class, ConnectionStatus.values());
    public static final Map<RedstoneFunction, LangEntry> REDSTONE_FUNCTIONS = LangEntry.enumValues(RedstoneFunction.class, RedstoneFunction.values());
    public static final Map<RedstoneScale, LangEntry> REDSTONE_SCALES = LangEntry.enumValues(RedstoneScale.class, RedstoneScale.values());
    public static final Map<ControlBehavior, LangEntry> CONTROL_BEHAVIORS = LangEntry.enumValues(ControlBehavior.class, ControlBehavior.values());

    // @formatter:on

    EnergyMeterLang(PackOutput output) {
        super(output, ModConstants.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        for (LangEntry entry : LangEntry.ENTRIES) {
            add(entry.key, entry.value);
        }
    }

    public record LangEntry(String key, String value) implements Supplier<MutableComponent> {

        private static final Set<LangEntry> ENTRIES = new HashSet<>();

        public static LangEntry of(String prefix, String id, String value) {
            LangEntry entry = new LangEntry(String.format("%s.%s.%s", prefix, ModConstants.MOD_ID, id), value);
            ENTRIES.add(entry);
            return entry;
        }

        public static LangEntry item(String id, String value) {
            return of("item", id, value);
        }

        private static LangEntry label(String id, String value) {
            return of("label", id, value);
        }

        private static LangEntry button(String id, String value) {
            return of("button", id, value);
        }

        private static LangEntry tooltip(String id, String value) {
            return of("tooltip", id, value);
        }

        @SafeVarargs
        private static <T extends Enum<T>> EnumMap<T, LangEntry> enumValues(Class<T> clazz, T... enumValues) {
            var enumClassName = clazz.getSimpleName();
            String prefix = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, enumClassName);

            var enumEntries = new EnumMap<T, LangEntry>(clazz);
            for (T enumValue : enumValues) {
                String id = enumValue.name().toLowerCase(Locale.ROOT);
                String value = StringUtils.capitalize(id);
                enumEntries.put(enumValue, of(prefix, id, value));
            }

            return enumEntries;
        }

        @Override
        public MutableComponent get() {
            return Component.translatable(key, value);
        }
    }
}
