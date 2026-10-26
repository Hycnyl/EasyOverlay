package com.hycnyl.easyoverlay.platform

import android.content.Context
import android.os.Build
import com.hycnyl.easyoverlay.AppContainer
import com.hycnyl.easyoverlay.BuildConfig
import com.hycnyl.easyoverlay.R
import com.hycnyl.easyoverlay.ui.Strings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticFormatter {
    fun packageLabel(pkg:String?,include:Boolean,context:Context?=null):String =
        if (pkg.isNullOrBlank()) context?.getString(R.string.unknown) ?: "Unknown"
        else if(include) pkg
        else "app#"+MessageDigest.getInstance("SHA-256").digest(pkg.toByteArray()).take(4).joinToString("") { "%02x".format(it) }

    fun time(value:Long,context:Context?=null):String = if(value<=0) context?.getString(R.string.no_record) ?: "No record"
        else SimpleDateFormat("MM-dd HH:mm:ss", context?.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()).format(Date(value))

    fun summary(context:Context,container:AppContainer,includePackages:Boolean=false)=buildString {
        val tr=Strings(context)
        fun flag(value:Boolean)=tr(if(value)R.string.yes else R.string.no)
        val state=container.runtime.value
        appendLine("EasyOverlay ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine(tr(R.string.diag_package,BuildConfig.APPLICATION_ID))
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} / ${Build.MANUFACTURER}")
        appendLine(tr(R.string.diag_permissions,flag(Permissions.overlay(context)),flag(Permissions.usage(context)),flag(Permissions.notifications(context)),flag(Permissions.accessibility(context))))
        appendLine(tr(R.string.diag_session,flag(state.serviceRunning),flag(state.autoMonitoring),state.monitorProblem ?: tr(R.string.no_known_error)))
        appendLine(tr(R.string.diag_background,flag(context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted)))
        appendLine(tr(R.string.diag_foreground,packageLabel(state.foregroundPackage,includePackages,context),time(state.observedAt,context)))
        appendLine(tr(R.string.maximum_opacity,container.maximumPercent))
        val monitor=container.monitoring.state.value
        val cap=container.overlayCapabilities.state.value
        appendLine(tr(R.string.diag_monitoring,monitor.requestedMode.name,monitor.selectedSource.name,monitor.fallbackReason ?: "NONE"))
        appendLine(tr(R.string.diag_privileged,flag(cap.accessibilityConnected),container.privileged.state.value.connection.name,
            if(container.dhizuku.state.value.granted)tr(R.string.authorized)else tr(R.string.not_connected)))
        appendLine(tr(R.string.diag_backend,flag(cap.trustedPrimaryAvailable),cap.ordinaryMaxPercent,state.restoreProblem ?: "NONE"))
        container.displays.displays.value.forEach { display ->
            val session=state.sessions[display.id]
            appendLine(tr(R.string.diag_display,tr.displayName(display.id),display.id,
                session?.target?.owner?.name ?: "NONE",session?.target?.assetId?.take(8) ?: tr(R.string.none),
                session?.target?.opacityPercent ?: 0,time(session?.mountedAt ?: 0,context),
                state.failures[display.id]?.let(tr::message) ?: tr(R.string.no_mount_error)))
        }
        appendLine(tr(R.string.secondary_trusted_limited))
        appendLine(tr(R.string.diag_events))
        container.events.events.value.takeLast(100).forEach { appendLine("${time(it.at,context)} ${it.code} display=${it.displayId ?: "-"}") }
    }
}
