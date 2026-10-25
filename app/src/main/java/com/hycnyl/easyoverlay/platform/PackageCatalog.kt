package com.hycnyl.easyoverlay.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val icon: Bitmap?)

class PackageCatalog(private val context: Context) {
    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info -> InstalledApp(info.activityInfo.packageName,
                runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.activityInfo.packageName),
                runCatching { info.loadIcon(pm).toBitmap(64,64) }.getOrNull()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
