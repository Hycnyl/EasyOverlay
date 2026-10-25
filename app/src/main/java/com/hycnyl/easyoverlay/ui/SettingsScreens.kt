package com.hycnyl.easyoverlay.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    backupExport:()->Unit,backupImport:()->Unit,legacyImport:()->Unit,library:()->Unit) {
    val prefs by vm.settings.collectAsStateWithLifecycle();val runtime by vm.runtime.collectAsStateWithLifecycle();val grants by vm.permissions.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("按你的方式使用",style=MaterialTheme.typography.headlineMedium)}
        item {SectionCard("权限与自动监测") {
            SettingRow("自动监测会话",if(runtime.autoMonitoring)if(runtime.monitorProblem==null)"已开启 · 手动遮罩始终优先" else "已暂停或受限 · 查看诊断" else "关闭 · 只有此功能需要 Usage Access",{auto(!runtime.autoMonitoring)}) {
                Switch(runtime.autoMonitoring,{auto(it)})
            }
            if(!runtime.serviceRunning && prefs.lastAutoRequested)Text("上次会话已结束，需要你再次主动开启。",style=MaterialTheme.typography.bodySmall)
            SettingRow("在其他应用上层显示",if(grants.overlay)"已允许" else "启动遮罩时需要",{permission("overlay")})
            SettingRow("使用情况访问",if(grants.usage)"已允许 · 仅自动切换使用" else "未允许 · 快速使用不受影响",{permission("usage")})
            SettingRow("会话通知",if(grants.notifications)"已允许 · 可从通知停止遮罩" else "未允许 · 系统可能仅在“活动应用”中显示会话",{permission("notifications")})
        }}
        item {SectionCard("外观") {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(ThemeChoice.SYSTEM to "跟随系统",ThemeChoice.LIGHT to "浅色",ThemeChoice.DARK to "深色").forEach {(value,label)->FilterChip(prefs.theme==value,{vm.theme(value)},label={Text(label)})}
            }
            Text("支持系统动态取色和字体大小设置。",style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionCard("图片与备份","默认保存在应用私有空间，首次打开无需选择文件夹。卸载前请导出备份。") {
            SettingRow("图片库 / 待分配项","查看图片、处理旧配置冲突或选择到快速使用",library)
            SettingRow("导出 EasyOverlay 备份","包含版本化 JSON 与原始图片，不含权限和运行会话",backupExport)
            SettingRow("导入 EasyOverlay 备份","先验证完整性，再合并；现有规则优先保留",backupImport)
            SettingRow("从 ImageOverlay 导入","只读扫描你授权的旧目录，预览后复制图片",legacyImport)
        }}
        item {SectionCard("更多控制") {
            SettingRow("诊断与帮助","权限、运行模式、最近切换与脱敏事件",{navigate("diagnostics")})
            SettingRow("高级功能","双屏、实体按键、悬浮球与显示范围",{navigate("advanced")})
            SettingRow("关于 EasyOverlay","${BuildConfig.VERSION_NAME} · GPL-3.0 · 非官方 Fork",{navigate("about")})
        }}
        if(runtime.serviceRunning)item {OutlinedButton(onClick={vm.stopAll()},modifier=Modifier.fillMaxWidth()){Text("停止全部会话与遮罩")}}
    }
}

