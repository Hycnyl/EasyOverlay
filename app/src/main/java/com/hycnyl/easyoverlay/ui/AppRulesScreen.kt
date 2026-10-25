package com.hycnyl.easyoverlay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hycnyl.easyoverlay.domain.AppRule

@Composable fun AppRulesScreen(vm:EasyOverlayViewModel,onAdd:()->Unit,onAuto:()->Unit) {
    val library by vm.library.collectAsStateWithLifecycle();val apps by vm.apps.collectAsStateWithLifecycle();val runtime by vm.runtime.collectAsStateWithLifecycle()
    var query by rememberSaveable {mutableStateOf("")};var delete by remember {mutableStateOf<AppRule?>(null)}
    LaunchedEffect(Unit){vm.loadApps()}
    val filtered=library.rules.filter {rule->query.isBlank() || rule.packageName.contains(query,true) || apps.firstOrNull {it.packageName==rule.packageName}?.label?.contains(query,true)==true}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text("每个应用，一张专属遮罩",style=MaterialTheme.typography.headlineMedium)}
        item {SectionCard(if(runtime.autoMonitoring)"自动监测已开启" else "需要时再开启自动监测", "为应用保存图片，开启规则后随前台应用切换。快速使用始终优先。") {
            FilledTonalButton(onClick=onAuto){Text(if(runtime.autoMonitoring)"管理自动监测" else "开启自动监测")}
        }}
        item {Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("添加应用遮罩")}}
        item {OutlinedTextField(value=query,onValueChange={query=it},label={Text("搜索应用名称或包名")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())}
        if(filtered.isEmpty())item {SectionCard(if(library.rules.isEmpty())"还没有应用规则" else "没有匹配的应用", "选择应用，再导入图片和调整不透明度。未开启监测时，规则可以照常编辑。") {}}
        items(filtered,key={it.key}) {rule ->
            val app=apps.firstOrNull {it.packageName==rule.packageName}
            Card(onClick={vm.beginRule(rule.packageName,rule.displayId)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(app?.icon!=null)Image(app.icon.asImageBitmap(),null,Modifier.size(44.dp))else Icon(Icons.Default.Apps,null,Modifier.size(40.dp))
                        Column(Modifier.weight(1f)){Text(app?.label ?: rule.packageName,style=MaterialTheme.typography.titleMedium);Text(rule.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        IconButton(onClick={delete=rule}){Icon(Icons.Default.Delete,"移除 ${app?.label ?: rule.packageName} 的规则")}
                    }
                    Text("${if(rule.displayId==0)"主屏" else "屏幕 ${rule.displayId} · 仅手动分配"} · 不透明度 ${rule.opacityPercent.coerceAtMost(vm.maxOpacity)}%",style=MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment=Alignment.CenterVertically){Text("自动启用此规则",modifier=Modifier.weight(1f));Switch(checked=rule.autoEnabled,onCheckedChange={vm.toggleRule(rule,it)})}
                }
            }
        }
    }
    delete?.let {rule->AlertDialog(onDismissRequest={delete=null},title={Text("移除应用规则？")},text={Text("规则将移除，原图片仍保留在图片库中。")},confirmButton={TextButton(onClick={vm.deleteRule(rule);delete=null}){Text("移除")}},dismissButton={TextButton(onClick={delete=null}){Text("取消")}})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppPicker(vm:EasyOverlayViewModel,onDismiss:()->Unit) {
    val apps by vm.apps.collectAsStateWithLifecycle();val loading by vm.loadingApps.collectAsStateWithLifecycle();val library by vm.library.collectAsStateWithLifecycle()
    var query by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(Unit){vm.loadApps()}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("选择应用",style=MaterialTheme.typography.headlineSmall)
            OutlinedTextField(query,{query=it},label={Text("名称 / 包名，也可直接输入包名")},modifier=Modifier.fillMaxWidth(),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(query.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+".toRegex()))TextButton(onClick={vm.beginRule(query.trim());onDismiss()}){Text("使用包名 $query")}
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
                items(apps.filter {query.isBlank() || it.label.contains(query,true) || it.packageName.contains(query,true)},key={it.packageName}) {app->
                    ListItem(headlineContent={Text(app.label)},supportingContent={Text(app.packageName)},leadingContent={app.icon?.let {Image(it.asImageBitmap(),null,Modifier.size(40.dp))}},
                        trailingContent={if(library.rules.any {it.packageName==app.packageName})Text("已配置",style=MaterialTheme.typography.labelSmall)},
                        modifier=Modifier.clickable {vm.beginRule(app.packageName);onDismiss()})
                }
            }
        }
    }
}
