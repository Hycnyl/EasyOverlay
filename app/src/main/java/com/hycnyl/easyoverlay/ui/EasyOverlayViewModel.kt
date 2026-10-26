package com.hycnyl.easyoverlay.ui

import com.hycnyl.easyoverlay.R

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hycnyl.easyoverlay.EasyOverlayApplication
import com.hycnyl.easyoverlay.data.*
import com.hycnyl.easyoverlay.domain.*
import com.hycnyl.easyoverlay.platform.*
import com.hycnyl.easyoverlay.platform.monitoring.MonitoringMode
import com.hycnyl.easyoverlay.platform.privileged.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PermissionState(val overlay:Boolean=false,val usage:Boolean=false,val notifications:Boolean=false,val keys:Boolean=false)
data class RuleDraft(val packageName:String,val displayId:Int=0,val assetId:String?=null,val opacity:Int=60,val auto:Boolean=true)

class EasyOverlayViewModel(application:Application):AndroidViewModel(application) {
    val container=(application as EasyOverlayApplication).container
    private val tr get() = Strings(getApplication())
    val library=container.library.state
    val ready=container.library.ready
    val storageProblem=container.library.problem
    val runtime=container.runtime.asStateFlow()
    val settings=container.settings
    val displays=container.displays.displays
    val events=container.events.events
    val capabilities=container.overlayCapabilities.state
    val monitoring=container.monitoring.state
    val shizuku=container.privileged.state
    val dhizuku=container.dhizuku.state
    val canRestoreOptimization=container.privileged.canRestore
    val canRestoreDhizuku=container.dhizuku.canRestore
    val maxOpacity get()=container.maximumPercent
    fun maximumOpacity(display:Int)=container.overlayCapabilities.maximumPercent(display)
    val messages=container.messages.asSharedFlow()
    private val _busy=MutableStateFlow<String?>(null);val busy=_busy.asStateFlow()
    private val _permissions=MutableStateFlow(PermissionState());val permissions=_permissions.asStateFlow()
    private val _editing=MutableStateFlow<RuleDraft?>(null);val editing=_editing.asStateFlow()
    private val _apps=MutableStateFlow<List<InstalledApp>>(emptyList());val apps=_apps.asStateFlow()
    private val _loadingApps=MutableStateFlow(false);val loadingApps=_loadingApps.asStateFlow()
    private val _backup=MutableStateFlow<BackupPreview?>(null);val backup=_backup.asStateFlow()
    private val _optimization=MutableStateFlow<OptimizationPreview?>(null);val optimization=_optimization.asStateFlow()
    private val _dhizukuPreview=MutableStateFlow<DhizukuNotificationPreview?>(null);val dhizukuPreview=_dhizukuPreview.asStateFlow()
    private val _optimizationResult=MutableStateFlow<OptimizationResult?>(null);val optimizationResult=_optimizationResult.asStateFlow()