@Composable fun AdvancedScreen(vm:EasyOverlayViewModel,permission:(String)->Unit,ball:(Boolean)->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle();val runtime by vm.runtime.collectAsStateWithLifecycle();val displays by vm.displays.collectAsStateWithLifecycle();val grants by vm.permissions.collectAsStateWithLifecycle()
    val captured by HardwareCapture.keys.collectAsStateWithLifecycle();var function by remember {mutableStateOf<String?>(null)}
    DisposableEffect(Unit){onDispose {HardwareCapture.end()}}
    val labels=mapOf("toggle_main" to "切换主屏遮罩","toggle_secondary" to "切换副屏遮罩","toggle_all" to "切换全部屏幕","toggle_ball" to "显示 / 隐藏悬浮球")
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionCard("显示屏", "检测到 ${displays.size} 块可用屏幕。副屏支持独立快速使用；UsageStats 不能可靠判断每块屏幕的前台应用，因此 V1 仅在主屏自动切换。") {
            displays.forEach {Text("${it.label} · ID ${it.id}")}
            SettingRow("延伸到刘海区域","系统仍可能限制安全页面、导航条或外接屏覆盖",{vm.cutout(!settings.coverCutout)}){Switch(settings.coverCutout,{vm.cutout(it)})}
        }}
        item {SectionCard("悬浮球","默认关闭。轻触切换主屏，拖动移位并吸附边缘，长按停止全部。控件所在区域接收触摸，请避开游戏按钮。") {
            SettingRow("显示悬浮球",if(runtime.floatingVisible)"当前已显示" else "当前关闭",{ball(!runtime.floatingVisible)}){Switch(runtime.floatingVisible,ball)}
        }}
        item {SectionCard("实体键快捷操作","可选无障碍服务仅接收你绑定的按键，不读取窗口内容，不参与应用识别或绕过透明度限制。系统可能不向应用提供所有实体键。") {
            SettingRow("实体键权限",if(grants.keys)"已允许" else "未允许 · 不影响主流程",{permission("keys")})
            labels.forEach {(key,label)->SettingRow(label,settings.hardwareBindings[key].orEmpty().ifEmpty {emptySet()}.joinToString(" + "){KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")}.ifBlank {"未绑定"},
                {HardwareCapture.begin();function=key})}
        }}
        item {SectionCard("快捷设置磁贴","从系统快捷设置编辑面板添加 EasyOverlay。轻触可切换已选择的快速使用图片；正在显示时可关闭当前屏幕。无可用图片或缺少权限时会打开应用。") {}}
    }
    function?.let {key->AlertDialog(onDismissRequest={HardwareCapture.end();function=null},title={Text(labels[key].orEmpty())},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("按下最多三个实体键，组合不能与其他绑定重叠。返回键可取消。")
        Text(captured.joinToString(" + "){KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_")}.ifBlank {"等待按键…"})
        TextButton(onClick={vm.bind(key,emptySet());function=null}){Text("清除此绑定")}
    }},confirmButton={TextButton(onClick={vm.bind(key,captured);HardwareCapture.end();function=null},enabled=captured.isNotEmpty()){Text("保存")}},dismissButton={TextButton(onClick={HardwareCapture.end();function=null}){Text("取消")}})}
}

