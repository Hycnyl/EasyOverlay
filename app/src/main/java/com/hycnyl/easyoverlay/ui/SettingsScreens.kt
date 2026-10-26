package com.hycnyl.easyoverlay.ui

import com.hycnyl.easyoverlay.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hycnyl.easyoverlay.BuildConfig
import com.hycnyl.easyoverlay.data.*
import com.hycnyl.easyoverlay.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun SettingsScreen(vm:EasyOverlayViewModel,auto:(Boolean)->Unit,permission:(String)->Unit,navigate:(String)->Unit,
    backupExport:()->Unit,backupImport:()->Unit,library:()->Unit) {
    val tr = rememberStrings()
    val context = LocalContext.current
    val prefs by vm.settings.collectAsStateWithLifecycle();val runtime by vm.runtime.collectAsStateWithLifecycle();val grants by vm.permissions.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(tr(R.string.settings_title),style=MaterialTheme.typography.headlineMedium)}
        item {SectionCard(tr(R.string.permissions_monitoring)) {
            SettingRow(tr(R.string.monitoring_setup),tr(R.string.monitoring_intro),{navigate("monitoring")})
            SettingRow(tr(R.string.monitor_session),if(runtime.autoMonitoring)if(runtime.monitorProblem==null)tr(R.string.monitor_running_detail) else tr(R.string.monitor_paused_detail) else tr(R.string.monitor_off_detail),{auto(!runtime.autoMonitoring)}) {
                Switch(runtime.autoMonitoring,{auto(it)})
            }
            if(!runtime.serviceRunning && prefs.lastAutoRequested)Text(tr(R.string.session_ended_help),style=MaterialTheme.typography.bodySmall)
            SettingRow(tr(R.string.display_over_apps),if(grants.overlay)tr(R.string.allowed) else tr(R.string.overlay_permission_needed),{permission("overlay")})
            SettingRow(tr(R.string.usage_access),if(grants.usage)tr(R.string.usage_allowed_detail) else tr(R.string.usage_denied_detail),{permission("usage")})
            SettingRow(tr(R.string.session_notifications),if(grants.notifications)tr(R.string.notifications_allowed_detail) else tr(R.string.notifications_denied_detail),{permission("notifications")})
        }}
        item {SectionCard(tr(R.string.appearance)) {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(ThemeChoice.SYSTEM to tr(R.string.follow_system),ThemeChoice.LIGHT to tr(R.string.theme_light),ThemeChoice.DARK to tr(R.string.theme_dark)).forEach {(value,label)->FilterChip(prefs.theme==value,{vm.theme(value)},label={Text(label)})}
            }
            Text(tr(R.string.dynamic_theme_help),style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionCard(tr(R.string.language_title), tr(R.string.language_help)) {
            val selected = AppLanguages.current(context)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(
                    AppLanguage.SYSTEM to tr(R.string.follow_system),
                    AppLanguage.SIMPLIFIED_CHINESE to tr(R.string.language_chinese),
                    AppLanguage.ENGLISH to tr(R.string.language_english)
                ).forEach { (language, label) ->
                    FilterChip(selected=selected==language,onClick={AppLanguages.select(context,language)},label={Text(label)})
                }
            }
        }}
        item {SectionCard(tr(R.string.images_backups),tr(R.string.private_storage_help)) {
            SettingRow(tr(R.string.library_unassigned),tr(R.string.library_settings_help),library)
            SettingRow(tr(R.string.export_backup),tr(R.string.export_backup_help),backupExport)
            SettingRow(tr(R.string.import_backup),tr(R.string.import_backup_help),backupImport)
        }}
        item {SectionCard(tr(R.string.more_controls)) {
            SettingRow(tr(R.string.diagnostics_title),tr(R.string.diagnostics_settings_help),{navigate("diagnostics")})
            SettingRow(tr(R.string.advanced_title),tr(R.string.advanced_settings_help),{navigate("advanced")})
            if(prefs.suppressUnsafeOpacityWarning)SettingRow(tr(R.string.reset_opacity_warning),tr(R.string.unsafe_opacity_status),{vm.suppressOpacityWarning(false)})
        }}
        if(runtime.serviceRunning)item {OutlinedButton(onClick={vm.stopAll()},modifier=Modifier.fillMaxWidth()){Text(tr(R.string.stop_all_sessions))}}
        item {SectionCard(tr(R.string.about_title)) {SettingRow(tr(R.string.app_name),tr(R.string.about_version,BuildConfig.VERSION_NAME),{navigate("about")})}}
    }
}

