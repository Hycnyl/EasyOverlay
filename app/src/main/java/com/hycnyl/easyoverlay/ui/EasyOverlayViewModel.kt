package com.hycnyl.easyoverlay.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hycnyl.easyoverlay.EasyOverlayApplication
import com.hycnyl.easyoverlay.data.*
import com.hycnyl.easyoverlay.domain.*
import com.hycnyl.easyoverlay.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PermissionState(val overlay:Boolean=false,val usage:Boolean=false,val notifications:Boolean=false,val keys:Boolean=false)
data class RuleDraft(val packageName:String,val displayId:Int=0,val assetId:String?=null,val opacity:Int=60,val auto:Boolean=true)

class EasyOverlayViewModel(application:Application):AndroidViewModel(application) {
    val container=(application as EasyOverlayApplication).container
    val library=container.library.state
    val ready=container.library.ready
    val storageProblem=container.library.problem
    val runtime=container.runtime.asStateFlow()
    val settings=container.settings
    val displays=container.displays.displays
    val events=container.events.events
    val maxOpacity get()=container.maximumPercent
    val messages=container.messages.asSharedFlow()
    private val _busy=MutableStateFlow<String?>(null);val busy=_busy.asStateFlow()
    private val _permissions=MutableStateFlow(PermissionState());val permissions=_permissions.asStateFlow()
    private val _editing=MutableStateFlow<RuleDraft?>(null);val editing=_editing.asStateFlow()
    private val _apps=MutableStateFlow<List<InstalledApp>>(emptyList());val apps=_apps.asStateFlow()
    private val _loadingApps=MutableStateFlow(false);val loadingApps=_loadingApps.asStateFlow()
    private val _legacy=MutableStateFlow<LegacyScan?>(null);val legacy=_legacy.asStateFlow()
    private val _backup=MutableStateFlow<BackupPreview?>(null);val backup=_backup.asStateFlow()
    private val _migration=MutableStateFlow<MigrationReport?>(null);val migration=_migration.asStateFlow()

