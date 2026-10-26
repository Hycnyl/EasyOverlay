package com.hycnyl.easyoverlay.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hycnyl.easyoverlay.R
import com.hycnyl.easyoverlay.platform.monitoring.MonitoringMode

private fun Context.activity():Activity?=when(this){is Activity->this;is ContextWrapper->baseContext.activity();else->null}

@Composable fun MonitoringScreen(vm:EasyOverlayViewModel,permission:(String)->Unit) {
    val tr=rememberStrings();val context=LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle();val monitor by vm.monitoring.collectAsStateWithLifecycle()
    val capability by vm.capabilities.collectAsStateWithLifecycle();val shizuku by vm.shizuku.collectAsStateWithLifecycle();val dhizuku by vm.dhizuku.collectAsStateWithLifecycle()
    val canRestore by vm.canRestoreOptimization.collectAsStateWithLifecycle();val canRestoreDhizuku by vm.canRestoreDhizuku.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle();val grants by vm.permissions.collectAsStateWithLifecycle()
    val preview by vm.optimization.collectAsStateWithLifecycle();val dhizukuPreview by vm.dhizukuPreview.collectAsStateWithLifecycle()
    val result by vm.optimizationResult.collectAsStateWithLifecycle()
    var restore by remember {mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionCard(tr(R.string.monitoring_setup),tr(R.string.monitoring_intro)) {
            listOf(MonitoringMode.AUTO to R.string.monitor_mode_auto,MonitoringMode.ACCESSIBILITY to R.string.monitor_mode_accessibility,
                MonitoringMode.SHIZUKU to R.string.monitor_mode_shizuku,MonitoringMode.USAGE to R.string.monitor_mode_usage).forEach {(mode,label)->
                FilterChip(settings.monitoringMode==mode.name,{vm.monitorMode(mode)},label={Text(tr(label))},enabled=busy==null)
            }
            Text(tr(R.string.monitor_source_label,tr.source(monitor.selectedSource.name)))
            monitor.fallbackReason?.let {Text(tr(R.string.monitor_fallback,tr.problem(it)),style=MaterialTheme.typography.bodySmall)}
            monitor.problem?.let {Text(tr.problem(it),style=MaterialTheme.typography.bodySmall)}
            Text(tr(R.string.foreground_accuracy_help),style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionCard(tr(R.string.accessibility_enhancement),tr(R.string.accessibility_enhancement_help)) {
            Text(tr(if(capability.accessibilityConnected)R.string.connected else R.string.not_connected))
            Text(tr(R.string.maximum_opacity,capability.maximumPercent(0)))
            capability.problemCode?.let {Text(tr.problem(it),color=MaterialTheme.colorScheme.error)}
            OutlinedButton(onClick={permission("keys")}){Text(tr(R.string.grant_or_open))}
        }}
        item {SectionCard("Shizuku",tr(R.string.shizuku_help)) {
            Text(tr(when { !shizuku.installed && !shizuku.binderAlive->R.string.not_installed;!shizuku.binderAlive->R.string.not_running;!shizuku.granted->R.string.authorization_required;else->R.string.authorized }))
            shizuku.problem?.takeIf {shizuku.granted}?.let {Text(tr.problem("SHIZUKU_${it.name}"),style=MaterialTheme.typography.bodySmall)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={context.activity()?.let {vm.container.privileged.requestPermission(it)}},enabled=shizuku.binderAlive && !shizuku.granted){Text(tr(R.string.request_authorization))}
                TextButton(onClick={vm.refreshPermissions()}){Text(tr(R.string.connect_retry))}
            }
        }}
        item {SectionCard(tr(R.string.optimize_title),tr(R.string.optimize_help)) {
            if(canRestore)Text(tr(R.string.optimization_restore_point),style=MaterialTheme.typography.bodySmall)
            Button(onClick={vm.previewOptimization()},enabled=shizuku.granted && shizuku.binderAlive && !canRestore && busy==null){Text(tr(R.string.preview_changes))}
            if(canRestore)OutlinedButton(onClick={restore="shizuku"},enabled=shizuku.granted && busy==null){Text(tr(R.string.restore_changes))}
            if(!grants.notifications)TextButton(onClick={permission("notifications")}){Text(tr(R.string.notification_still_disabled))}
        }}
        item {SectionCard("Dhizuku",tr(R.string.dhizuku_help)) {
            Text(tr(when{!dhizuku.installed->R.string.not_installed;!dhizuku.available->R.string.not_running;!dhizuku.granted->R.string.authorization_required;else->R.string.authorized}))
            if(dhizuku.available)Text(tr(R.string.device_owner_status,tr(if(dhizuku.deviceOwner)R.string.yes else R.string.no),tr(if(dhizuku.profileOwner)R.string.yes else R.string.no)),style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick={context.activity()?.let {vm.container.dhizuku.requestPermission(it)}},enabled=dhizuku.available && !dhizuku.granted){Text(tr(R.string.request_authorization))}
            TextButton(onClick={vm.previewDhizuku()},enabled=dhizuku.granted && !canRestoreDhizuku && busy==null){Text(tr(R.string.dhizuku_notifications))}
            if(canRestoreDhizuku)TextButton(onClick={restore="dhizuku"},enabled=dhizuku.granted && busy==null){Text(tr(R.string.restore_changes))}
        }}
        item {SectionCard(tr(R.string.recover_automatic),tr(R.string.recover_automatic_help)) {
            SettingRow(tr(R.string.recover_automatic),tr(if(settings.recoverAutomaticSession)R.string.session_state_on else R.string.session_state_off),{vm.recoverAutomatic(!settings.recoverAutomaticSession)}){Switch(settings.recoverAutomaticSession,{vm.recoverAutomatic(it)},enabled=busy==null)}
            val ignored=context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
            Text(tr(if(ignored)R.string.ignoring_battery else R.string.not_ignoring_battery),style=MaterialTheme.typography.bodySmall)
            SettingRow(tr(R.string.battery_settings),tr(R.string.battery_settings_help),{permission("battery")})
        }}
    }
    preview?.let {p->AlertDialog(onDismissRequest=vm::dismissOptimization,title={Text(tr(R.string.optimize_preview_title))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("EasyOverlay · com.hycnyl.easyoverlay",style=MaterialTheme.typography.bodySmall)
        listOf(p.changeNotification to R.string.optimize_notification,p.changeUsage to R.string.optimize_usage,p.changeBattery to R.string.optimize_battery).forEach {(change,label)->
            Text(if(change)"• ${tr(label)}" else tr(R.string.optimization_unchanged,tr(label)))
        }
        Text(tr(R.string.optimize_help),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={vm.applyOptimization()},enabled=p.hasChanges && !p.restoreAvailable && busy==null){Text(tr(R.string.apply_changes))}},dismissButton={TextButton(onClick=vm::dismissOptimization){Text(tr(R.string.cancel))}})}
    dhizukuPreview?.let {p->AlertDialog(onDismissRequest=vm::dismissDhizuku,title={Text(tr(R.string.optimize_preview_title))},text={Column {
        Text(tr(R.string.dhizuku_preview));Text(if(p.hasChanges)"• ${tr(R.string.optimize_notification)}" else tr(R.string.no_change_needed))
    }},confirmButton={TextButton(onClick={vm.applyDhizuku()},enabled=p.hasChanges && !p.restoreAvailable && busy==null){Text(tr(R.string.apply_changes))}},dismissButton={TextButton(onClick=vm::dismissDhizuku){Text(tr(R.string.cancel))}})}
    restore?.let {kind->AlertDialog(onDismissRequest={restore=null},title={Text(tr(R.string.restore_changes))},text={Text(tr(if(kind=="shizuku")R.string.restore_confirmation else R.string.restore_dhizuku_confirmation))},
        confirmButton={TextButton(onClick={restore=null;if(kind=="shizuku")vm.restoreOptimization()else vm.restoreDhizuku()}){Text(tr(R.string.restore_changes))}},dismissButton={TextButton(onClick={restore=null}){Text(tr(R.string.cancel))}})}
    result?.let {r->AlertDialog(onDismissRequest=vm::dismissOptimizationResult,title={Text(tr(R.string.operation_result))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        r.items.forEach {item->val label=when(item.item){"notification"->R.string.optimize_notification;"usage"->R.string.optimize_usage;else->R.string.optimize_battery}
            Text("${tr(label)}: ${tr(if(item.success)R.string.operation_success else R.string.operation_failure)}",color=if(item.success)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        }
    }},confirmButton={TextButton(onClick=vm::dismissOptimizationResult){Text(tr(R.string.understood))}})}
}