    init { refreshPermissions() }
    fun refreshPermissions() {
        val app=getApplication<Application>()
        AccessibilityOverlayBridge.refresh()
        container.monitoring.refreshAvailability();container.privileged.connect();container.dhizuku.connect()
        _permissions.value=PermissionState(Permissions.overlay(app),Permissions.usage(app),Permissions.notifications(app),Permissions.accessibility(app))
    }
    fun message(value:String) { container.messages.tryEmit(value) }
    private fun work(label:String,block:suspend ()->Unit) {
        if (_busy.value!=null) return
        viewModelScope.launch {
            _busy.value=label
            try { block() }
            catch (_:TimeoutCancellationException) { message(tr(R.string.authorization_unavailable));container.events.record("USER_OPERATION_TIMEOUT") }
            catch (cancel:CancellationException) { throw cancel }
            catch (e:Exception) {
                val detail=e.message.orEmpty()
                val safe=if ((e is IllegalArgumentException || e is IllegalStateException || e is java.io.IOException) &&
                    detail.isNotBlank() && !detail.contains("://") && !detail.contains("/data/") && !detail.contains("/storage/")) detail.take(300)
                    else tr(R.string.operation_failed)
                message(tr.problem(tr.message(safe)));container.events.record("USER_OPERATION_FAILED")
            } finally { _busy.value=null }
        }
    }
    fun loadApps() {
        if (_loadingApps.value || _apps.value.isNotEmpty()) return
        viewModelScope.launch { _loadingApps.value=true;try { _apps.value=PackageCatalog(getApplication()).load() }
            catch (_:Exception) { message(tr(R.string.apps_unavailable)) } finally { _loadingApps.value=false } }
    }
    fun startQuick(displayId:Int) { OverlaySessionService.request(getApplication(),OverlaySessionService.START_QUICK,displayId) }
    fun stopQuick(displayId:Int) { OverlaySessionService.request(getApplication(),OverlaySessionService.STOP_QUICK,displayId) }
    fun stopAll() { OverlaySessionService.request(getApplication(),OverlaySessionService.STOP_ALL) }
    fun setAuto(value:Boolean) { OverlaySessionService.request(getApplication(),OverlaySessionService.SET_AUTO,enabled=value) }
    fun setBall(value:Boolean) { OverlaySessionService.request(getApplication(),OverlaySessionService.SET_BALL,enabled=value) }
    private fun refreshSession() { OverlaySessionService.request(getApplication(),OverlaySessionService.REFRESH) }
    fun importQuick(uri:Uri,display:Int)=work(tr(R.string.busy_import_image)) {
        val asset=container.images.import(uri)
        val opacity=library.value.quickSelections.firstOrNull { it.displayId==display }?.opacityPercent ?: 60
        container.library.selectQuick(display,asset.id,opacity.coerceAtMost(maximumOpacity(display)));refreshSession()
        container.events.record("IMAGE_IMPORTED",display)
    }
    fun chooseQuick(assetId:String,display:Int)=work(tr(R.string.busy_select_image)) { container.library.selectQuick(display,assetId,60.coerceAtMost(maximumOpacity(display)));refreshSession() }
    fun clearQuick(display:Int)=work(tr(R.string.busy_clear_recent)) { stopQuick(display);container.library.selectQuick(display,null);refreshSession() }
    fun quickOpacity(display:Int,value:Int)=work(tr(R.string.busy_opacity)) {
        val selected=library.value.quickSelections.firstOrNull { it.displayId==display } ?: return@work
        container.library.selectQuick(display,selected.assetId,value.coerceIn(0,maximumOpacity(display)));refreshSession()
    }
    fun beginRule(pkg:String,display:Int=0) {
        val rule=library.value.rules.firstOrNull { it.packageName==pkg && it.displayId==display }
        _editing.value=rule?.let { RuleDraft(it.packageName,it.displayId,it.assetId,it.opacityPercent.coerceAtMost(maximumOpacity(display)),it.autoEnabled) } ?: RuleDraft(pkg,display,opacity=60.coerceAtMost(maximumOpacity(display)))
    }
    fun updateDraft(change:(RuleDraft)->RuleDraft) { _editing.value=_editing.value?.let(change) }
    fun cancelEdit() { if (_busy.value==null) _editing.value=null }
    fun importRule(uri:Uri)=work(tr(R.string.busy_import_image)) {
        val pkg=_editing.value?.packageName ?: return@work
        val asset=container.images.import(uri)
        if (_editing.value?.packageName==pkg) updateDraft { it.copy(assetId=asset.id) }
    }
    fun saveDraft()=work(tr(R.string.busy_save_app_rule)) {
        val draft=_editing.value ?: return@work
        val asset=draft.assetId ?: error(tr(R.string.choose_image_first))
        val rule=AppRule(draft.packageName,draft.displayId,asset,draft.opacity.coerceIn(0,maximumOpacity(draft.displayId)),draft.auto)
        container.library.saveRule(rule);_editing.value=null;refreshSession();message(tr(R.string.app_overlay_saved))
    }
    fun deleteRule(rule:AppRule)=work(tr(R.string.busy_remove_rule)) { container.library.removeRule(rule.key);refreshSession() }
    fun toggleRule(rule:AppRule,value:Boolean)=work(tr(R.string.busy_save_rule)) { container.library.saveRule(rule.copy(autoEnabled=value));refreshSession() }
    fun deleteAsset(id:String)=work(tr(R.string.busy_remove_asset)) { container.library.deleteUnusedAsset(id) }
    fun assignToQuick(id:String,display:Int)=chooseQuick(id,display)
    fun theme(value:ThemeChoice)=work(tr(R.string.busy_theme)) { container.preferences.setTheme(value) }
    fun cutout(value:Boolean)=work(tr(R.string.busy_display_settings)) { container.preferences.setCoverCutout(value) }
    fun monitorMode(value:MonitoringMode)=work(tr(R.string.monitoring_setup)) { container.preferences.setMonitoringMode(value.name);container.monitoring.setMode(value);refreshSession() }
    fun recoverAutomatic(value:Boolean)=work(tr(R.string.recover_automatic)) { container.preferences.setRecoverAutomaticSession(value);refreshSession() }
    fun suppressOpacityWarning(value:Boolean) { viewModelScope.launch {
        try {container.preferences.setSuppressUnsafeOpacityWarning(value)}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){message(tr(R.string.operation_failed))}
    } }
    fun previewOptimization()=work(tr(R.string.optimize_title)) { _optimization.value=container.privileged.previewOptimization() }
    fun dismissOptimization() { if (_busy.value==null) _optimization.value=null }
    fun applyOptimization()=work(tr(R.string.optimize_title)) {
        val preview=_optimization.value ?: return@work
        _optimization.value=null;_optimizationResult.value=container.privileged.applyOptimization(preview);refreshPermissions()
    }
    fun restoreOptimization()=work(tr(R.string.restore_changes)) { _optimizationResult.value=container.privileged.restoreOptimization();refreshPermissions() }
    fun previewDhizuku()=work(tr(R.string.dhizuku_notifications)) { _dhizukuPreview.value=container.dhizuku.previewNotificationPermission() }
    fun dismissDhizuku() { if (_busy.value==null) _dhizukuPreview.value=null }
    fun applyDhizuku()=work(tr(R.string.dhizuku_notifications)) {
        val preview=_dhizukuPreview.value ?: return@work
        _dhizukuPreview.value=null;_optimizationResult.value=container.dhizuku.grantOwnNotificationPermission(preview);refreshPermissions()
    }
    fun restoreDhizuku()=work(tr(R.string.restore_changes)) { _optimizationResult.value=container.dhizuku.restoreOwnNotificationPermission();refreshPermissions() }
    fun dismissOptimizationResult() { _optimizationResult.value=null }
    fun bind(function:String,keys:Set<Int>)=work(tr(R.string.busy_keys)) { container.preferences.bind(function,keys);HardwareCapture.end();message(tr(R.string.keys_saved)) }
    fun exportBackup(uri:Uri)=work(tr(R.string.busy_export_backup)) { container.backups.export(uri);message(tr(R.string.backup_exported)) }
    fun previewBackup(uri:Uri)=work(tr(R.string.busy_validate_backup)) { _backup.value?.close();_backup.value=container.backups.preview(uri) }
    fun dismissBackup() { if (_busy.value==null) { _backup.value?.close();_backup.value=null } }
    fun restoreBackup()=work(tr(R.string.busy_merge_backup)) {
        val preview=_backup.value ?: return@work
        _backup.value=null;container.backups.restore(preview);refreshSession();message(tr(R.string.backup_imported))
    }
    fun exportDiagnostics(uri:Uri)=work(tr(R.string.busy_export_diagnostics)) {
        withContext(Dispatchers.IO) { val resolver=getApplication<Application>().contentResolver
            (resolver.openOutputStream(uri,"wt") ?: error(tr(R.string.diagnostics_write_failed))).use { it.write(DiagnosticFormatter.summary(getApplication(),container,false).toByteArray()) } }
        message(tr(R.string.diagnostics_exported))
    }
    override fun onCleared() { _backup.value?.close();HardwareCapture.end();super.onCleared() }
}
