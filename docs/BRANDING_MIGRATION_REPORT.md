# EasyOverlay branding migration

B1-A changes the installation identity and user-visible app name, README, adaptive
launcher icon and Android 13 themed icon. A real Debug build succeeded in 4s.
`aapt dump badging` on that APK reported `com.hycnyl.easyoverlay`; the intermediate
version remains upstream 5.2 until the feature-release commit.

B1-B migrates Kotlin sources to `java/com/hycnyl/easyoverlay`, package declarations,
imports, explicit broadcast actions, accessibility component strings, namespace,
application class and theme references. The deprecated manifest package attribute
is removed. This is a scoped source/resource migration, not a global historical
text replacement. Final APK/component checks are recorded in VALIDATION.md.

Intentionally retained upstream identifiers: `ImageOverlay` SAF directory names,
legacy JSON group/config fields, upstream copyright/license/URLs, and recorded
baseline names in historical documentation. These are source attribution or
read-only data compatibility identifiers, not the new app's installation identity.

Physical coexistence, launcher, notification and tile testing remain unverified.
