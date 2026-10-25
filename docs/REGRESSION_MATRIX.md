# Regression matrix

Status vocabulary: PASS = actually executed; PENDING = not executed. JVM tests and
APK inspection cannot establish hardware behavior.

| Area | Required evidence | Initial status |
|---|---|---|
| Quick use | No Usage Access required; persistent image; accurate mounted state | PENDING |
| Formats | PNG alpha, JPEG, static WebP, animated GIF; reject corrupt/animated WebP | PENDING |
| Touch | Window alpha equals slider; taps pass through on API 33–36 | PENDING (device) |
| Replacement | Failed decode preserves existing window; stale load discarded | PENDING |
| Rules | A→B→C, home, self, pause/resume; manual per-display priority | PENDING |
| Storage | Atomic index, no source deletion, ref-safe cleanup, retry migration | PENDING |
| Displays | Rotation, removal, independent manual sessions | PENDING (device) |
| Advanced | Hardware keys, tile, floating ball, optional accessibility | PENDING (device) |
| FGS | User initiation, specialUse, notification stop, revocation, process death | PENDING (device) |
| Branding | APK identity, version, icon, component names, coexistence | PENDING |
| Distribution | One signed universal APK, SHA-256, source and licenses | PENDING |
