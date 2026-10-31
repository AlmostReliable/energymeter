package com.almostreliable.energymeter.core;

@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
public interface Constants {

    // ids
    String METER_ID = "meter";
    String MONITOR_ID = "monitor";

    // properties
    String BOTTOM_PROP = "bottom";
    String HORIZONTAL_PROP = "horizontal";
    String VERTICAL_PROP = "vertical";
    String CONTROLLER_PROP = "controller";
    String MULTIBLOCK_TYPE_PROP = "multiblock_type";

    // serialization
    String SIDE_CONFIG_ID = "side_config";
    String TRANSFER_MODE_ID = "transfer_mode";
    String MEASURE_MODE_ID = "measure_mode";
    String MEASURE_INTERVAL_ID = "measure_interval";
    String ZERO_TOLERANCE_ID = "zero_tolerance";
    String TRANSFER_LIMIT_ID = "transfer_limit";
    String ENERGY_RATE_ID = "energy_rate";
    String TOTAL_ENERGY_ID = "total_energy";
    String REDSTONE_ID = "redstone";
    String MULTIBLOCK_WIDTH_ID = "width";
    String MULTIBLOCK_HEIGHT_ID = "height";
    String FUNCTION_ID = "function";
    String LOWER_BOUND_ID = "lower";
    String UPPER_BOUND_ID = "upper";
    String SCALE_ID = "scale";
    String CONTROL_BEHAVIOR_ID = "control_behavior";
    String INVERTED_ID = "inverted";
    String THRESHOLD_ACTIVE_ID = "threshold_active";
    String OUTPUT_SIGNAL_ID = "output_signal";

    // mods
    String GUIDE_ME = "guideme";

    // colors
    int COLOR_ACCENT = 0xFF00_FFA2;
    int COLOR_BACKGROUND = 0xFF17_1717;
    int COLOR_GRAY = 0xFFBF_BFBF;
    int COLOR_RED = 0xFFFF_0000;
    int COLOR_WHITE = 0xFFFF_FFFF;
}
