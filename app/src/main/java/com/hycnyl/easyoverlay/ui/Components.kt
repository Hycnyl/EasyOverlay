package com.hycnyl.easyoverlay.ui

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
    if (displays.size>1 || selected !in displays.map { it.id }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            displays.forEach { display -> FilterChip(selected=selected==display.id,onClick={onSelect(display.id)},label={Text(display.label)}) }
            if (selected !in displays.map { it.id }) AssistChip(onClick={},label={Text("屏幕 $selected 已断开，请重新选择")})
        }
    }
}

@Composable fun AssetPreview(asset:OverlayAsset?,container:AppContainer,height:Dp=220.dp,opacity:Float=1f) {
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
                Text(if(failed)"图片无法预览，请重新选择" else "选择一张图片，马上开始",style=MaterialTheme.typography.bodyLarge)
                if(asset==null)Text("PNG · JPG · 静态 WebP · GIF",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else CircularProgressIndicator()
        }
    }
}

@Composable fun OpacityControl(value:Int,maximum:Int,onChange:(Int)->Unit,onFinish:()->Unit={}) {
    var help by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("不透明度",style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
        Text("$value%",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
    }
    Slider(value=value.toFloat().coerceIn(0f,maximum.toFloat()),onValueChange={onChange(it.toInt())},
        valueRange=0f..maximum.coerceAtLeast(1).toFloat(),onValueChangeFinished=onFinish,enabled=maximum>0)
    TextButton(onClick={help=true},contentPadding=PaddingValues(0.dp)) {Text("为什么最高是 $maximum%？")}
    if(help)AlertDialog(onDismissRequest={help=false},title={Text("优先保证触摸穿透")},text={Text("Android 对普通悬浮窗有触摸安全限制。本机允许的窗口不透明度最高为 $maximum%，滑块与实际窗口使用同一数值。PNG / GIF 的透明像素仍会保留。悬浮球所在区域用于控制，可拖到屏幕边缘。")},confirmButton={TextButton(onClick={help=false}){Text("明白了")}})
}

@Composable fun RuntimeBanner(state:RuntimeState,onDiagnostics:()->Unit) {
    val mode=when {
        state.sessions.values.any { it.target.owner==OverlayOwner.QUICK_USE } -> "快速使用正在显示"
        state.sessions.isNotEmpty() -> "应用规则正在显示"
        state.pendingDisplays.isNotEmpty() -> "正在准备图片"
        else -> "遮罩已关闭"
    }
    Card(onClick=onDiagnostics,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),shape=RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=if(state.sessions.isNotEmpty())MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,shape=RoundedCornerShape(50),modifier=Modifier.size(10.dp)){}
            Column(Modifier.weight(1f)) {Text(mode,style=MaterialTheme.typography.titleSmall)
                Text("自动监测：${if(!state.autoMonitoring)"关" else if(state.monitorProblem!=null)"暂停 / 受限" else "开"} · 轻触查看诊断",style=MaterialTheme.typography.bodySmall)}
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
