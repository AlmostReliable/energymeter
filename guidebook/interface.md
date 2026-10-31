---
navigation:
  title: Interface
  parent: index.md
---

# Energy Meter Interface

This page explains all settings that can be configured for the <ItemLink id="meter" components="rarity=epic"/>. To access the settings,
place the block and interact with it to open its user interface. The interface is divided into two sections. The left panel is the global
panel and is always visible. The right panel depends on the currently selected tab in the top tab bar.

<br/>

## Global Panel

<FloatingImage src="assets/global_panel.png" align="right"/>

The global panel is located on the left side of the interface. It remains visible even when switching tabs. This panel contains the
current measurement values and the status. It also includes the I/O grid and the option to reset the total amount of measured energy.

### Measurements

The measurements include the current flow rate and the total amount of measured energy. The total value can be reset using the button
below the I/O grid.

The values for the current measurements update once per configured interval. The only exception is reaching the [tolerance](interface.md#tolerance).
See that section for more information.

### Status

The status updates whenever a setting or state changes that could affect it. This includes the current flow rate, changes to neighboring
blocks, and changes to input and output configurations.

The status **Disconnected** indicates that the required settings for the currently selected mode have not been configured. In most cases,
this means that an input or output is missing. **Idle** means that all settings are configured correctly and the Energy Meter is waiting to
transfer energy. The final status depends on the selected [transfer mode](interface.md#transferring) and indicates that the
<ItemLink id="meter" components="rarity=epic"/> is currently processing requests and operating correctly. **Blocked** means that the
[Control](interface.md#function) redstone function currently blocks the transfer.

### I/O Grid

This section is used to configure the sides of the block. Each side can be configured as an input, an output, or disabled for energy
requests. The front side cannot be configured because it is reserved for displaying the flow rate on the block. The sides can be configured
using left-click or right-click. It is also possible to reset a single side instantly or reset all sides at once. More details can be found
in the tooltips of the respective buttons.

Whether a side can be configured as an input or output depends on the selected [transfer mode](interface.md#transferring).

<br clear="all" />
---

## Configuration Panel

<FloatingImage src="assets/configuration_tab.png" align="right"/>

The configuration panel, accessible by selecting the **Configuration** tab, allows you to adjust settings related to modes and measurements.

### Modes

In this section, different modes can be selected using radio buttons. Only one option per section can be selected at a time.

#### Transferring

The transfer mode determines how energy requests arriving on sides configured as inputs are forwarded to the output sides.
The default mode is **Split**.

In **Split** mode, incoming requests are distributed evenly across all outputs. The system first attempts to divide the energy equally by
the number of outputs so that each output receives the same share. If one of the outputs accepts less than its calculated share, the
remaining energy is redistributed to the other outputs. This means the split is not forced. If there are more outputs than available energy,
the remainder is sent to a randomly selected output.

The **Transfer** mode is a special mode. It changes the behavior of the I/O grid and allows priorities to be assigned to outputs. Outputs
with higher priority always receive forwarded energy first. The energy is then forwarded in descending priority order. Lower-priority
outputs only receive energy if higher-priority outputs cannot accept all available energy.

The **Consume** mode is used for testing setups. Instead of forwarding energy, this mode permanently deletes all incoming energy. This mode
also changes the behavior of the I/O grid by disallowing outputs. It is typically used to measure how much energy can enter through all
inputs, especially when the normal flow is limited by the outputs.

#### Measuring

The measuring mode determines how the flow rate for an interval is calculated. The default mode is **Instant**.

In **Instant** mode, only the stored values measured during the current interval are considered. Previous intervals are ignored. This mode
is useful when exact values are required.

The **Smoothed** mode changes the calculation by incorporating the previous interval. Instead of using only the measurements of the current
interval, the flow rate is adjusted using the previous interval. This produces a smoother graph and reduces the impact of sudden spikes in
the flow rate. This mode is useful for observing average behavior over longer periods.

<br/>

### Settings

The options in this section are text fields that modify certain <ItemLink id="meter" components="rarity=epic"/> settings that affect
measurement and energy flow. These text boxes also have several special behaviors.

By default, the fields always display the current value. While editing a value, the displayed value does not update. Instead, the button on
the right side becomes active. Pressing this button applies the entered value. Alternatively, the modified value can be confirmed using
the **Enter** key.

The text boxes also support several smart features, including input validation. If an input is invalid, for example due to an invalid
number, the box turns red and the value cannot be applied.

All text boxes also support mathematical expressions. Supported operations include addition, subtraction, multiplication, division,
exponentiation, and parentheses. If a mathematical expression is detected, the text box displays a small popup with the calculated result
that can be applied. Since all configurable values are integers, results from mathematical expressions are automatically rounded to the
nearest whole number. Invalid symbols are also prevented while entering mathematical expressions. For example, a closing parenthesis can only
be inserted if a matching opening parenthesis exists.

Numbers can be shortened with unit suffixes: `k` (thousand), `M` (million), `G` (billion), `T`, `P`, and `E`, each a factor of 1000
larger than the previous one. The suffix is case-insensitive and must directly follow a number, for example `100k`, `1.5M`, or `2k * 3`.
Large values such as the transfer limit are also displayed in this short form when possible.

#### Limit

The transfer limit defines how much energy the <ItemLink id="meter" components="rarity=epic"/> can transfer per tick _(1 second = 20 ticks)_.
All inputs are accumulated and limited by this value. The limit therefore applies globally and not per input. Any energy exceeding the
limit is returned to the sender.

#### Interval

The interval defines the time in ticks _(1 second = 20 ticks)_ after which a new measurement is performed. During an interval, all relevant
values are internally recorded and stored. Once the interval ends, these values are used to calculate the current flow rate and update the
total measured energy.

By default, this value is **5 ticks (0.25 seconds)**. This default can be changed in the mod configuration. For performance reasons, the
value can never be lower than 5 ticks.

The relatively small default value allows the <ItemLink id="meter" components="rarity=epic"/> to react quickly to changes. This is
especially helpful when you want to immediately see when an energy supply is interrupted. However, this can cause fluctuating values to
produce a changing average. Increasing the interval can smooth out fluctuations but results in less frequent updates of the flow rate.

#### Tolerance

Tolerance is a niche option and is rarely required. It is an additional option that refines the interval and defines how many ticks
_(1 second = 20 ticks)_ the energy rate may remain zero before the interval resets.

During an interval, the energy supply may temporarily stop. In that case, the flow rate is only set to zero once the entire interval has
elapsed. This makes it difficult to detect interruptions when long intervals are used. By default, tolerance is **5 ticks (0.25 seconds)**.
This means it is acceptable for the energy flow through the <ItemLink id="meter" components="rarity=epic"/> to be zero for up to 5 ticks.
If no energy is transferred on the 6th tick, the interval ends early. This option is therefore only useful when the interval is greater
than 5 ticks.

The tolerance value is inclusive. With a value of 5, it is acceptable for energy to be absent for 5 ticks. Only the 6th tick resets
the interval.

This option is useful for very long intervals where you want to detect a loss of energy flow early. For example, if the interval is set to
**400 ticks (20 seconds)**, it may be useful to set the tolerance to **50 ticks (2.5 seconds)**. This would allow you to detect a power
interruption after only 2.5 seconds instead of up to 20 seconds. This is particularly helpful when using the **Smoothed**
[measurement mode](interface.md#measuring), since the flow rate takes longer to reach zero in that mode.

<br clear="all" />
---

## Graph Panel

<FloatingImage src="assets/graph_tab.png" align="right"/>

The graph panel can be accessed by selecting the **Graph** tab. It displays a graphical representation of the last 10 intervals.

The data is shown in a simple coordinate system. The **x-axis** represents the most recent intervals, while the **y-axis** shows the
average flow rate of each interval. The maximum value of the y-axis is always **10% higher** than the largest interval value to make the
graph points easier to interpret.

If data for fewer than 10 intervals exists, only the available intervals are displayed. While the current interval is running, only a
horizontal line is shown. The graph point appears once the interval result becomes available.

For the **Analog** and **Threshold** [redstone functions](interface.md#function), the lower and upper bounds are shown as dashed lines, so
you can see where the flow rate sits relative to them. The upper bound is drawn in green and the lower bound in gray. A bound above the
visible range is shown as a marker with its value at the top edge instead.

To inspect the data more easily, the graph can be paused using the small pause icon in the upper-right corner. Pausing only stops the
graphical representation of the graph. Internally, new data is still processed and the Energy Meter continues operating. A label inside
the graph indicates that it is paused, and the icon turns into a play icon. Press it again to resume the graph.

<br clear="all" />
---

## Redstone Panel

<FloatingImage src="assets/redstone_tab.png" align="right"/>

The redstone panel can be accessed by selecting the **Redstone** tab. It allows the <ItemLink id="meter" components="rarity=epic"/> to
emit a redstone signal based on the current flow rate, or to be switched on and off with a redstone signal.

Redstone is disabled by default. Pick a function other than **Off** to activate it. While active, redstone dust connects to every side
of the <ItemLink id="meter" components="rarity=epic"/> except the front with the display. The signal is weak power, so it does not pass
through solid blocks next to the <ItemLink id="meter" components="rarity=epic"/>. The signal is updated once per
[interval](interface.md#interval).

### Function

The function defines how the flow rate is turned into a signal. Only one function can be active at a time.

- **Off** emits no signal and redstone dust does not connect.
- **Analog** maps the flow rate between the lower and upper bound to a signal strength between 0 and 15. A flow rate at or below the
  lower bound emits 0; a flow rate at or above the upper bound emits 15.
- **Threshold** emits a full signal once the flow rate reaches the upper bound **(On at)** and keeps it until the flow rate drops below
  the lower bound **(Off below)**. The gap between both bounds prevents the signal from flickering when the flow rate fluctuates around a
  single value. If both bounds are equal, the signal switches at that value.
- **Control** reads a redstone signal from every side except the front and blocks all energy transfer while powered. Levers, buttons,
  redstone dust, redstone blocks, and strongly powered blocks next to the <ItemLink id="meter" components="rarity=epic"/> count. This
  function emits no signal itself, so redstone dust leading into the <ItemLink id="meter" components="rarity=epic"/> can't keep itself
  powered. While blocked, the [status](interface.md#status) shows **Blocked** and the flow rate drops to zero with the next interval.
  The [behavior](interface.md#behavior) defines whether the signal only blocks the transfer or scales it gradually.

### Scale

The scale only applies to the **Analog** function:

- **Linear**: every signal level covers the same range of flow rates. To get one signal level per step, set the lower bound to `0` and the
  upper bound to `15 x step`. For example, a lower bound of `0` and an upper bound of `150k` adds one signal level per 10k FE/t.
- **Logarithmic**: every signal level multiplies the flow rate by the same factor. This is useful to cover a large range of flow rates.
  For example, a lower bound of `1` and an upper bound of `1000T` _(10^15)_ adds one signal level per factor of 10. A lower bound of `0` is
  treated as `1`.

### Behavior

For the **Control** function, the scale section is replaced by the behavior section:

- **Switch**: any signal blocks the transfer.
- **Throttle**: the signal strength scales the [transfer limit](interface.md#limit) down in 15 steps, so a redstone circuit can regulate
  how much energy flows. A signal of 15 blocks the transfer. Throttle also applies the transfer limit in the **Consume** mode.

With a transfer limit of 15k FE/t, the effective limit looks like this:

| Signal | Normal  | Inverted |
|--------|---------|----------|
| 0      | 15k     | 0        |
| 5      | 10k     | 5k       |
| 15     | 0       | 15k      |

Throttle needs a transfer limit as its base. Without one, the signal can only block the transfer at full strength _(or at zero when
inverted)_ and the transfer is unlimited otherwise.

For example, a comparator reading an energy storage behind the <ItemLink id="meter" components="rarity=epic"/> slows down the transfer as
the storage fills up and stops it once the storage is full.

### Settings

The **lower** and **upper** bounds are flow rates in FE/t. Both are shared between the functions, so switching the function keeps the
values. If the lower bound is not below the upper bound, the **Analog** function acts like a threshold at the upper bound.

The bounds don't apply to the **Control** function.

**Invert** flips the signal. An inverted **Analog** signal emits 15 at the lower bound and 0 at the upper bound. An inverted **Threshold**
signal is on while the flow rate is low. An inverted **Control** function blocks the transfer while unpowered instead. With **Throttle**,
an inverted signal grants the transfer limit instead of reducing it. Since a <ItemLink id="meter" components="rarity=epic"/> without any flow has a flow rate of zero,
inverting the signal can be used as an alarm when the energy supply stops.

### Output

The output section shows the signal strength that is currently emitted and, for the **Analog** function, how much the flow rate has to
change for the signal to change by one level. For the **Control** function, it is replaced by the input section, which shows the strongest
signal received and whether the transfer is currently allowed or blocked. With **Throttle**, it shows the effective transfer limit
instead. It is shown in red if no transfer limit is set.

<br clear="all" />
