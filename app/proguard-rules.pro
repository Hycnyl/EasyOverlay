# Gson's versioned on-disk/backup schema must retain field and class members.
-keep class com.hycnyl.easyoverlay.domain.OverlayAsset { *; }
-keep class com.hycnyl.easyoverlay.domain.AppRule { *; }
-keep class com.hycnyl.easyoverlay.domain.QuickSelection { *; }
-keep class com.hycnyl.easyoverlay.domain.UnassignedAsset { *; }
-keep class com.hycnyl.easyoverlay.domain.LibraryState { *; }
-keep class com.hycnyl.easyoverlay.data.BackupEnvelope { *; }
-keep class com.hycnyl.easyoverlay.data.DiagnosticEvent { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
