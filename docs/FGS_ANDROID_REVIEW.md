# Foreground-service and platform review (6.1)

EasyOverlay uses one non-exported `specialUse` foreground service, started immediately with a visible low-importance notification and Stop All action. No dataSync timer or invisible overlay is used for lifetime extension. Ordinary manual overlays need SYSTEM_ALERT_WINDOW; a genuinely connected AccessibilityService can supply a trusted default-display overlay token instead. Neither path requires Usage Access for manual Quick Use.

Automatic monitoring uses a single controller: Accessibility window events, optional Shizuku task snapshots, or UsageStats fallback. Source selection and actual connection are visible in settings/diagnostics. Accessibility reads root package/window metadata without retaining screen text or traversing child node trees. Shizuku uses a non-daemon UserService, released when monitoring stops; Dhizuku is not a global foreground detector.

6.1 supersedes the original construction restriction on accessibility overlays and recovery. Only newly explicitly enabled automatic sessions persist restart intent. Manual windows do not reappear after process death. A recoverable session uses START_STICKY; boot/update/unlock, accessibility reconnect and app-open can request recovery. Recovery respects the user setting, ApplicationExitInfo user-stop records, and a maximum of three recovery starts in ten minutes. Android foreground-start restrictions still apply; failure offers an app-open notification instead of alarms or repeated retries. The receiver is non-exported and accepts protected system events only. Stop All and disabling automatic mode persist user intent before stopping observation.

At normal opacity, ordinary windows remain below InputManager.maximumObscuringOpacityForTouch. Above that limit, the main display uses TYPE_ACCESSIBILITY_OVERLAY from the connected service's createWindowContext. Losing that token clamps to the ordinary limit or removes the window if ordinary permission is absent. Secondary display extended opacity is not advertised. Sliders above 80% stay red even with warnings suppressed.

Compile SDK 36, target SDK **35**, minimum SDK 33. Raising the target to 36 remains gated on physical-device foreground-service validation. The user's real-device acceptance of basic 6.0 Quick Use is retained as reported evidence; it does not establish the Android 13/14/15/16 and OEM background matrix for 6.1. A specialUse FGS, accessibility binding, battery whitelist or privileged broker cannot guarantee unlimited lifetime.

References:
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping
- https://developer.android.com/reference/android/app/Service#START_STICKY
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE
