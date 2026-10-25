# EasyOverlay 6.0 architecture decisions

1. Android 13–16 is the supported range (minSdk 33). API 36 compile SDK, with
   explicit specialUse foreground service declarations. A single user-started
   service hosts manual overlays, optional foreground observation and the optional
   floating control. No boot receiver, sticky resurrection, dataSync, network
   permission or accessibility overlay bypass.
2. One ordinary non-touchable overlay window per display. System InputManager
   opacity is floored to an integer percent; the same bound governs UI and windows.
   Images decode off the main thread. Prepared content updates the existing window;
   a failed or obsolete request does not delete a valid previous window.
3. One serialized coordinator owns desired state; only successful WindowManager
   operations publish mounted sessions. Manual requests outrank rules per display.
   Runtime sessions are never persisted as if they were mounted after process death.
4. A small, versioned JSON library uses Android AtomicFile for a single transactional
   index. Assets have UUID paths and content hashes. Files finish copying and
   validation before references change. Mutations serialize under a Mutex; errors
   are visible. Corrupt indexes are protected from automatic overwrite.
   This is an intentionally smaller equivalent to a database for this local library.
5. DataStore owns preferences. UI observes StateFlow through lifecycle-aware Compose.
   Legacy import is a separate read-only SAF scanner; no legacy loader or writer
   participates in the new app. Conflict items and unbound images remain available
   for assignment instead of silently replacing a rule.
6. Platform ImageDecoder handles PNG/JPEG/static WebP/animated GIF on all supported
   Android versions. There is no separate native image codec. AndroidX transitive
   native libraries are included for arm64-v8a, armeabi-v7a, x86 and x86_64 in the
   same universal APK; the release verification script checks each library's ABIs.
7. UsageStats cannot reliably identify independent frontmost packages on physical
   secondary displays. Automatic rules apply to the default display only in V1;
   secondary displays retain independent manual control and editable rules. UI and
   diagnostics explain this limit.
8. Real-device FGS, touch-through, external displays, physical keys, ROM power
   management and Android 16 target behavior are release validation gates. Cloud
   results are recorded separately and never substituted for device evidence.
