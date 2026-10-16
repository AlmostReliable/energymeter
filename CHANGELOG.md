# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

- added Portuguese translation ([#45](https://github.com/AlmostReliable/energymeter/pull/45))
- added new test utilities and test cable blocks
- fixed crash with looped connections ([#46](https://github.com/AlmostReliable/energymeter/issues/46)) and added tests for it

## [0.5.2] - 2026-08-11

- added tests for ensuring neighbor updates are listened to
- changed logic for listening to neighbor updates
- fixed Energy Meter not accepting maximum possible energy in consume mode
- fixed crash when placing test energy emitter block
- fixed rare unnecessary saving of Energy Meter block entities
- fixed split mode being biased on outputs
- fixed CC: Tweaked integration sometimes not displaying correct connection status
- fixed CC: Tweaked computers sometimes not being subscribed to the Energy Meter when multiple computers are attached
- fixed CC: Tweaked Energy Meter observers not being called synchronously
- fixed CC: Tweaked peripheral capability being recreated for every attached computer

## [0.5.1] - 2026-08-10

- fixed monitor block models generating duplicate elements

## [0.5.0] - 2026-07-30

- added French translation ([#43](https://github.com/AlmostReliable/energymeter/pull/43))
- added CC: Tweaked integration ([#42](https://github.com/AlmostReliable/energymeter/pull/42))
- fixed Energy Meter not storing changes when only the total energy changes

## [0.4.1] - 2026-05-16

- fixed guide book not showing contents in production
- fixed dead link to transfer mode section in the interface guidebook page

## [0.4.0] - 2026-03-05

- added mechanic and feedback to cap text boxes at their maximum values
- added zero tolerance implementation (previously, setting the option had no effect)
- added config option and logic to disable meters connecting to each other (disallowed by default)
- added GuideME integration
- fixed meter not supporting long-based transfer limit
- fixed crash when text box value exceeds the maximum supported value
- changed order for interval and tolerance options in the GUI

## [0.3.2] - 2026-03-04

- fixed world bricking when an output does not accept multiple energy pushes in a single tick in split mode
- fixed logic to fill available outputs with their maximum voiding energy if the actual amount differs from the simulation
- improved splitting logic to only do one transfer operation in a single tick

## [0.3.1] - 2026-02-28

- fixed energy limit being applied per operation instead of per tick
- fixed energy limit not respecting multiple inputs
- improved energy splitting algorithm to distribute leftover energy

## [0.3.0] - 2026-02-27

- added new GUI tab showing a graphical representation of the last measured energy rates
- added proper client action registration system to avoid unhandled synced actions
- added fallback system to clear output caches when a neighbor block changes without invalidating its capability
- fixed cable connections with mods relying on block updates instead of capability invalidation when an I/O config is changed
- fixed cable connections with mods checking for non-directional energy storages
- fixed players entering or re-entering the chunk not getting the latest energy rates

## [0.2.0] - 2026-02-25

- added button to reset the total energy transferred
- added option to set custom interval length for exact/instant mode
- fixed output priority being zero when switching from split to transfer mode
- fixed I/O config widgets not being centered in their group box
- fixed meter configuration values not always being saved
- improved performance by drastically simplifying interval average calculation logic
- renamed measuring modes from "exact" and "interval" to "instant" and "smoothed"

## [0.1.0] - 2026-02-25

Initial 1.21.1 release! The mod is still subject to change and lacks some features, as well as explanations.

The new version is a complete rewrite of the 1.20.1 version.

<!-- Versions -->

[0.5.2]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.5.2
[0.5.1]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.5.1
[0.5.0]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.5.0
[0.4.1]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.4.1
[0.4.0]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.4.0
[0.3.2]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.3.2
[0.3.1]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.3.1
[0.3.0]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.3.0
[0.2.0]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.2.0
[0.1.0]: https://github.com/AlmostReliable/energymeter/releases/tag/v1.21.1-neoforge-0.1.0
