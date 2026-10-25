package com.hycnyl.easyoverlay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hycnyl.easyoverlay.domain.OverlayOwner

@Composable fun QuickUseScreen(vm:EasyOverlayViewModel,pickImage:(Int)->Unit,start:(Int)->Unit,openLibrary:(Int)->Unit,onDiagnostics:()->Unit) {
    val library by vm.library.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val displays by vm.displays.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var display by rememberSaveable {mutableIntStateOf(0)}
    LaunchedEffect(displays) {if(displays.none {it.id==display})display=0}
    val selection=library.quickSelections.firstOrNull {it.displayId==display}
    val asset=library.assets.firstOrNull {it.id==selection?.assetId}
    var opacity by remember(selection,vm.maxOpacity) {mutableIntStateOf((selection?.opacityPercent ?: 60).coerceAtMost(vm.maxOpacity))}
    val session=runtime.sessions[display]
    val manual=session?.target?.owner==OverlayOwner.QUICK_USE
    val pending=display in runtime.pendingDisplays
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text("你的图片，随屏而行",style=MaterialTheme.typography.headlineMedium)
            Text("选图 · 调整 · 显示。无需绑定应用。",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        item {RuntimeBanner(runtime,onDiagnostics)}
        if(displays.size>1)item {DisplaySelector(displays,display,{display=it})}
        item {AssetPreview(asset,vm.container,height=if(asset==null)160.dp else 190.dp,opacity=opacity/100f)}
        item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(asset!=null)Text("${asset.width} × ${asset.height} · ${if(asset.isAnimatedGif)"GIF 动图" else asset.mimeType.substringAfter('/').uppercase()} · 显示时适配全屏，可能拉伸",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                OutlinedButton(onClick={pickImage(display)},enabled=busy==null,modifier=Modifier.weight(1f)){Icon(Icons.Default.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text(if(asset==null)"选择图片" else "更换图片")}
                if(library.assets.isNotEmpty())OutlinedButton(onClick={openLibrary(display)},enabled=busy==null){Text("图片库")}
            }
            if(asset!=null)TextButton(onClick={vm.clearQuick(display)},enabled=busy==null){Text("清除最近选择（保留图片库）")}
        }}
        item {SectionCard("让下层操作保持顺畅") {OpacityControl(opacity,vm.maxOpacity,{opacity=it},{if(asset!=null)vm.quickOpacity(display,opacity)})}}
        if(runtime.autoMonitoring)item {Text("手动优先：启动快速使用后，本屏自动规则暂停；停止后按最新前台应用恢复。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        runtime.failures[display]?.let {failure->item {Text(failure,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={start(display)}){Text("重试显示")}}}
        item {Text("关闭后可从应用或会话通知重新控制。系统安全页面可能限制显示；后台持续运行取决于系统与设备设置。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    Surface(tonalElevation=2.dp) {
        Button(onClick={if(manual || pending)vm.stopQuick(display) else start(display)},
            enabled=manual || pending || (asset!=null && busy==null),
            modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).heightIn(min=56.dp)) {
            Icon(if(manual || pending)Icons.Default.Stop else Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp))
            Text(if(manual)"停止快速遮罩" else if(pending)"取消启动" else "显示遮罩")
        }
    }
    }
}