@Composable fun AdvancedScreen(vm:EasyOverlayViewModel,permission:(String)->Unit,ball:(Boolean)->Unit) {
    val tr = rememberStrings()
    val settings by vm.settings.collectAsStateWithLifecycle();val runtime by vm.runtime.collectAsStateWithLifecycle();val displays by vm.displays.collectAsStateWithLifecycle();val grants by vm.permissions.collectAsStateWithLifecycle()
    val captured by HardwareCapture.keys.collectAsStateWithLifecycle();var function by remember {mutableStateOf<String?>(null)}
    DisposableEffect(Unit){onDispose {HardwareCapture.end()}}
    val labels=mapOf("toggle_main" to tr(R.string.toggle_main),"toggle_secondary" to tr(R.string.toggle_secondary),"toggle_all" to tr(R.string.toggle_all),"toggle_ball" to tr(R.string.toggle_ball))
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionCard(tr(R.string.displays), tr(R.string.displays_help, displays.size)) {
            displays.forEach {Text(tr(R.string.display_id_label, tr.displayName(it.id), it.id))}
            SettingRow(tr(R.string.cover_cutout),tr(R.string.cutout_help),{vm.cutout(!settings.coverCutout)}){Switch(settings.coverCutout,{vm.cutout(it)})}
        }}
        item {SectionCard(tr(R.string.floating_control),tr(R.string.floating_help)) {
            SettingRow(tr(R.string.show_ball),if(runtime.floatingVisible)tr(R.string.currently_shown) else tr(R.string.currently_off),{ball(!runtime.floatingVisible)}){Switch(runtime.floatingVisible,ball)}
        }}
        item {SectionCard(tr(R.string.hardware_shortcuts),tr(R.string.hardware_help)) {
            SettingRow(tr(R.string.hardware_permission),if(grants.keys)tr(R.string.allowed) else tr(R.string.optional_permission_denied),{permission("keys")})
            labels.forEach {(key,label)->SettingRow(label,settings.hardwareBindings[key].orEmpty().ifEmpty {emptySet()}.joinToString(" + "){KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")}.ifBlank {tr(R.string.not_bound)},
                {HardwareCapture.begin();function=key})}
        }}
        item {SectionCard(tr(R.string.quick_settings_tile),tr(R.string.tile_help)) {}}
    }
    function?.let {key->AlertDialog(onDismissRequest={HardwareCapture.end();function=null},title={Text(labels[key].orEmpty())},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(tr(R.string.hardware_record_help))
        Text(captured.joinToString(" + "){KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")}.ifBlank {tr(R.string.waiting_keys)})
        TextButton(onClick={vm.bind(key,emptySet());function=null}){Text(tr(R.string.clear_binding))}
    }},confirmButton={TextButton(onClick={vm.bind(key,captured);HardwareCapture.end();function=null},enabled=captured.isNotEmpty()){Text(tr(R.string.save))}},dismissButton={TextButton(onClick={HardwareCapture.end();function=null}){Text(tr(R.string.cancel))}})}
}

