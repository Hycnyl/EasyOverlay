package com.hycnyl.easyoverlay.ui

import com.hycnyl.easyoverlay.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hycnyl.easyoverlay.domain.OverlayAsset

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RuleEditor(vm:EasyOverlayViewModel,pickImage:()->Unit) {
    val tr = rememberStrings()
    val draft by vm.editing.collectAsStateWithLifecycle();val library by vm.library.collectAsStateWithLifecycle()
    val displays by vm.displays.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val apps by vm.apps.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle();val settings by vm.settings.collectAsStateWithLifecycle()
    var showLibrary by remember {mutableStateOf(false)}
    draft?.let {rule->
        ModalBottomSheet(onDismissRequest={vm.cancelEdit()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(apps.firstOrNull {it.packageName==rule.packageName}?.label ?: tr(R.string.tab_rules),style=MaterialTheme.typography.headlineSmall)
                Text(rule.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                DisplaySelector(displays,rule.displayId,{vm.beginRule(rule.packageName,it)})
                AssetPreview(library.assets.firstOrNull {it.id==rule.assetId},vm.container,height=180.dp,opacity=rule.opacity/100f)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=pickImage,enabled=busy==null){Text(if(rule.assetId==null)tr(R.string.import_image) else tr(R.string.replace_image))}
                    if(library.assets.isNotEmpty())TextButton(onClick={showLibrary=true}){Text(tr(R.string.choose_from_library))}
                }
                Text(tr(R.string.stretch_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val maximum=capabilities.maximumPercent(rule.displayId)
                LaunchedEffect(maximum,rule.packageName,rule.displayId) {if(rule.opacity>maximum)vm.updateDraft {it.copy(opacity=maximum)}}
                key(rule.packageName,rule.displayId) {OpacityControl(rule.opacity.coerceAtMost(maximum),maximum,{value->vm.updateDraft {it.copy(opacity=value)}},
                    suppressWarning=settings.suppressUnsafeOpacityWarning,onSuppressWarning=vm::suppressOpacityWarning)}
                Row {Text(tr(R.string.auto_enable_rule),modifier=Modifier.weight(1f));Switch(rule.auto,{value->vm.updateDraft {it.copy(auto=value)}})}
                Text(if(rule.displayId==0)tr(R.string.rule_saved_help) else tr(R.string.secondary_rule_help),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={vm.saveDraft()},enabled=rule.assetId!=null && busy==null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(tr(R.string.save_app_overlay))}
                TextButton(onClick={vm.cancelEdit()},enabled=busy==null,modifier=Modifier.fillMaxWidth()){Text(tr(R.string.cancel))}
            }
        }
        if(showLibrary)ImageLibrary(vm,{showLibrary=false}){asset->vm.updateDraft {it.copy(assetId=asset.id)};showLibrary=false}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ImageLibrary(vm:EasyOverlayViewModel,onDismiss:()->Unit,onChoose:(OverlayAsset)->Unit) {
    val tr = rememberStrings()
    val library by vm.library.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.9f),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Text(tr(R.string.image_library),style=MaterialTheme.typography.headlineSmall)}
            item {Text(tr(R.string.library_intro),style=MaterialTheme.typography.bodyMedium)}
            if(library.assets.isEmpty())item {Text(tr(R.string.library_empty))}
            items(library.assets.asReversed(),key={it.id}){asset->
                SectionCard("${asset.mimeType.substringAfter('/').uppercase()} · ${asset.width} × ${asset.height}") {
                    AssetPreview(asset,vm.container,height=130.dp)
                    library.unassigned.filter {it.assetId==asset.id}.forEach {item->
                        Text(item.label,style=MaterialTheme.typography.titleSmall)
                        Text(tr.message(item.reason),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val used=library.rules.any {it.assetId==asset.id} || library.quickSelections.any {it.assetId==asset.id}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick={onChoose(asset)},enabled=busy==null){Text(tr(R.string.choose_this_image))}
                        TextButton(onClick={vm.deleteAsset(asset.id)},enabled=!used && busy==null){Text(if(used)tr(R.string.in_use) else tr(R.string.remove))}
                    }
                }
            }
        }
    }
}

@Composable fun ImportDialogs(vm:EasyOverlayViewModel) {
    val tr = rememberStrings()
    val backup by vm.backup.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    backup?.let { preview ->
        AlertDialog(
            onDismissRequest={vm.dismissBackup()},
            title={Text(tr(R.string.merge_backup_title))},
            text={Text(tr(R.string.merge_backup_details, preview.state.assets.size, preview.state.rules.size))},
            confirmButton={TextButton(onClick={vm.restoreBackup()},enabled=busy==null){Text(tr(R.string.merge_import))}},
            dismissButton={TextButton(onClick={vm.dismissBackup()},enabled=busy==null){Text(tr(R.string.cancel))}}
        )
    }
}