@Composable fun DiagnosticsScreen(vm:EasyOverlayViewModel,export:()->Unit,permission:(String)->Unit) {
    val runtime by vm.runtime.collectAsStateWithLifecycle();val events by vm.events.collectAsStateWithLifecycle();val grants by vm.permissions.collectAsStateWithLifecycle()
    val displays by vm.displays.collectAsStateWithLifecycle();val context=LocalContext.current
    var reveal by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionCard("当前状态") {
            Text("EasyOverlay ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            Text(BuildConfig.APPLICATION_ID,style=MaterialTheme.typography.bodySmall)
            Text("自动监测：${if(!runtime.autoMonitoring)"关闭" else if(runtime.monitorProblem==null)"开启" else "暂停 / 受限"}")
            if(context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted)Text("系统已限制此应用的后台运行，离开应用后会话可能中断。请检查系统电池管理。",color=MaterialTheme.colorScheme.error)
            runtime.monitorProblem?.let {Text(when(it){"USAGE_PERMISSION_MISSING"->"使用情况访问权限已撤销，请重新授权。";"SCREEN_LOCKED"->"屏幕关闭或已锁定，自动规则已暂停。";"BACKGROUND_START_NOT_ALLOWED"->"系统拒绝后台启动，请回到应用重新开启。";else->"监听或权限受限，请检查系统设置并重新开启。"},color=MaterialTheme.colorScheme.error)}
            Text("最近前台事件：${DiagnosticFormatter.time(runtime.observedAt)}")
            Text("前台目标：${DiagnosticFormatter.packageLabel(runtime.foregroundPackage,reveal)}")
            Row {Text("在此页显示包名",modifier=Modifier.weight(1f));Switch(reveal,{reveal=it})}
            Text("手动快速使用优先于本屏自动规则。进程结束后不会把旧窗口当作仍在运行。",style=MaterialTheme.typography.bodySmall)
        }}
        item {SectionCard("权限检查") {
            SettingRow("悬浮窗",if(grants.overlay)"已允许" else "未允许",{permission("overlay")})
            SettingRow("使用情况访问",if(grants.usage)"已允许" else "未允许，仅自动模式需要",{permission("usage")})
            SettingRow("通知",if(grants.notifications)"已允许" else "未允许",{permission("notifications")})
            Text("系统最大不透明度：${vm.maxOpacity}%")
        }}
        items(displays,key={it.id}){display->val session=runtime.sessions[display.id]
            SectionCard(display.label) {
                Text("模式：${session?.target?.owner?.let {if(it.name=="QUICK_USE")"手动快速使用" else "应用规则"} ?: "已关闭"}")
                Text("图片：${session?.target?.assetId?.take(8) ?: "无"} · 实际不透明度：${session?.target?.opacityPercent ?: 0}%")
                Text("最近挂载：${DiagnosticFormatter.time(session?.mountedAt ?: 0)}")
                runtime.failures[display.id]?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }
        }
        item {SectionCard("导出与排查","导出和复制始终隐藏目标包名，不包含文件 URI、用户目录或图片路径；无自动网络上报。") {
            Button(onClick={val clipboard=context.getSystemService(ClipboardManager::class.java);clipboard.setPrimaryClip(ClipData.newPlainText("EasyOverlay diagnostics",DiagnosticFormatter.summary(context,vm.container,false)));vm.message("脱敏诊断已复制")}){Text("复制脱敏摘要")}
            OutlinedButton(onClick=export){Text("导出诊断文件")}
            Text("频繁被系统停止：检查系统电池管理，并从应用主动重新开启。系统安全页、锁屏、权限弹窗和部分外接屏可能无法覆盖。",style=MaterialTheme.typography.bodySmall)
        }}
        item {Text("最近 ${events.size} 条本地事件",style=MaterialTheme.typography.titleMedium)}
        items(events.asReversed()){event->Text("${DiagnosticFormatter.time(event.at)}\n${event.code} · 屏幕 ${event.displayId ?: "—"}",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable fun AboutScreen() {
    val context=LocalContext.current
    val license by produceState("") {value=withContext(Dispatchers.IO){context.assets.open("LICENSE.txt").bufferedReader().use {it.readText()}}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("EasyOverlay ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.headlineMedium)}
        item {Text("基于 PerryKum / ImageOverlay 的非官方 Fork，保留上游版权与 GPL-3.0 许可证。EasyOverlay 使用独立包名，可与原版并存。")}
        item {Text("源码：https://github.com/Hycnyl/EasyOverlay\n上游：https://github.com/PerryKum/ImageOverlay")}
        item {Text("第三方组件：AndroidX / Compose / DataStore（Apache-2.0）、Kotlin / kotlinx.coroutines（Apache-2.0）、Gson（Apache-2.0）。图片解码使用 Android 系统组件。")}
        item {Text("支持范围：Android 13–16；compile SDK 36，target SDK 35。尚无真实设备验收记录，触摸穿透、长期后台、双屏与实体键需按设备验证。",style=MaterialTheme.typography.bodySmall)}
        item {Text(license,style=MaterialTheme.typography.bodySmall)}
    }
}
