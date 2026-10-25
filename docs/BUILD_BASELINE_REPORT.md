# Actual upstream baseline results — 2026-10-25

Source: upstream `a74ef6f2a2ceedf250f79e265ed2c9433fe929b1`, with documentation and
CI-only additions. No application behavior or identity was changed for this run.
Environment: Temurin 17.0.21+9, Gradle 8.12, AGP 8.1.2, Android SDK 34. The old AGP
also automatically installed build-tools 33.0.1.

Command actually executed:
`bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --stacktrace --console=plain`

| Check | Actual outcome |
|---|---|
| assembleDebug | PASS; APK copied to `/workspace/baseline-artifacts/ImageOverlay-5.2-baseline-debug.apk` |
| testDebugUnitTest | NO-SOURCE; upstream has no tests; not a test pass |
| lintDebug | FAIL; 14 errors and 122 warnings; existing API guards/TileService issues |
| Hardware/install/touch/FGS | NOT RUN; no Android device attached |

The combined Gradle command exited 1 after 6m 2s because lint failed. Failures
include TileService on minSdk 23, subtitle calls guarded at 24 rather than 29,
and the display-specific createWindowContext overload guarded at 30 rather than 31.
Reports are preserved beside the baseline APK. The final app must run lint without
these errors; no lint baseline or blanket suppression will hide them.

The initial GitHub CI setup failed because setup-android tried to install the now
removed SDK package `tools`. Commit `5bcd559` changes that action to platform-tools.
Subsequent CI and all final app results are recorded in the final validation report.
