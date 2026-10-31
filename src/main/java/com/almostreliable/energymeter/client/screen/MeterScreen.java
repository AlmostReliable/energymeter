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
import com.almostreliable.energymeter.client.screen.widget.GraphPauseButton;
import com.almostreliable.energymeter.client.screen.widget.GuideButton;
import com.almostreliable.energymeter.client.screen.widget.IoConfigButton;
import com.almostreliable.energymeter.client.screen.widget.OutlinedButton;
import com.almostreliable.energymeter.client.screen.widget.RadioButton;
import com.almostreliable.energymeter.client.screen.widget.Checkbox;
import com.almostreliable.energymeter.client.screen.widget.TabButton;
import com.almostreliable.energymeter.client.screen.widget.base.ClickedOutsideListener;
import com.almostreliable.energymeter.client.screen.widget.base.OutlinedCompositeWidget;
import com.almostreliable.energymeter.core.Constants;
import com.almostreliable.energymeter.data.EnergyMeterLang;
import com.almostreliable.energymeter.menu.MeterMenu;
import com.almostreliable.energymeter.network.action.ClientActionRegistry;
import com.almostreliable.energymeter.network.action.EnumClientAction;
import com.almostreliable.energymeter.network.action.IoSettingClientAction;
import com.almostreliable.energymeter.network.action.SimpleClientAction;
import com.almostreliable.energymeter.network.action.TextValueClientAction;
import com.almostreliable.energymeter.util.NumberFormatter;
import com.almostreliable.energymeter.util.TooltipBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.redstone.Redstone;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class MeterScreen extends SynchronizedContainerScreen<MeterMenu> {

    private static final int PANE_SPACING = 4;
    private static final int PANE_PADDING = 6;
    private static final int RIGHT_PANE_MIN_WIDTH = 160;
    private static final int VERTICAL_ELEMENT_SPACING = 4;
    private static final int GLOBAL_INFO_WIDTH = 70;
    private static final int GRAPH_PADDING = 12;
    private static final int GRAPH_MAX_VALUE_INSET = 6;
    private static final int GRAPH_BOUND_DASH_LENGTH = 3;
    private static final int GRAPH_PAUSE_INSET = 2;
    private static final int REDSTONE_ELEMENT_WIDTH = 110;

    private final List<ClickedOutsideListener> clickedOutsideListeners = new ArrayList<>();

    private TabType currentTab = TabType.CONFIGURATION;
    private RedstoneFunction lastRedstoneFunction = RedstoneFunction.OFF;
    private ControlBehavior lastControlBehavior = ControlBehavior.SWITCH;
    private int leftPaneWidth;
    private int rightPaneWidth;
    private int paneHeight;

    public MeterScreen(MeterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();

        var ioConfigLayout = IoConfigButton.createGroup(
            menu.getBlockState(),
            menu::getIoSetting,
            this::onIoSettingSelected,
            menu::getTransferMode,
            this::addRenderableWidget
        );
        var globalInfoLayout = createGlobalInfoLayout(ioConfigLayout);
        globalInfoLayout.arrangeElements();

        var tabContent = currentTab.contentFactory.apply(this);
        tabContent.arrangeElements();
        var tabContentWidth = tabContent.getWidth();
        var tabContentHeight = tabContent.getHeight();

        // the right pane fits the largest tab so the gui doesn't jump when switching tabs
        for (var tabType : TabType.values()) {
            if (tabType == currentTab) continue;

            var otherTabContent = tabType.contentFactory.apply(this);
            otherTabContent.arrangeElements();
            tabContentWidth = Math.max(tabContentWidth, otherTabContent.getWidth());
            tabContentHeight = Math.max(tabContentHeight, otherTabContent.getHeight());
        }

        leftPaneWidth = globalInfoLayout.getWidth() + PANE_PADDING * 2;
        rightPaneWidth = Math.max(tabContentWidth + PANE_PADDING * 2, RIGHT_PANE_MIN_WIDTH);
        paneHeight = Math.max(globalInfoLayout.getHeight(), tabContentHeight) + PANE_PADDING * 2;
        leftPos = (width - getGuiWidth()) / 2;
        topPos = (height - paneHeight) / 2;

        FrameLayout.centerInRectangle(globalInfoLayout, leftPos, topPos, leftPaneWidth, paneHeight);
        globalInfoLayout.visitWidgets(this::addRenderableWidget);
        addRenderableWidget(new GuideButton(leftPos, topPos));

        initTabs();

        FrameLayout.centerInRectangle(tabContent, getRightPaneX(), topPos, rightPaneWidth, paneHeight);
        tabContent.visitWidgets(this::addRenderableWidget);
        if (currentTab == TabType.GRAPH) {
            var x = getRightPaneX() + rightPaneWidth - GRAPH_PAUSE_INSET - GraphPauseButton.SIZE;
            addRenderableWidget(new GraphPauseButton(x, topPos + GRAPH_PAUSE_INSET, menu::isGraphPaused, this::onToggleGraphPause));
        }
    }

    private LinearLayout createGlobalInfoLayout(Layout ioConfigLayout) {
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

        var ioConfigComposite = OutlinedCompositeWidget.ofLayout(EnergyMeterLang.HEADER_IO.get(), ioConfigLayout);
        ioConfigComposite.setMinWidth(GLOBAL_INFO_WIDTH);

        var resetButton = new OutlinedButton(GLOBAL_INFO_WIDTH + 8, EnergyMeterLang.BUTTON_RESET_TOTAL.get(), this::onResetTotalButtonClicked);
        resetButton.setTooltip(TooltipBuilder.create().literal(EnergyMeterLang.BUTTON_RESET_TOTAL_TOOLTIP.get()).build());

        var layout = LinearLayout.vertical().spacing(VERTICAL_ELEMENT_SPACING);
        layout.addChild(energyRateComposite);
        layout.addChild(totalEnergyComposite);
        layout.addChild(statusComposite);
        layout.addChild(ioConfigComposite);
        layout.addChild(resetButton);

        return layout;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (currentTab == TabType.REDSTONE && hasRedstoneLayoutChanged()) {
            rebuildWidgets();
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
            getRightPaneX(),
            topPos - TabButton.TAB_HEIGHT + 1,
            rightPaneWidth,
            TabButton.TAB_HEIGHT,
            0.1f,
            0
        );

        layout.visitWidgets(this::addRenderableWidget);
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private Layout createConfigTab() {
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

        return layout;
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private Layout createGraphTab() {
        // the graph is drawn in the background and fills the pane
        return LinearLayout.vertical();
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private Layout createRedstoneTab() {
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

        return layout;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // don't render title and inventory labels
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int paneLeft = getRightPaneX();
        renderPane(guiGraphics, leftPos, leftPaneWidth);
        renderPane(guiGraphics, paneLeft, rightPaneWidth);
        if (currentTab != TabType.GRAPH) return;

        // dimensions
        int paneBottom = topPos + paneHeight;
        int graphLeft = paneLeft + GRAPH_PADDING;
        int graphRight = paneLeft + rightPaneWidth - GRAPH_PADDING;
        int graphTop = topPos + GRAPH_PADDING;
        int graphBottom = paneBottom - GRAPH_PADDING;
        int graphWidth = graphRight - graphLeft;
        int graphHeight = graphBottom - graphTop - GRAPH_MAX_VALUE_INSET;

        var graphHandler = menu.getGraphHandler();
        var points = graphHandler.getPoints();

        // no data
        if (points.length == 0) {
            guiGraphics.drawCenteredString(
                font,
                EnergyMeterLang.GRAPH_NO_DATA.get(),
                paneLeft + rightPaneWidth / 2,
                topPos + paneHeight / 2 - font.lineHeight / 2,
                Constants.COLOR_WHITE
            );
            return;
        }

        // paused
        if (menu.isGraphPaused()) {
            guiGraphics.drawCenteredString(
                font,
                EnergyMeterLang.GRAPH_PAUSED.get().withStyle(ChatFormatting.DARK_RED),
                paneLeft + rightPaneWidth / 2,
                topPos + paneHeight / 2 - font.lineHeight / 2,
                Constants.COLOR_WHITE
            );
        }

        // axes
        guiGraphics.vLine(graphLeft, graphTop, graphBottom + GRAPH_PADDING / 2, Constants.COLOR_WHITE);
        guiGraphics.hLine(graphLeft - GRAPH_PADDING / 2, graphRight, graphBottom, Constants.COLOR_WHITE);
        var xLabel = EnergyMeterLang.GRAPH_INTERVAL.get();
        guiGraphics.drawString(font, xLabel, graphRight - font.width(xLabel), graphBottom + 2, Constants.COLOR_WHITE);

        // y axis max value
        var yLabel = graphHandler.getYLabel();
        if (!yLabel.isEmpty()) {
            guiGraphics.hLine(graphLeft - 2, graphLeft + 2, graphTop + GRAPH_MAX_VALUE_INSET, Constants.COLOR_WHITE);
            guiGraphics.drawString(
                font,
                yLabel,
                graphLeft + 6,
                graphTop + GRAPH_MAX_VALUE_INSET - font.lineHeight / 2,
                Constants.COLOR_WHITE,
                false
            );
        }

        drawRedstoneBounds(guiGraphics, graphLeft, graphRight, graphBottom, graphHeight);

        // points and lines
        int previousX = -1;
        int previousY = -1;

        var pose = guiGraphics.pose().last().pose();
        var bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.LINE_STRIP, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        var progress = menu.getGraphProgress();
        int progressOffset = Mth.floor(Mth.clampedLerp(0f, (float) graphWidth / GraphHandler.HISTORY_SIZE, progress));

        for (GraphPoint point : points) {
            int pX = graphLeft + Mth.floor(point.x() * graphWidth);
            int pY = graphBottom - Mth.floor(point.y() * graphHeight);

            // progress offset
            pX -= progressOffset;

            // collect line vertices
            if (previousX != -1 && previousY != -1) {
                bufferBuilder.addVertex(pose, previousX, previousY, 0).setColor(Constants.COLOR_WHITE).setNormal(1, 1, 0);
                bufferBuilder.addVertex(pose, pX, pY, 0).setColor(Constants.COLOR_WHITE).setNormal(1, 1, 1);

                // draw point marker
                guiGraphics.enableScissor(graphLeft + 1, graphTop, graphRight, graphBottom);
                guiGraphics.fill(pX - 2, pY - 2, pX + 1, pY + 1, Constants.COLOR_ACCENT);
                guiGraphics.disableScissor();
            }

            previousX = pX;
            previousY = pY;
        }

        // draw progress line
        if (previousX != -1 && previousY != -1) {
            bufferBuilder.addVertex(pose, previousX, previousY, 0).setColor(Constants.COLOR_ACCENT).setNormal(1, 1, 0);
            bufferBuilder.addVertex(pose, previousX + progressOffset, previousY, 0).setColor(Constants.COLOR_ACCENT).setNormal(1, 1, 1);
        }

        var drawData = bufferBuilder.build();
        if (drawData == null) return;

        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(2.0F);
        guiGraphics.enableScissor(graphLeft + 1, graphTop, graphRight, graphBottom);

        BufferUploader.drawWithShader(drawData);

        guiGraphics.disableScissor();
        RenderSystem.lineWidth(1.0F);
        GlStateManager._enableCull();
        GlStateManager._depthMask(true);
    }

    private void renderPane(GuiGraphics guiGraphics, int x, int paneWidth) {
        guiGraphics.fill(x, topPos, x + paneWidth, topPos + paneHeight, Constants.COLOR_BACKGROUND);
        guiGraphics.renderOutline(x, topPos, paneWidth, paneHeight, Constants.COLOR_ACCENT);
    }

    private void drawRedstoneBounds(GuiGraphics guiGraphics, int graphLeft, int graphRight, int graphBottom, int graphHeight) {
        var redstoneHandler = menu.getRedstoneHandler();
        if (!redstoneHandler.getFunction().emitsSignal()) return;

        var yMax = menu.getGraphHandler().getYMax();
        var yMaxLine = graphBottom - graphHeight;
        var bounds = new long[] {redstoneHandler.getUpperBound(), redstoneHandler.getLowerBound()};
        var colors = new int[] {Constants.COLOR_ACCENT, Constants.COLOR_GRAY};
        var markerY = yMaxLine - font.lineHeight / 2;

        for (var i = 0; i < bounds.length; i++) {
            var bound = bounds[i];
            if (bound <= 0) continue;

            var label = NumberFormatter.formatEnergy(bound).getEnergyWithUnit();

            // bounds above the visible range stack as markers at the top edge
            if (bound > yMax) {
                var markerLabel = "▲ " + label;
                guiGraphics.drawString(font, markerLabel, graphRight - font.width(markerLabel), markerY, colors[i], false);
                markerY += font.lineHeight;
                continue;
            }

            var lineY = graphBottom - Mth.floor(bound / yMax * graphHeight);
            for (var dashX = graphLeft + 1; dashX < graphRight; dashX += GRAPH_BOUND_DASH_LENGTH * 2) {
                var dashEnd = Math.min(dashX + GRAPH_BOUND_DASH_LENGTH, graphRight);
                guiGraphics.fill(dashX, lineY, dashEnd, lineY + 1, colors[i]);
            }

            // upper label above its line, lower label below unless it would collide with the x-axis
            var labelBelowY = lineY + 2;
            var isLabelBelow = i > 0 && labelBelowY + font.lineHeight <= graphBottom;
            var labelY = isLabelBelow ? labelBelowY : lineY - font.lineHeight;
            guiGraphics.drawString(font, label, graphRight - font.width(label), labelY, colors[i], false);
        }
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
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int mouseButton) {
        return mouseX < left || mouseY < top || mouseX >= left + getGuiWidth() || mouseY >= top + paneHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (var clickedOutsideListener : clickedOutsideListeners) {
            clickedOutsideListener.receiveClickOutside(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getGuiWidth() {
        return leftPaneWidth + PANE_SPACING + rightPaneWidth;
    }

    private int getRightPaneX() {
        return leftPos + leftPaneWidth + PANE_SPACING;
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
        CONFIGURATION(MeterScreen::createConfigTab),
        GRAPH(MeterScreen::createGraphTab),
        REDSTONE(MeterScreen::createRedstoneTab);

        private final Function<MeterScreen, Layout> contentFactory;

        TabType(Function<MeterScreen, Layout> contentFactory) {
            this.contentFactory = contentFactory;
        }
    }
}
