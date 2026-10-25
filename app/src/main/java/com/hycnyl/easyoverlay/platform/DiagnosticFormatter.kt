package com.hycnyl.easyoverlay.platform

import android.content.Context
import android.os.Build
import com.hycnyl.easyoverlay.AppContainer
import com.hycnyl.easyoverlay.BuildConfig
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticFormatter {
    fun packageLabel(pkg:String?,include:Boolean):String = if (pkg.isNullOrBlank()) "未确定" else if(include) pkg else
        "app#"+MessageDigest.getInstance("SHA-256").digest(pkg.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
    fun time(value:Long):String=if(value<=0) "无记录" else SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault()).format(Date(value))
    fun summary(context:Context,container:AppContainer,includePackages:Boolean=false)=buildString {
        val state=container.runtime.value
        appendLine("EasyOverlay ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("包名：${BuildConfig.APPLICATION_ID}")
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT} / ${Build.MANUFACTURER}")
        appendLine("悬浮窗：${Permissions.overlay(context)}；Usage Access：${Permissions.usage(context)}；通知：${Permissions.notifications(context)}；实体键权限：${Permissions.accessibility(context)}")
        appendLine("服务：${state.serviceRunning}；自动监测：${state.autoMonitoring}；限制：${state.monitorProblem ?: "无已知错误"}")
        appendLine("前台：${packageLabel(state.foregroundPackage,includePackages)}；最近事件：${time(state.observedAt)}")
        appendLine("系统最大不透明度：${container.maximumPercent}%")
        container.displays.displays.value.forEach { display ->
            val session=state.sessions[display.id]
            appendLine("${display.label} (${display.id})：${session?.target?.owner ?: "NONE"}；图片 ${session?.target?.assetId?.take(8) ?: "无"}；实际 ${session?.target?.opacityPercent ?: 0}%；挂载 ${time(session?.mountedAt ?: 0)}；${state.failures[display.id] ?: "无挂载错误"}")
        }
        appendLine("副屏自动检测未验证；V1 仅主屏自动切换。手动优先级按屏幕独立生效。")
        appendLine("最近本地事件（不含文件 URI、用户目录或图片路径）：")
        container.events.events.value.takeLast(100).forEach { appendLine("${time(it.at)} ${it.code} display=${it.displayId ?: "-"}") }
    }
}
