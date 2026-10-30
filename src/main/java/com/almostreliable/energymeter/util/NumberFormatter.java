package com.almostreliable.energymeter.util;

import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public final class NumberFormatter {

    private static final DecimalFormat DECIMAL_FORMAT = (DecimalFormat) NumberFormat.getInstance(Locale.getDefault());
    private static final String[] UNIT_PREFIXES = {"", "k", "M", "G", "T", "P", "E", "Z", "Y"};

    static {
        DECIMAL_FORMAT.setRoundingMode(RoundingMode.DOWN);
        DECIMAL_FORMAT.setMinimumFractionDigits(1);
        DECIMAL_FORMAT.setMaximumFractionDigits(3);
    }

    private NumberFormatter() {}

    public static FormatResult formatEnergy(double energy) {
        int index = 0;
        while (energy >= 1_000 && index < UNIT_PREFIXES.length - 1) {
            energy /= 1_000;
            index++;
        }

        String formattedEnergy = DECIMAL_FORMAT.format(energy);
        String unit = UNIT_PREFIXES[index] + "FE";

        return new FormatResult(formattedEnergy, unit);
    }

    public static String formatCompact(long value) {
        var number = BigDecimal.valueOf(value);
        for (int index = UNIT_PREFIXES.length - 1; index > 0; index--) {
            var scaled = number.movePointLeft(3 * index);
            if (scaled.abs().compareTo(BigDecimal.ONE) < 0) continue;

            var stripped = scaled.stripTrailingZeros();
            if (stripped.scale() > 2) continue;

            return stripped.toPlainString() + UNIT_PREFIXES[index];
        }

        return String.valueOf(value);
    }

    public static final class FormatResult {

        private final String energy;
        private final String unit;

        private FormatResult(String energy, String unit) {
            this.energy = energy;
            this.unit = unit;
        }

        public Component componentWithUnit() {
            return Component.literal(getEnergyWithUnit());
        }

        public Component componentWithUnitPerTick() {
            return Component.literal(energy + " " + getUnitPerTick());
        }

        public String getEnergy() {
            return energy;
        }

        public String getEnergyWithUnit() {
            return energy + " " + unit;
        }

        public String getUnitPerTick() {
            return unit + "/t";
        }
    }
}