    init { refreshPermissions() }
    fun refreshPermissions() { val app=getApplication<Application>();_permissions.value=PermissionState(Permissions.overlay(app),Permissions.usage(app),Permissions.notifications(app),Permissions.accessibility(app)) }
    fun message(value:String) { container.messages.tryEmit(value) }
    private fun work(label:String,block:suspend ()->Unit) {
        if (_busy.value!=null) return
        viewModelScope.launch {
            _busy.value=label
            try { block() }
            catch (cancel:CancellationException) { throw cancel }
            catch (e:Exception) {
                val detail=e.message.orEmpty()
                val safe=if ((e is IllegalArgumentException || e is IllegalStateException || e is java.io.IOException) &&
                    detail.isNotBlank() && !detail.contains("://") && !detail.contains("/data/") && !detail.contains("/storage/")) detail.take(300)
                    else "操作未完成。请检查图片是否损坏、文件授权和可用存储空间，再重试。"
                message(safe);container.events.record("USER_OPERATION_FAILED")
            } finally { _busy.value=null }
        }
    }
    fun loadApps() {
        if (_loadingApps.value || _apps.value.isNotEmpty()) return
        viewModelScope.launch { _loadingApps.value=true;try { _apps.value=PackageCatalog(getApplication()).load() }
            catch (_:Exception) { message("应用列表暂时不可用，可以手动输入应用包名") } finally { _loadingApps.value=false } }
    }
    fun startQuick(displayId:Int) { OverlaySessionService.request(getApplication(),OverlaySessionService.START_QUICK,displayId) }
    fun stopQuick(displayId:Int) { OverlaySessionService.request(getApplication(),OverlaySessionService.STOP_QUICK,displayId) }
    fun stopAll() { OverlaySessionService.request(getApplication(),OverlaySessionService.STOP_ALL) }
    fun setAuto(value:Boolean) { OverlaySessionService.request(getApplication(),OverlaySessionService.SET_AUTO,enabled=value) }
    fun setBall(value:Boolean) { OverlaySessionService.request(getApplication(),OverlaySessionService.SET_BALL,enabled=value) }
    private fun refreshSession() { OverlaySessionService.request(getApplication(),OverlaySessionService.REFRESH) }
    fun importQuick(uri:Uri,display:Int)=work("正在验证并保存图片") {
        val asset=container.images.import(uri)
        val opacity=library.value.quickSelections.firstOrNull { it.displayId==display }?.opacityPercent ?: 60
        container.library.selectQuick(display,asset.id,opacity.coerceAtMost(maxOpacity));refreshSession()
        container.events.record("IMAGE_IMPORTED",display)
    }
    fun chooseQuick(assetId:String,display:Int)=work("正在选择图片") { container.library.selectQuick(display,assetId,60.coerceAtMost(maxOpacity));refreshSession() }
    fun clearQuick(display:Int)=work("正在清除最近图片") { stopQuick(display);container.library.selectQuick(display,null);refreshSession() }
    fun quickOpacity(display:Int,value:Int)=work("正在保存不透明度") {
        val selected=library.value.quickSelections.firstOrNull { it.displayId==display } ?: return@work
        container.library.selectQuick(display,selected.assetId,value.coerceIn(0,maxOpacity));refreshSession()
    }
    fun beginRule(pkg:String,display:Int=0) {
        val rule=library.value.rules.firstOrNull { it.packageName==pkg && it.displayId==display }
        _editing.value=rule?.let { RuleDraft(it.packageName,it.displayId,it.assetId,it.opacityPercent.coerceAtMost(maxOpacity),it.autoEnabled) } ?: RuleDraft(pkg,display,opacity=60.coerceAtMost(maxOpacity))
    }
    fun updateDraft(change:(RuleDraft)->RuleDraft) { _editing.value=_editing.value?.let(change) }
    fun cancelEdit() { if (_busy.value==null) _editing.value=null }
    fun importRule(uri:Uri)=work("正在验证并保存图片") {
        val pkg=_editing.value?.packageName ?: return@work
        val asset=container.images.import(uri)
        if (_editing.value?.packageName==pkg) updateDraft { it.copy(assetId=asset.id) }
    }
    fun saveDraft()=work("正在保存应用规则") {
        val draft=_editing.value ?: return@work
        val asset=draft.assetId ?: error("请先选择一张图片")
        val rule=AppRule(draft.packageName,draft.displayId,asset,draft.opacity.coerceIn(0,maxOpacity),draft.auto)
        container.library.saveRule(rule);_editing.value=null;refreshSession();message("应用遮罩已保存")
    }
    fun deleteRule(rule:AppRule)=work("正在移除应用规则") { container.library.removeRule(rule.key);refreshSession() }
    fun toggleRule(rule:AppRule,value:Boolean)=work("正在保存规则") { container.library.saveRule(rule.copy(autoEnabled=value));refreshSession() }
    fun deleteAsset(id:String)=work("正在移除未使用图片") { container.library.deleteUnusedAsset(id) }
    fun assignToQuick(id:String,display:Int)=chooseQuick(id,display)
    fun theme(value:ThemeChoice)=work("正在保存外观") { container.preferences.setTheme(value) }
    fun cutout(value:Boolean)=work("正在保存显示设置") { container.preferences.setCoverCutout(value) }
    fun bind(function:String,keys:Set<Int>)=work("正在保存快捷键") { container.preferences.bind(function,keys);HardwareCapture.end();message("快捷键已保存") }
    fun scanLegacy(uri:Uri)=work("只读扫描旧配置") {
        runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        _legacy.value=container.legacy.scan(uri)
    }
    fun dismissLegacy() { if (_busy.value==null) _legacy.value=null }
    fun importLegacy()=work("正在复制旧图片，源目录保持只读") {
        val scan=_legacy.value ?: return@work
        _migration.value=container.legacy.import(scan);_legacy.value=null;refreshSession()
    }
    fun dismissMigration() { _migration.value=null }
    fun exportBackup(uri:Uri)=work("正在导出备份") { container.backups.export(uri);message("备份已导出") }
    fun previewBackup(uri:Uri)=work("正在验证备份") { _backup.value?.close();_backup.value=container.backups.preview(uri) }
    fun dismissBackup() { if (_busy.value==null) { _backup.value?.close();_backup.value=null } }
    fun restoreBackup()=work("正在合并备份") {
        val preview=_backup.value ?: return@work
        _backup.value=null;container.backups.restore(preview);refreshSession();message("备份已导入；冲突项可在图片库中分配")
    }
    fun exportDiagnostics(uri:Uri)=work("正在导出脱敏诊断") {
        withContext(Dispatchers.IO) { val resolver=getApplication<Application>().contentResolver
            (resolver.openOutputStream(uri,"wt") ?: error("无法写入诊断文件")).use { it.write(DiagnosticFormatter.summary(getApplication(),container,false).toByteArray()) } }
        message("脱敏诊断已导出")
    }
    override fun onCleared() { _backup.value?.close();HardwareCapture.end();super.onCleared() }
}
