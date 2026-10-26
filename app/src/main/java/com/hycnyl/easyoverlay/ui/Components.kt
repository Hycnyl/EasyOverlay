package com.hycnyl.easyoverlay.ui

import com.hycnyl.easyoverlay.R

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hycnyl.easyoverlay.AppContainer
import com.hycnyl.easyoverlay.data.AssetDecoder
import com.hycnyl.easyoverlay.domain.*
import com.hycnyl.easyoverlay.platform.DisplayInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun SectionCard(title:String,subtitle:String?=null,content:@Composable ColumnScope.()->Unit) {
    Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable fun DisplaySelector(displays:List<DisplayInfo>,selected:Int,onSelect:(Int)->Unit) {
    val tr = rememberStrings()
    if (displays.size>1 || selected !in displays.map { it.id }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            displays.forEach { display -> FilterChip(selected=selected==display.id,onClick={onSelect(display.id)},label={Text(tr.displayName(display.id))}) }
            if (selected !in displays.map { it.id }) AssistChip(onClick={},label={Text(tr(R.string.display_disconnected, selected))})
        }
    }
}

@Composable fun AssetPreview(asset:OverlayAsset?,container:AppContainer,height:Dp=220.dp,opacity:Float=1f) {
    val tr = rememberStrings()
    var failed by remember(asset?.id) { mutableStateOf(false) }
    val drawable by produceState<Drawable?>(null,asset?.id) {
        if(asset!=null) value=withContext(Dispatchers.IO) { runCatching { AssetDecoder.decode(container.library.assetFile(asset),1000,700) }
            .onFailure { failed=true }.getOrNull() }
    }
    val cellA=MaterialTheme.colorScheme.surfaceContainerHighest
    val cellB=MaterialTheme.colorScheme.surface
    Surface(shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().height(height)) {
        Box(contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val side=18.dp.toPx();var row=0;var y=0f
                while(y<size.height) {var x=0f;var col=0
                    while(x<size.width) {drawRect(if((row+col)%2==0)cellA else cellB,Offset(x,y),Size(side,side));x+=side;col++};y+=side;row++}
            }
            if(drawable!=null) AndroidView(factory={ImageView(it).apply { scaleType=ImageView.ScaleType.FIT_CENTER;importantForAccessibility=ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO }},
                update={view->view.setImageDrawable(drawable);view.alpha=opacity.coerceIn(0f,1f)},modifier=Modifier.fillMaxSize().padding(12.dp))
            else if(asset==null || failed) Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Image,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
                Text(if(failed)tr(R.string.preview_failed) else tr(R.string.preview_empty),style=MaterialTheme.typography.bodyLarge)
                if(asset==null)Text(tr(R.string.supported_images),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else CircularProgressIndicator()
        }
    }
}

@Composable fun OpacityControl(value:Int,maximum:Int,onChange:(Int)->Unit,onFinish:()->Unit={},
    suppressWarning:Boolean=false,onSuppressWarning:(Boolean)->Unit={}) {
    val tr = rememberStrings()
    var help by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    var accepted by remember { mutableIntStateOf(value.coerceAtMost(maximum)) }
    LaunchedEffect(value,maximum) { if(!dragging && !warning)accepted=value.coerceAtMost(maximum) }
    val unsafe=OpacityPolicy.requiresHighOpacityWarning(value)
    val red=if(androidx.compose.foundation.isSystemInDarkTheme())Color(0xFFFF6B6B) else Color(0xFFD32F2F)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(tr(R.string.opacity),style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        Text(tr(R.string.opacity_percent, value),style=MaterialTheme.typography.titleLarge,color=if(unsafe)red else MaterialTheme.colorScheme.primary)
    }
    Slider(value=value.toFloat().coerceIn(0f,maximum.toFloat()),onValueChange={
        if(!dragging){accepted=value.coerceAtMost(maximum);dragging=true};onChange(it.toInt())
    },valueRange=0f..maximum.coerceAtLeast(1).toFloat(),onValueChangeFinished={
        if(OpacityPolicy.requiresHighOpacityWarning(value) && !suppressWarning){skip=false;warning=true}
        else {accepted=value;onFinish()}
        dragging=false
    },enabled=maximum>0,colors=SliderDefaults.colors(
        thumbColor=if(unsafe)red else MaterialTheme.colorScheme.primary,
        activeTrackColor=if(unsafe)red else MaterialTheme.colorScheme.primary))
    if(unsafe)Text(tr(R.string.unsafe_opacity_status),color=red,style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={help=true},contentPadding=PaddingValues(0.dp)) {Text(tr(R.string.opacity_limit_question, maximum))}
    if(help)AlertDialog(onDismissRequest={help=false},title={Text(tr(R.string.touch_safety_title))},text={Text(tr(if(maximum>80)R.string.opacity_extended_help else R.string.opacity_safe_help))},confirmButton={TextButton(onClick={help=false}){Text(tr(R.string.understood))}})
    if(warning)AlertDialog(onDismissRequest={warning=false;onChange(accepted.coerceAtMost(maximum))},
        title={Text(tr(R.string.unsafe_opacity_title))},text={Column {
            Text(tr(R.string.unsafe_opacity_warning))
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(skip,{skip=it});Text(tr(R.string.unsafe_opacity_skip))}
        }},confirmButton={TextButton(onClick={
            if(skip)onSuppressWarning(true);accepted=value.coerceAtMost(maximum);warning=false;onFinish()
        }){Text(tr(R.string.unsafe_opacity_confirm))}})
}

@Composable fun RuntimeBanner(state:RuntimeState,onDiagnostics:()->Unit) {
    val tr = rememberStrings()
    val mode=when {
        state.sessions.values.any { it.target.owner==OverlayOwner.QUICK_USE } -> tr(R.string.quick_running)
        state.sessions.isNotEmpty() -> tr(R.string.rule_running)
        state.pendingDisplays.isNotEmpty() -> tr(R.string.image_preparing)
        else -> tr(R.string.overlay_closed)
    }
    Card(onClick=onDiagnostics,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),shape=RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=if(state.sessions.isNotEmpty())MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,shape=RoundedCornerShape(50),modifier=Modifier.size(10.dp)){}
            Column(Modifier.weight(1f)) {Text(mode,style=MaterialTheme.typography.titleSmall)
                Text(tr(R.string.runtime_banner_detail, if(!state.autoMonitoring)tr(R.string.session_state_off) else if(state.monitorProblem!=null)tr(R.string.state_paused) else tr(R.string.session_state_on)),style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable fun SettingRow(title:String,detail:String,onClick:()->Unit,trailing:@Composable (()->Unit)?=null) {
    Surface(onClick=onClick,color=Color.Transparent,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {Text(title,style=MaterialTheme.typography.bodyLarge);Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            trailing?.invoke()
        }
    }
}
