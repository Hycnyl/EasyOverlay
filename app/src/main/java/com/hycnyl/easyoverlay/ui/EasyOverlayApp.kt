package com.hycnyl.easyoverlay.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hycnyl.easyoverlay.platform.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EasyOverlayApp(vm:EasyOverlayViewModel) {
    val context=LocalContext.current
    val ready by vm.ready.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val storageProblem by vm.storageProblem.collectAsStateWithLifecycle()
    val snackbars=remember {SnackbarHostState()}
    var tab by rememberSaveable {mutableIntStateOf(0)}
    var page by rememberSaveable {mutableStateOf<String?>(null)}
    var appPicker by rememberSaveable {mutableStateOf(false)}
    var showLibrary by rememberSaveable {mutableStateOf(false)}
    var libraryDisplay by rememberSaveable {mutableIntStateOf(0)}
    var imageTarget by rememberSaveable {mutableStateOf("quick:0")}
    var pendingStart by rememberSaveable {mutableStateOf("")}
    var notificationAsked by rememberSaveable {mutableStateOf(false)}
    val execute:(String)->Unit={action->when(action.substringBefore(':')) {
        "quick"->vm.startQuick(action.substringAfter(':').toIntOrNull() ?: 0)
        "auto"->vm.setAuto(true)
        "ball"->vm.setBall(true)
    }}
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        vm.refreshPermissions()
        if(!granted)vm.message("通知未开启。可在应用内停止遮罩，也可在系统“活动应用”中结束会话。")
        if(pendingStart.isNotBlank())execute(pendingStart)
        pendingStart=""
    }
    val openSystem:(String)->Unit={kind->
        val intent=when(kind) {"overlay"->Permissions.overlayIntent(context);"usage"->Permissions.usageIntent(context);"notifications"->Permissions.notificationIntent(context);else->Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)}
        try {context.startActivity(intent)}catch(_:Exception){try {context.startActivity(Intent(Settings.ACTION_SETTINGS))}catch(_:Exception){vm.message("无法打开系统设置，请手动前往应用权限设置。")}}
    }
    val start:(String)->Unit={action->
        when {
            !Permissions.overlay(context)->{vm.message("请先允许“在其他应用上层显示”，返回后重新点击启动。 ");openSystem("overlay")}
            action=="auto" && !Permissions.usage(context)->{vm.message("自动切换需要使用情况访问。授权后返回开启；快速使用无需此权限。");openSystem("usage")}
            !Permissions.notifications(context) && !notificationAsked->{notificationAsked=true;pendingStart=action;notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}
            else->execute(action)
        }
    }
    val image=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null) {
        if(imageTarget=="rule")vm.importRule(uri) else vm.importQuick(uri,imageTarget.substringAfter(':').toIntOrNull() ?: 0)
    }}
    val pick:(String)->Unit={target->imageTarget=target;image.launch(arrayOf("image/png","image/jpeg","image/webp","image/gif"))}
    val legacy=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->uri?.let {vm.scanLegacy(it)}}
    val backupOut=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->uri?.let {vm.exportBackup(it)}}
    val backupIn=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let {vm.previewBackup(it)}}
    val diagnostics=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->uri?.let {vm.exportDiagnostics(it)}}
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME){vm.refreshPermissions()}
    LaunchedEffect(vm){vm.messages.collect {snackbars.showSnackbar(it)}}
    BackHandler(enabled=page!=null){page=null}
    val titles=mapOf("advanced" to "高级功能","diagnostics" to "诊断与帮助","about" to "关于 EasyOverlay")
    Scaffold(topBar={TopAppBar(title={Text(page?.let {titles[it]} ?: "EasyOverlay")},navigationIcon={if(page!=null)IconButton(onClick={page=null}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},actions={if(page==null)IconButton(onClick={page="diagnostics"}){Icon(Icons.Default.Info,"打开诊断")}})},
        bottomBar={if(page==null)NavigationBar {
            val labels=listOf("快速使用","应用遮罩","设置");val icons=listOf(Icons.Default.Layers,Icons.Default.Apps,Icons.Default.Settings)
            labels.forEachIndexed {index,label->NavigationBarItem(selected=tab==index,onClick={tab=index},icon={Icon(icons[index],null)},label={Text(label)})}
        }},snackbarHost={SnackbarHost(snackbars)}) {padding->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(busy!=null){LinearProgressIndicator(Modifier.fillMaxWidth());Text(busy.orEmpty(),style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=20.dp,vertical=4.dp))}
            if(storageProblem!=null)Card(Modifier.padding(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)) {
                Text(storageProblem.orEmpty(),modifier=Modifier.padding(16.dp));TextButton(onClick={tab=2;page=null}){Text("打开备份与修复设置")}
            }
            if(!ready)Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator()}
            else when(page) {
                "advanced"->AdvancedScreen(vm,openSystem,{if(it)start("ball")else vm.setBall(false)})
                "diagnostics"->DiagnosticsScreen(vm,{diagnostics.launch("EasyOverlay-diagnostics-${System.currentTimeMillis()}.txt")},openSystem)
                "about"->AboutScreen()
                else->when(tab) {
                    0->QuickUseScreen(vm,{pick("quick:$it")},{start("quick:$it")},{libraryDisplay=it;showLibrary=true},{page="diagnostics"})
                    1->AppRulesScreen(vm,{appPicker=true},{if(vm.runtime.value.autoMonitoring){tab=2}else start("auto")})
                    else->SettingsScreen(vm,{if(it)start("auto")else vm.setAuto(false)},openSystem,{page=it},
                        {backupOut.launch("EasyOverlay-backup-${System.currentTimeMillis()}.zip")},
                        {backupIn.launch(arrayOf("application/zip","application/octet-stream"))},
                        {legacy.launch(null)},{libraryDisplay=0;showLibrary=true})
                }
            }
        }
    }
    if(appPicker)AppPicker(vm,{appPicker=false})
    RuleEditor(vm,{pick("rule")})
    if(showLibrary)ImageLibrary(vm,{showLibrary=false}){asset->vm.chooseQuick(asset.id,libraryDisplay);showLibrary=false;tab=0;page=null}
    ImportDialogs(vm)
}
