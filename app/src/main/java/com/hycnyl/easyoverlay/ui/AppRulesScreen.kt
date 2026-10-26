package com.hycnyl.easyoverlay.ui

import com.hycnyl.easyoverlay.R

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
    val tr = rememberStrings()
    val library by vm.library.collectAsStateWithLifecycle();val apps by vm.apps.collectAsStateWithLifecycle();val runtime by vm.runtime.collectAsStateWithLifecycle()
    var query by rememberSaveable {mutableStateOf("")};var delete by remember {mutableStateOf<AppRule?>(null)}
    LaunchedEffect(Unit){vm.loadApps()}
    val filtered=library.rules.filter {rule->query.isBlank() || rule.packageName.contains(query,true) || apps.firstOrNull {it.packageName==rule.packageName}?.label?.contains(query,true)==true}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Text(tr(R.string.rules_title),style=MaterialTheme.typography.headlineMedium)}
        item {SectionCard(if(runtime.autoMonitoring)tr(R.string.monitor_on) else tr(R.string.monitor_offer), tr(R.string.rules_intro)) {
            FilledTonalButton(onClick=onAuto){Text(if(runtime.autoMonitoring)tr(R.string.manage_monitor) else tr(R.string.enable_monitor))}
        }}
        item {Button(onClick=onAdd,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text(tr(R.string.add_rule))}}
        item {OutlinedTextField(value=query,onValueChange={query=it},label={Text(tr(R.string.search_apps))},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())}
        if(filtered.isEmpty())item {SectionCard(if(library.rules.isEmpty())tr(R.string.no_rules) else tr(R.string.no_matching_apps), tr(R.string.rules_empty_help)) {}}
        items(filtered,key={it.key}) {rule ->
            val app=apps.firstOrNull {it.packageName==rule.packageName}
            Card(onClick={vm.beginRule(rule.packageName,rule.displayId)},modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(app?.icon!=null)Image(app.icon.asImageBitmap(),null,Modifier.size(44.dp))else Icon(Icons.Default.Apps,null,Modifier.size(40.dp))
                        Column(Modifier.weight(1f)){Text(app?.label ?: rule.packageName,style=MaterialTheme.typography.titleMedium);Text(rule.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        IconButton(onClick={delete=rule}){Icon(Icons.Default.Delete,tr(R.string.remove_rule_description, app?.label ?: rule.packageName))}
                    }
                    Text(tr(R.string.rule_details, if(rule.displayId==0)tr(R.string.display_main) else tr(R.string.screen_manual_only,rule.displayId), rule.opacityPercent.coerceAtMost(vm.maxOpacity)),style=MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment=Alignment.CenterVertically){Text(tr(R.string.auto_enable_rule),modifier=Modifier.weight(1f));Switch(checked=rule.autoEnabled,onCheckedChange={vm.toggleRule(rule,it)})}
                }
            }
        }
    }
    delete?.let {rule->AlertDialog(onDismissRequest={delete=null},title={Text(tr(R.string.remove_rule_title))},text={Text(tr(R.string.remove_rule_help))},confirmButton={TextButton(onClick={vm.deleteRule(rule);delete=null}){Text(tr(R.string.remove))}},dismissButton={TextButton(onClick={delete=null}){Text(tr(R.string.cancel))}})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppPicker(vm:EasyOverlayViewModel,onDismiss:()->Unit) {
    val tr = rememberStrings()
    val apps by vm.apps.collectAsStateWithLifecycle();val loading by vm.loadingApps.collectAsStateWithLifecycle();val library by vm.library.collectAsStateWithLifecycle()
    var query by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(Unit){vm.loadApps()}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(tr(R.string.choose_app),style=MaterialTheme.typography.headlineSmall)
            OutlinedTextField(query,{query=it},label={Text(tr(R.string.app_search_hint))},modifier=Modifier.fillMaxWidth(),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(query.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+".toRegex()))TextButton(onClick={vm.beginRule(query.trim());onDismiss()}){Text(tr(R.string.use_package, query))}
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
                items(apps.filter {query.isBlank() || it.label.contains(query,true) || it.packageName.contains(query,true)},key={it.packageName}) {app->
                    ListItem(headlineContent={Text(app.label)},supportingContent={Text(app.packageName)},leadingContent={app.icon?.let {Image(it.asImageBitmap(),null,Modifier.size(40.dp))}},
                        trailingContent={if(library.rules.any {it.packageName==app.packageName})Text(tr(R.string.configured),style=MaterialTheme.typography.labelSmall)},
                        modifier=Modifier.clickable {vm.beginRule(app.packageName);onDismiss()})
                }
            }
        }
    }
}