@Composable fun DiagnosticsScreen(vm:EasyOverlayViewModel,export:()->Unit,permission:(String)->Unit) {
    val tr = rememberStrings()
    val runtime by vm.runtime.collectAsStateWithLifecycle();val events by vm.events.collectAsStateWithLifecycle();val grants by vm.permissions.collectAsStateWithLifecycle()
    val displays by vm.displays.collectAsStateWithLifecycle();val context=LocalContext.current
    val monitor by vm.monitoring.collectAsStateWithLifecycle();val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    var reveal by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionCard(tr(R.string.current_status)) {
            Text("EasyOverlay ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            Text(BuildConfig.APPLICATION_ID,style=MaterialTheme.typography.bodySmall)
            Text(tr(R.string.automatic_status, if(!runtime.autoMonitoring)tr(R.string.session_state_off) else if(runtime.monitorProblem==null)tr(R.string.session_state_on) else tr(R.string.state_paused)))
            Text(tr(R.string.monitor_source_label,tr.source(monitor.selectedSource.name)))
            monitor.fallbackReason?.let {Text(tr(R.string.monitor_fallback,tr.problem(it)))}
            runtime.restoreProblem?.let {Text(tr.problem(it),color=MaterialTheme.colorScheme.error)}
            if(context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted)Text(tr(R.string.background_restricted_help),color=MaterialTheme.colorScheme.error)
            runtime.monitorProblem?.let {Text(tr.problem(it),color=MaterialTheme.colorScheme.error)}
            Text(tr(R.string.recent_foreground, DiagnosticFormatter.time(runtime.observedAt,context)))
            Text(tr(R.string.foreground_target, DiagnosticFormatter.packageLabel(runtime.foregroundPackage,reveal,context)))
            Row {Text(tr(R.string.show_package_here),modifier=Modifier.weight(1f));Switch(reveal,{reveal=it})}
            Text(tr(R.string.manual_state_help),style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionCard(tr(R.string.permission_check)) {
            SettingRow(tr(R.string.overlay_permission),if(grants.overlay)tr(R.string.allowed) else tr(R.string.not_allowed),{permission("overlay")})
            SettingRow(tr(R.string.usage_access),if(grants.usage)tr(R.string.allowed) else tr(R.string.usage_optional_needed),{permission("usage")})
            SettingRow(tr(R.string.notifications),if(grants.notifications)tr(R.string.allowed) else tr(R.string.not_allowed),{permission("notifications")})
            Text(tr(R.string.maximum_opacity, capabilities.maximumPercent(0)))
        }}
        items(displays,key={it.id}){display->val session=runtime.sessions[display.id]
            SectionCard(tr.displayName(display.id)) {
                Text(tr(R.string.display_mode, session?.target?.owner?.let {if(it.name=="QUICK_USE")tr(R.string.manual_mode) else tr(R.string.rule_mode)} ?: tr(R.string.closed)))
                Text(tr(R.string.actual_image_opacity, session?.target?.assetId?.take(8) ?: tr(R.string.none),session?.target?.opacityPercent ?: 0))
                Text(tr(R.string.last_mount,DiagnosticFormatter.time(session?.mountedAt ?: 0,context)))
                runtime.failures[display.id]?.let {Text(tr.message(it),color=MaterialTheme.colorScheme.error)}
            }
        }
        item {SectionCard(tr(R.string.export_troubleshoot),tr(R.string.diagnostics_privacy)) {
            Button(onClick={val clipboard=context.getSystemService(ClipboardManager::class.java);clipboard.setPrimaryClip(ClipData.newPlainText("EasyOverlay diagnostics",DiagnosticFormatter.summary(context,vm.container,false)));vm.message(tr(R.string.diagnostics_copied))}){Text(tr(R.string.copy_summary))}
            OutlinedButton(onClick=export){Text(tr(R.string.export_diagnostics))}
            Text(tr(R.string.recover_automatic_help),style=MaterialTheme.typography.bodySmall)
        }}
        item {Text(tr(R.string.recent_event_count,events.size),style=MaterialTheme.typography.titleMedium)}
        items(events.asReversed()){event->Text(tr(R.string.diagnostic_event_line,DiagnosticFormatter.time(event.at,context),event.code,event.displayId?.toString() ?: "—"),style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable fun AboutScreen() {
    val tr = rememberStrings()
    val context=LocalContext.current
    val open:(String)->Unit={url->try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){}}
    var notices by remember {mutableStateOf(false)}
    val license by produceState("") {value=withContext(Dispatchers.IO){listOf("LICENSE.txt","Shizuku-API-LICENSE.txt","Dhizuku-API-LICENSE.txt").joinToString("\n\n") {name->context.assets.open(name).bufferedReader().use {it.readText()}}}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("EasyOverlay ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.headlineMedium)}
        item {Text(tr(R.string.about_description))}
        item {SectionCard(tr(R.string.about_author)) {SettingRow("Hycnyl","https://github.com/Hycnyl",{open("https://github.com/Hycnyl")})}}
        item {SectionCard(tr(R.string.about_repository)) {SettingRow("EasyOverlay","https://github.com/Hycnyl/EasyOverlay",{open("https://github.com/Hycnyl/EasyOverlay")})}}
        item {SettingRow(tr(R.string.about_open_source),"GPL-3.0",{notices=!notices})}
        if(notices) {
            item {Text(tr(R.string.about_fork))}
            item {Text(tr(R.string.about_sources))}
            item {Text(tr(R.string.about_libraries))}
            item {Text(license,style=MaterialTheme.typography.bodySmall)}
        }
    }
}
