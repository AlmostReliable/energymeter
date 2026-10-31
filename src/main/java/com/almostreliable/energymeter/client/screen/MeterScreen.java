package com.almostreliable.energymeter.client.screen;

import com.almostreliable.energymeter.block.component.GraphHandler;
import com.almostreliable.energymeter.block.component.GraphHandler.GraphPoint;
import com.almostreliable.energymeter.block.component.IoConfig.IoSettingWithPriority;
import com.almostreliable.energymeter.block.component.RedstoneHandler.ControlBehavior;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneFunction;
import com.almostreliable.energymeter.block.component.RedstoneHandler.RedstoneScale;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.MeasureMode;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity.TransferMode;
import com.almostreliable.energymeter.client.screen.layout.InputLayoutElement;
import com.almostreliable.energymeter.client.screen.widget.DynamicMarqueeStringWidget;
import com.almostreliable.energymeter.client.screen.widget.GuideButton;
import com.almostreliable.energymeter.client.screen.widget.IoConfigButton;
import com.almostreliable.energymeter.client.screen.widget.IoConfigButton.SettingSelectorWidget;
import com.almostreliable.energymeter.client.screen.widget.RadioButton;
import com.almostreliable.energymeter.client.screen.widget.Checkbox;
import com.almostreliable.energymeter.client.screen.widget.TabButton;
import com.almostreliable.energymeter.client.screen.widget.base.ClickedOutsideListener;
import com.almostreliable.energymeter.client.screen.widget.base.OutlinedCompositeWidget;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.data.EnergyMeterLang;
import com.almostreliable.energymeter.menu.MeterMenu;
import com.almostreliable.energymeter.mixin.GuiGraphicsExtractorAccessor;
import com.almostreliable.energymeter.network.action.ClientActionRegistry;
import com.almostreliable.energymeter.network.action.EnumClientAction;
import com.almostreliable.energymeter.network.action.IoSettingClientAction;
import com.almostreliable.energymeter.network.action.SimpleClientAction;
import com.almostreliable.energymeter.network.action.TextValueClientAction;
import com.almostreliable.energymeter.util.NumberFormatter;
import com.almostreliable.energymeter.util.TexRenderer;
import com.almostreliable.energymeter.util.TooltipBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.redstone.Redstone;

