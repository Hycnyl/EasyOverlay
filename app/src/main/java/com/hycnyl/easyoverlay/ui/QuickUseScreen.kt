package com.hycnyl.easyoverlay.ui

import com.hycnyl.easyoverlay.R

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
    val tr = rememberStrings()
    val library by vm.library.collectAsStateWithLifecycle()
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val displays by vm.displays.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var display by rememberSaveable {mutableIntStateOf(0)}
    LaunchedEffect(displays) {if(displays.none {it.id==display})display=0}
    val selection=library.quickSelections.firstOrNull {it.displayId==display}
    val asset=library.assets.firstOrNull {it.id==selection?.assetId}
    val maximum=capabilities.maximumPercent(display)
    var opacity by remember(display,selection,maximum) {mutableIntStateOf((selection?.opacityPercent ?: 60).coerceAtMost(maximum))}
    val session=runtime.sessions[display]
    val manual=session?.target?.owner==OverlayOwner.QUICK_USE
    val pending=display in runtime.pendingDisplays
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(tr(R.string.quick_title),style=MaterialTheme.typography.headlineMedium)
            Text(tr(R.string.quick_subtitle),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        item {RuntimeBanner(runtime,onDiagnostics)}
        if(displays.size>1)item {DisplaySelector(displays,display,{display=it})}
        item {AssetPreview(asset,vm.container,height=if(asset==null)160.dp else 190.dp,opacity=opacity/100f)}
        item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(asset!=null)Text(tr(R.string.asset_details, asset.width, asset.height, if(asset.isAnimatedGif)tr(R.string.gif_animated) else asset.mimeType.substringAfter('/').uppercase()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                OutlinedButton(onClick={pickImage(display)},enabled=busy==null,modifier=Modifier.weight(1f)){Icon(Icons.Default.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text(if(asset==null)tr(R.string.choose_image) else tr(R.string.replace_image))}
                if(library.assets.isNotEmpty())OutlinedButton(onClick={openLibrary(display)},enabled=busy==null){Text(tr(R.string.image_library))}
            }
            if(asset!=null)TextButton(onClick={vm.clearQuick(display)},enabled=busy==null){Text(tr(R.string.clear_recent))}
        }}
        item {SectionCard(tr(R.string.touch_priority)) {
            key(display,asset?.id) {OpacityControl(opacity,maximum,{opacity=it},{if(asset!=null)vm.quickOpacity(display,opacity)},settings.suppressUnsafeOpacityWarning,vm::suppressOpacityWarning)}
            if((selection?.opacityPercent ?: 0)>maximum)Text(tr(R.string.opacity_fallback),style=MaterialTheme.typography.bodySmall)
            if(display!=0 && capabilities.accessibilityConnected)Text(tr(R.string.secondary_trusted_limited),style=MaterialTheme.typography.bodySmall)
        }}
        if(runtime.autoMonitoring)item {Text(tr(R.string.manual_priority_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        runtime.failures[display]?.let {failure->item {Text(tr.message(failure),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={start(display)}){Text(tr(R.string.retry_show))}}}
        item {Text(tr(R.string.overlay_limit_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    Surface(tonalElevation=2.dp) {
        Button(onClick={if(manual || pending)vm.stopQuick(display) else start(display)},
            enabled=manual || pending || (asset!=null && busy==null),
            modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).heightIn(min=56.dp)) {
            Icon(if(manual || pending)Icons.Default.Stop else Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp))
            Text(if(manual)tr(R.string.stop_quick) else if(pending)tr(R.string.cancel_start) else tr(R.string.show_overlay))
        }
    }
    }
}
