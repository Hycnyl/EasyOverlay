# Foreground-service and platform review

## Design

The upstream declares three long-running dataSync services. This is inappropriate
for continuous user-controlled overlays and reaches Android 15's dataSync time
budget. EasyOverlay uses one non-exported `specialUse` service, permission
`FOREGROUND_SERVICE_SPECIAL_USE`, and manifest property
`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` describing a user-started image
overlay session with optional local app-switch monitoring and visible stop controls.

The service enters the foreground immediately, before loading data/images. It has
an ongoing low-importance notification with a stop-all action. Manual use requires
SYSTEM_ALERT_WINDOW, but not PACKAGE_USAGE_STATS or accessibility. Notification
denial does not falsely become overlay denial: Android may show the session only
in its active-apps task manager. The UI explains this and offers notification settings.

Only explicit UI/tile/key actions may start a session. Commands reuse the live
service when possible. ForegroundServiceStartNotAllowedException/SecurityException
must produce a visible retry-from-app instruction. START_NOT_STICKY, no boot
receiver and no auto-restart on launch/update avoid claiming unrestricted revival.
System process termination clears in-memory mounted state. The previous selected
image persists, and users can explicitly start a fresh session.

Screen-off/lock suspends automatic targets and re-observes after unlock. Revoked
Usage Access stops automatic targets only; manual sessions retain ownership.
Revoked overlay permission removes windows and explains the interruption. All
optional control paths enter the same coordinator; accessibility receives hardware
key events only and never provides a trusted overlay to bypass opacity limits.

## API policy and evidence

Compile SDK 36; target SDK **35** for this delivery. The construction specification
requires physical-device FGS review before raising targetSdk to 36. No Android
hardware is attached to this cloud instance, so that gate cannot honestly be
claimed passed. API 35 already enforces the relevant dataSync and background-start
restrictions; target 35 is not used to retain the upstream dataSync behavior. Running
on Android 16 remains a device-validation item; a later tested release can raise
the target without changing the application identity or signing key.

No specialUse type provides unlimited lifetime. OEM power management, system stop,
restricted/security surfaces, external displays and background initiation need
device testing. The release must disclose the unverified Android 13/14/15/16
matrix and must not advertise a tested 24/7 session.

References:
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- https://developer.android.com/develop/background-work/services/fgs/changes
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
