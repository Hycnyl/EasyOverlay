# Upstream baseline

- Fork: https://github.com/Hycnyl/EasyOverlay, branch `main`.
- Recorded HEAD: `a74ef6f2a2ceedf250f79e265ed2c9433fe929b1`.
- Upstream: https://github.com/PerryKum/ImageOverlay, GPL-3.0.
- Version: 5.2.0-revolution, versionCode 60.
- Original identity: com.example.imageoverlay; min/target/compile SDK 23/34/34.
- AGP 8.1.2, Kotlin 1.9.0, wrapper Gradle 8.12; JDK 17.

## Existing capabilities and risks

QuickUseFragment supports manual images without app bindings. OverlayService mounts
per-display windows, including GIF via android-gif-drawable. ConfigRepository mixes
groups, persistence, activation and service control. UsageStatsListener polls every
500 ms but applies a 3-second cooldown. MainActivity requires a SAF root before use.
FloatingBallService, keybinding/KeyBindingService and tiles/DefaultToggleTileService
provide advanced controls. These controls must use the new coordinator after migration.

Confirmed code risks: GroupDetailFragment contains directory-wide image deletion;
OverlayService removes the previous window before decoding its replacement;
DisplayUtil can report opacity above the actual allowed window alpha; all three
foreground services are declared dataSync. The old loader may create/write an
ImageOverlay directory. The new legacy importer must never call that loader.

The baseline has no automated tests or CI. Real device behavior has not been verified.