import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class MeterScreen extends SynchronizedContainerScreen<MeterMenu> {

    private static final int GUI_WIDTH = 234;
    private static final int GUI_HEIGHT = 185;
    private static final int PANE_SPACING = 4;
    private static final int LEFT_PANE_WIDTH = 90;
    private static final int RIGHT_PANE_WIDTH = GUI_WIDTH - LEFT_PANE_WIDTH - PANE_SPACING;
    private static final int VERTICAL_ELEMENT_SPACING = 4;
    private static final int GLOBAL_INFO_WIDTH = 70;
    private static final int GRAPH_PADDING = 12;
    private static final int GRAPH_MAX_VALUE_INSET = 6;
    private static final int REDSTONE_ELEMENT_WIDTH = 110;
    private static final TexRenderer BACKGROUND = TexRenderer.gui("meter").tex(0, 0, GUI_WIDTH, GUI_HEIGHT);

    private final List<ClickedOutsideListener> clickedOutsideListeners = new ArrayList<>();
    private TabType currentTab = TabType.CONFIGURATION;
    private RedstoneFunction lastRedstoneFunction = RedstoneFunction.OFF;
    private ControlBehavior lastControlBehavior = ControlBehavior.SWITCH;
    private @Nullable SettingSelectorWidget settingSelectorWidget;

    public MeterScreen(MeterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        initGlobalInfo();
        initTabs();
        currentTab.init.accept(this);
    }

    private void initGlobalInfo() {
        var energyRateComposite = OutlinedCompositeWidget.ofElement(
            EnergyMeterLang.HEADER_CURRENT.get(),
            new DynamicMarqueeStringWidget(
                GLOBAL_INFO_WIDTH,
                () -> NumberFormatter.formatEnergy(menu.getEnergyRate()).componentWithUnitPerTick()
            )
        );

        var totalEnergyComposite = OutlinedCompositeWidget.ofElement(
            EnergyMeterLang.HEADER_TOTAL.get(),
            new DynamicMarqueeStringWidget(GLOBAL_INFO_WIDTH, () -> NumberFormatter.formatEnergy(menu.getTotalEnergy()).componentWithUnit())
        );

        var statusComposite = OutlinedCompositeWidget.ofElement(
            EnergyMeterLang.HEADER_STATUS.get(),
            new DynamicMarqueeStringWidget(
                GLOBAL_INFO_WIDTH,
                () -> EnergyMeterLang.CONNECTION_STATUSES.get(menu.getConnectionStatus()).get()
            )
        );

        var ioConfigGroup = IoConfigButton.createGroup(
            menu.getBlockState(),
            menu::getIoSetting,
            this::onIoSettingSelected,
            menu::getTransferMode
        );
        var ioConfigComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.HEADER_IO.get(), ioConfigGroup.layout());
        ioConfigComposite.setMinWidth(GLOBAL_INFO_WIDTH);

        var resetButton = Button.builder(EnergyMeterLang.BUTTON_RESET_TOTAL.get(), _ -> onResetTotalButtonClicked())
            .width(GLOBAL_INFO_WIDTH + 8)
            .tooltip(TooltipBuilder.create().literal(EnergyMeterLang.BUTTON_RESET_TOTAL_TOOLTIP.get()).build())
            .build();

        var layout = LinearLayout.vertical().spacing(VERTICAL_ELEMENT_SPACING);
        layout.addChild(energyRateComposite);
        layout.addChild(totalEnergyComposite);
        layout.addChild(statusComposite);
        layout.addChild(ioConfigComposite);
        layout.addChild(resetButton);

        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, leftPos, topPos, LEFT_PANE_WIDTH, GUI_HEIGHT);

        layout.visitWidgets(this::addRenderableWidget);
        settingSelectorWidget = addWidget(ioConfigGroup.settingSelectorWidget());
        addRenderableWidget(new GuideButton(leftPos, topPos));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (currentTab == TabType.REDSTONE && hasRedstoneLayoutChanged()) {
            rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (settingSelectorWidget != null) {
            settingSelectorWidget.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void initTabs() {
        LinearLayout layout = LinearLayout.horizontal().spacing(1);

        for (TabType tabType : TabType.values()) {
            layout.addChild(new TabButton(tabType, currentTab, this::onTabButtonClicked));
        }

        layout.arrangeElements();
        FrameLayout.alignInRectangle(
            layout,
            leftPos + LEFT_PANE_WIDTH + PANE_SPACING,
            topPos - TabButton.TAB_HEIGHT + 1,
            RIGHT_PANE_WIDTH,
            TabButton.TAB_HEIGHT,
            0.1f,
            0
        );

        layout.visitWidgets(this::addRenderableWidget);
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void initConfigTab() {
        var transferModeRadioButtons = RadioButton.createGroup(
            102,
            EnergyMeterLang.TRANSFER_MODES,
            menu::getTransferMode,
            this::onTransferModeSelected
        );
        var transferModeComposite = OutlinedCompositeWidget.ofLayout(
            EnergyMeterLang.SUB_HEADER_TRANSFERRING.get(),
            transferModeRadioButtons
        );

        var measureModeRadioButtons = RadioButton.createGroup(
            102,
            EnergyMeterLang.MEASURE_MODES,
            menu::getMeasureMode,
            this::onMeasureModeSelected
        );
        var measureModeComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.SUB_HEADER_MEASURING.get(), measureModeRadioButtons);

        var modesLayout = LinearLayout.vertical().spacing(VERTICAL_ELEMENT_SPACING);
        modesLayout.addChild(transferModeComposite);
        modesLayout.addChild(measureModeComposite);
        var modesComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.HEADER_MODES.get(), modesLayout, Constants.COLOR_GRAY);

        var settingsLayout = LinearLayout.vertical().spacing(1);
        settingsLayout.addChild(new InputLayoutElement<>(
            MeterTextBoxType.TRANSFER_LIMIT,
            110,
            font,
            EnergyMeterLang.SETTING_LIMIT.get().append(":"),
            () -> NumberFormatter.formatCompact(menu.getTransferLimit()),
            this::onTextValueUpdated
        ));
        settingsLayout.addChild(new InputLayoutElement<>(
            MeterTextBoxType.MEASURE_INTERVAL,
            110,
            font,
            EnergyMeterLang.SETTING_INTERVAL.get().append(":"),
            () -> String.valueOf(menu.getMeasureInterval()),
            this::onTextValueUpdated
        ).withMaxValue(Integer.MAX_VALUE));
        settingsLayout.addChild(new InputLayoutElement<>(
            MeterTextBoxType.ZERO_TOLERANCE,
            110,
            font,
            EnergyMeterLang.SETTING_TOLERANCE.get().append(":"),
            () -> String.valueOf(menu.getZeroTolerance()),
            this::onTextValueUpdated
        ).withMaxValue(Integer.MAX_VALUE));
        var settingsComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.HEADER_SETTINGS.get(), settingsLayout);

        var layout = LinearLayout.vertical().spacing(VERTICAL_ELEMENT_SPACING);
        layout.addChild(modesComposite);
        layout.addChild(settingsComposite);

        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, leftPos + LEFT_PANE_WIDTH + PANE_SPACING, topPos, RIGHT_PANE_WIDTH, GUI_HEIGHT);

        layout.visitWidgets(this::addRenderableWidget);
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void initGraphTab() {
        addRenderableWidget(
            Button.builder(Component.literal("II"), _ -> onToggleGraphPause())
                .pos(leftPos + GUI_WIDTH - 22, topPos + 2)
                .size(20, 20)
                .build()
        );
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void initRedstoneTab() {
        var redstoneHandler = menu.getRedstoneHandler();
        lastRedstoneFunction = redstoneHandler.getFunction();
        lastControlBehavior = redstoneHandler.getControlBehavior();
        var isEnabled = lastRedstoneFunction != RedstoneFunction.OFF;
        var isEmitting = lastRedstoneFunction.emitsSignal();
        var isThreshold = lastRedstoneFunction == RedstoneFunction.THRESHOLD;
        var isControl = lastRedstoneFunction == RedstoneFunction.CONTROL;

        var functionRadioButtons = RadioButton.createGroup(
            REDSTONE_ELEMENT_WIDTH,
            EnergyMeterLang.REDSTONE_FUNCTIONS,
            redstoneHandler::getFunction,
            this::onRedstoneFunctionSelected
        );
        var functionComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.HEADER_FUNCTION.get(), functionRadioButtons);

        OutlinedCompositeWidget optionComposite;
        if (isControl) {
            var behaviorRadioButtons = RadioButton.createGroup(
                REDSTONE_ELEMENT_WIDTH,
                EnergyMeterLang.CONTROL_BEHAVIORS,
                redstoneHandler::getControlBehavior,
                this::onRedstoneControlBehaviorSelected
            );
            optionComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.HEADER_BEHAVIOR.get(), behaviorRadioButtons);
        } else {
            var scaleRadioButtons = RadioButton.createGroup(
                REDSTONE_ELEMENT_WIDTH,
                EnergyMeterLang.REDSTONE_SCALES,
                redstoneHandler::getScale,
                this::onRedstoneScaleSelected
            );
            scaleRadioButtons.visitWidgets(widget -> widget.active = lastRedstoneFunction == RedstoneFunction.ANALOG);
            optionComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.SETTING_SCALE.get(), scaleRadioButtons);
        }

        var settingsLayout = LinearLayout.vertical().spacing(1);
        var lowerBoundLabel = isThreshold ? EnergyMeterLang.SETTING_OFF_BELOW : EnergyMeterLang.SETTING_LOWER_BOUND;
        var upperBoundLabel = isThreshold ? EnergyMeterLang.SETTING_ON_AT : EnergyMeterLang.SETTING_UPPER_BOUND;
        settingsLayout.addChild(new InputLayoutElement<>(
            MeterTextBoxType.REDSTONE_LOWER_BOUND,
            REDSTONE_ELEMENT_WIDTH,
            font,
            lowerBoundLabel.get().append(":"),
            () -> NumberFormatter.formatCompact(redstoneHandler.getLowerBound()),
            this::onTextValueUpdated
        ).withActive(isEmitting));
        settingsLayout.addChild(new InputLayoutElement<>(
            MeterTextBoxType.REDSTONE_UPPER_BOUND,
            REDSTONE_ELEMENT_WIDTH,
            font,
            upperBoundLabel.get().append(":"),
            () -> NumberFormatter.formatCompact(redstoneHandler.getUpperBound()),
            this::onTextValueUpdated
        ).withActive(isEmitting));

        var invertCheckbox = new Checkbox(
            REDSTONE_ELEMENT_WIDTH,
            EnergyMeterLang.SETTING_INVERT.get(),
            redstoneHandler::isInverted,
            this::onToggleRedstoneInverted
        );
        invertCheckbox.active = isEnabled;
        settingsLayout.addChild(invertCheckbox);
        var settingsComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.HEADER_SETTINGS.get(), settingsLayout);

        var signalLayout = LinearLayout.vertical().spacing(2);
        if (isControl) {
            signalLayout.addChild(new DynamicMarqueeStringWidget(
                REDSTONE_ELEMENT_WIDTH,
                () -> EnergyMeterLang.REDSTONE_SIGNAL.get().append(": " + redstoneHandler.getInputSignal())
            ));
            if (lastControlBehavior == ControlBehavior.THROTTLE) {
                signalLayout.addChild(new DynamicMarqueeStringWidget(REDSTONE_ELEMENT_WIDTH, this::getRedstoneLimitText));
            } else {
                signalLayout.addChild(new DynamicMarqueeStringWidget(REDSTONE_ELEMENT_WIDTH, this::getRedstoneTransferText));
            }
        } else {
            signalLayout.addChild(new DynamicMarqueeStringWidget(
                REDSTONE_ELEMENT_WIDTH,
                () -> EnergyMeterLang.REDSTONE_SIGNAL.get().append(": " + redstoneHandler.getOutputSignal())
            ));
            signalLayout.addChild(new DynamicMarqueeStringWidget(REDSTONE_ELEMENT_WIDTH, this::getRedstoneStepText));
        }

        var signalHeader = isControl ? EnergyMeterLang.HEADER_INPUT : EnergyMeterLang.HEADER_OUTPUT;
        var signalComposite = OutlinedCompositeWidget.ofLayout(signalHeader.get(), signalLayout);

        var layout = LinearLayout.vertical().spacing(VERTICAL_ELEMENT_SPACING);
        layout.addChild(functionComposite);
        layout.addChild(optionComposite);
        layout.addChild(settingsComposite);
        layout.addChild(signalComposite);

        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, leftPos + LEFT_PANE_WIDTH + PANE_SPACING, topPos, RIGHT_PANE_WIDTH, GUI_HEIGHT);

        layout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // don't render title and inventory labels
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        BACKGROUND.target(leftPos, topPos).render(graphics);
        if (currentTab != TabType.GRAPH) return;

        // dimensions
        int paneLeft = leftPos + LEFT_PANE_WIDTH + PANE_SPACING;
        int paneBottom = topPos + GUI_HEIGHT;
        int graphLeft = paneLeft + GRAPH_PADDING;
        int graphRight = paneLeft + RIGHT_PANE_WIDTH - GRAPH_PADDING;
        int graphTop = topPos + GRAPH_PADDING;
        int graphBottom = paneBottom - GRAPH_PADDING;
        int graphWidth = graphRight - graphLeft;
        int graphHeight = graphBottom - graphTop - GRAPH_MAX_VALUE_INSET;

        var graphHandler = menu.getGraphHandler();
        var points = graphHandler.getPoints();

        // no data
        if (points.length == 0) {
            var text = EnergyMeterLang.GRAPH_NO_DATA.get();
            graphics.text(
                font,
                text,
                paneLeft + RIGHT_PANE_WIDTH / 2 - font.width(text) / 2,
                topPos + GUI_HEIGHT / 2 - font.lineHeight / 2,
                Constants.COLOR_WHITE,
                false
            );
            return;
        }

        // paused
        if (menu.isGraphPaused()) {
            var text = EnergyMeterLang.GRAPH_PAUSED.get().withStyle(ChatFormatting.DARK_RED);
            graphics.text(
                font,
                text,
                paneLeft + RIGHT_PANE_WIDTH / 2 - font.width(text) / 2,
                topPos + GUI_HEIGHT / 2 - font.lineHeight / 2,
                Constants.COLOR_WHITE,
                false
            );
        }

        // axes
        graphics.fill(graphLeft, graphTop, graphLeft + 1, graphBottom + GRAPH_PADDING / 2 + 1, Constants.COLOR_WHITE);
        graphics.fill(graphLeft - GRAPH_PADDING / 2, graphBottom, graphRight + 1, graphBottom + 1, Constants.COLOR_WHITE);
        var xLabel = EnergyMeterLang.GRAPH_INTERVAL.get();
        graphics.text(font, xLabel, graphRight - font.width(xLabel), graphBottom + 2, Constants.COLOR_WHITE, false);

        // y axis max value
        var yLabel = graphHandler.getYLabel();
        if (!yLabel.isEmpty()) {
            graphics.fill(graphLeft - 2, graphTop + GRAPH_MAX_VALUE_INSET, graphLeft + 3, graphTop + GRAPH_MAX_VALUE_INSET + 1, Constants.COLOR_WHITE);
            graphics.text(
                font,
                yLabel,
                graphLeft + 6,
                graphTop + GRAPH_MAX_VALUE_INSET - font.lineHeight / 2,
                Constants.COLOR_WHITE,
                false
            );
        }

        // points and lines
        int previousX = -1;
        int previousY = -1;

        var pose = graphics.pose();
        var graphBounds = new ScreenRectangle(graphLeft + 1, graphTop, graphWidth - 1, graphBottom - graphTop)
            .transformAxisAligned(pose);
        var graphRenderState = new GraphRenderState(new Matrix3x2f(pose), graphBounds);

        var progress = menu.getGraphProgress();
        int progressOffset = Mth.floor(Mth.clampedLerp(0f, (float) graphWidth / GraphHandler.HISTORY_SIZE, progress));

        for (GraphPoint point : points) {
            int pX = graphLeft + Mth.floor(point.x() * graphWidth);
            int pY = graphBottom - Mth.floor(point.y() * graphHeight);

            // progress offset
            pX -= progressOffset;

            // collect line vertices
            if (previousX != -1 && previousY != -1) {
                graphRenderState.addLine(previousX, previousY, pX, pY, Constants.COLOR_WHITE);

                // draw point marker
                graphics.enableScissor(graphLeft + 1, graphTop, graphRight, graphBottom);
                graphics.fill(pX - 2, pY - 2, pX + 1, pY + 1, Constants.COLOR_ACCENT);
                graphics.disableScissor();
            }

            previousX = pX;
            previousY = pY;
        }

        // draw progress line
        if (previousX != -1 && previousY != -1) {
            graphRenderState.addLine(previousX, previousY, previousX + progressOffset, previousY, Constants.COLOR_ACCENT);
        }

        // kinda hacky to collect the state that way, but I found no other way
        ((GuiGraphicsExtractorAccessor) graphics).getGuiRenderState().addGuiElement(graphRenderState);
    }

    @Override
    protected <T extends GuiEventListener & NarratableEntry> T addWidget(T listener) {
        if (listener instanceof OutlinedCompositeWidget composite) {
            for (var child : composite.children()) {
                if (child instanceof ClickedOutsideListener clickedOutsideListener) {
                    clickedOutsideListeners.add(clickedOutsideListener);
                }
            }
        }
        if (listener instanceof ClickedOutsideListener clickedOutsideListener) {
            clickedOutsideListeners.add(clickedOutsideListener);
        }
        return super.addWidget(listener);
    }

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        clickedOutsideListeners.clear();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (var clickedOutsideListener : clickedOutsideListeners) {
            clickedOutsideListener.receiveClickOutside(event.x(), event.y());
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Component getRedstoneStepText() {
        var redstoneHandler = menu.getRedstoneHandler();
        if (redstoneHandler.getFunction() != RedstoneFunction.ANALOG) {
            return EnergyMeterLang.REDSTONE_STEP.get().append(": -");
        }

        var lowerBound = redstoneHandler.getLowerBound();
        var upperBound = redstoneHandler.getUpperBound();
        var isLogarithmic = redstoneHandler.getScale() == RedstoneScale.LOGARITHMIC;
        if (isLogarithmic) {
            lowerBound = Math.max(lowerBound, 1);
        }

        if (lowerBound >= upperBound) {
            return EnergyMeterLang.REDSTONE_ACTS_AS_THRESHOLD.get();
        }

        Component step;
        if (isLogarithmic) {
            var factor = Math.pow((double) upperBound / lowerBound, 1.0 / Redstone.SIGNAL_MAX);
            step = Component.literal(String.format(Locale.ROOT, "x%.2f", factor));
        } else {
            var energyPerLevel = (double) (upperBound - lowerBound) / Redstone.SIGNAL_MAX;
            step = NumberFormatter.formatEnergy(energyPerLevel).componentWithUnitPerTick();
        }

        return EnergyMeterLang.REDSTONE_STEP.get().append(": ").append(step);
    }

    private boolean hasRedstoneLayoutChanged() {
        var redstoneHandler = menu.getRedstoneHandler();
        return lastRedstoneFunction != redstoneHandler.getFunction() || lastControlBehavior != redstoneHandler.getControlBehavior();
    }

    private long getEffectiveTransferLimit() {
        return menu.getRedstoneHandler().getEffectiveTransferLimit(menu.getTransferLimit(), menu.getTransferMode());
    }

    private Component getRedstoneTransferText() {
        var transferState = getEffectiveTransferLimit() == 0 ?
            EnergyMeterLang.REDSTONE_TRANSFER_BLOCKED :
            EnergyMeterLang.REDSTONE_TRANSFER_ALLOWED;
        return EnergyMeterLang.REDSTONE_TRANSFER.get().append(": ").append(transferState.get());
    }

    private Component getRedstoneLimitText() {
        var effectiveTransferLimit = getEffectiveTransferLimit();

        Component limit;
        if (effectiveTransferLimit == 0) {
            limit = EnergyMeterLang.REDSTONE_TRANSFER_BLOCKED.get();
        } else if (effectiveTransferLimit == Long.MAX_VALUE) {
            limit = EnergyMeterLang.REDSTONE_TRANSFER_UNLIMITED.get();
        } else {
            limit = NumberFormatter.formatEnergy(effectiveTransferLimit).componentWithUnitPerTick();
        }

        var limitText = EnergyMeterLang.SETTING_LIMIT.get().append(": ").append(limit);
        return menu.getTransferLimit() == 0 ? limitText.withStyle(ChatFormatting.RED) : limitText;
    }

    private void onTabButtonClicked(TabType tabType) {
        currentTab = tabType;
        rebuildWidgets();
    }

    private void onIoSettingSelected(@Nullable Direction direction, IoSettingWithPriority setting) {
        sendClientAction(IoSettingClientAction.change(direction, setting));
    }

    private void onTransferModeSelected(TransferMode mode) {
        sendClientAction(new EnumClientAction<>(ClientActionRegistry.TRANSFER_MODE_ID, mode));
    }

    private void onMeasureModeSelected(MeasureMode mode) {
        sendClientAction(new EnumClientAction<>(ClientActionRegistry.MEASURE_MODE_ID, mode));
    }

    private void onTextValueUpdated(MeterTextBoxType textBox, long value) {
        sendClientAction(new TextValueClientAction<>(ClientActionRegistry.UPDATE_TEXT_ID, textBox, value));
    }

    private void onResetTotalButtonClicked() {
        sendClientAction(new SimpleClientAction<>(ClientActionRegistry.RESET_TOTAL_ID));
    }

    private void onToggleGraphPause() {
        sendClientAction(new SimpleClientAction<>(ClientActionRegistry.TOGGLE_GRAPH_PAUSE_ID));
    }

    private void onRedstoneFunctionSelected(RedstoneFunction function) {
        sendClientAction(new EnumClientAction<>(ClientActionRegistry.REDSTONE_FUNCTION_ID, function));
    }

    private void onRedstoneScaleSelected(RedstoneScale scale) {
        sendClientAction(new EnumClientAction<>(ClientActionRegistry.REDSTONE_SCALE_ID, scale));
    }

    private void onRedstoneControlBehaviorSelected(ControlBehavior controlBehavior) {
        sendClientAction(new EnumClientAction<>(ClientActionRegistry.REDSTONE_CONTROL_BEHAVIOR_ID, controlBehavior));
    }

    private void onToggleRedstoneInverted() {
        sendClientAction(new SimpleClientAction<>(ClientActionRegistry.TOGGLE_REDSTONE_INVERTED_ID));
    }

    public enum TabType {
        CONFIGURATION(MeterScreen::initConfigTab),
        GRAPH(MeterScreen::initGraphTab),
        REDSTONE(MeterScreen::initRedstoneTab);

        private final Consumer<MeterScreen> init;

        TabType(Consumer<MeterScreen> init) {
            this.init = init;
        }
    }
}
