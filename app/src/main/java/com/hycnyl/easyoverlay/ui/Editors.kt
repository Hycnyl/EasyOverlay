package com.hycnyl.easyoverlay.ui

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
    val draft by vm.editing.collectAsStateWithLifecycle();val library by vm.library.collectAsStateWithLifecycle()
    val displays by vm.displays.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle();val apps by vm.apps.collectAsStateWithLifecycle()
    var showLibrary by remember {mutableStateOf(false)}
    draft?.let {rule->
        ModalBottomSheet(onDismissRequest={vm.cancelEdit()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).verticalScroll(rememberScrollState()).padding(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(apps.firstOrNull {it.packageName==rule.packageName}?.label ?: "应用遮罩",style=MaterialTheme.typography.headlineSmall)
                Text(rule.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                DisplaySelector(displays,rule.displayId,{vm.beginRule(rule.packageName,it)})
                AssetPreview(library.assets.firstOrNull {it.id==rule.assetId},vm.container,height=180.dp,opacity=rule.opacity/100f)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=pickImage,enabled=busy==null){Text(if(rule.assetId==null)"导入图片" else "更换图片")}
                    if(library.assets.isNotEmpty())TextButton(onClick={showLibrary=true}){Text("从图片库选择")}
                }
                Text("显示时适配目标屏幕，图片可能拉伸。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OpacityControl(rule.opacity,vm.maxOpacity,{value->vm.updateDraft {it.copy(opacity=value)}})
                Row {Text("自动启用此规则",modifier=Modifier.weight(1f));Switch(rule.auto,{value->vm.updateDraft {it.copy(auto=value)}})}
                Text(if(rule.displayId==0)"保存后，在设置中开启自动监测即可随应用切换。" else "UsageStats 无法可靠区分副屏前台应用。此屏规则可保存；V1 请从图片库选择到该屏快速使用。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={vm.saveDraft()},enabled=rule.assetId!=null && busy==null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("保存应用遮罩")}
                TextButton(onClick={vm.cancelEdit()},enabled=busy==null,modifier=Modifier.fillMaxWidth()){Text("取消")}
            }
        }
        if(showLibrary)ImageLibrary(vm,{showLibrary=false}){asset->vm.updateDraft {it.copy(assetId=asset.id)};showLibrary=false}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ImageLibrary(vm:EasyOverlayViewModel,onDismiss:()->Unit,onChoose:(OverlayAsset)->Unit) {
    val library by vm.library.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.9f),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Text("图片库",style=MaterialTheme.typography.headlineSmall)}
            item {Text("图片保存在 EasyOverlay 私有空间。待分配和迁移冲突项可在这里选择；移除仍被引用的图片会受到保护。",style=MaterialTheme.typography.bodyMedium)}
            if(library.assets.isEmpty())item {Text("图片库还是空的，请先导入图片。")}
            items(library.assets.asReversed(),key={it.id}){asset->
                SectionCard("${asset.mimeType.substringAfter('/').uppercase()} · ${asset.width} × ${asset.height}") {
                    AssetPreview(asset,vm.container,height=130.dp)
                    library.unassigned.filter {it.assetId==asset.id}.forEach {item->
                        Text(item.label,style=MaterialTheme.typography.titleSmall)
                        Text(item.reason,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val used=library.rules.any {it.assetId==asset.id} || library.quickSelections.any {it.assetId==asset.id}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick={onChoose(asset)},enabled=busy==null){Text("选择此图片")}
                        TextButton(onClick={vm.deleteAsset(asset.id)},enabled=!used && busy==null){Text(if(used)"使用中" else "移除")}
                    }
                }
            }
        }
    }
}

@Composable fun ImportDialogs(vm:EasyOverlayViewModel) {
    val legacy by vm.legacy.collectAsStateWithLifecycle();val backup by vm.backup.collectAsStateWithLifecycle()
    val report by vm.migration.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    legacy?.let {scan->AlertDialog(onDismissRequest={vm.dismissLegacy()},title={Text("旧配置扫描预览")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("发现 ${scan.plan.groupCount} 个历史组、${scan.items.size} 张配置图片；${scan.items.count {it.source!=null}} 项可读取。源目录只读，导入将复制图片。")
            Text("自动规则默认关闭；冲突项保存在图片库待分配。副屏配置也需重新选择目标屏幕。",style=MaterialTheme.typography.bodySmall)
            scan.items.forEach {row->Text("${row.item.groupName} / ${row.item.configName}\n${row.item.packageName ?: "未绑定应用"} · ${row.note}",style=MaterialTheme.typography.bodySmall)}
        }},confirmButton={TextButton(onClick={vm.importLegacy()},enabled=busy==null && scan.items.any {it.source!=null}){Text("复制并导入")}},dismissButton={TextButton(onClick={vm.dismissLegacy()},enabled=busy==null){Text("取消")}})}
    backup?.let {preview->AlertDialog(onDismissRequest={vm.dismissBackup()},title={Text("合并 EasyOverlay 备份？")},text={Text("已校验 ${preview.state.assets.size} 张图片、${preview.state.rules.size} 条规则。现有规则和快速使用图片优先保留；冲突项放入图片库待分配。此备份不包含运行中的窗口和系统权限。")},
        confirmButton={TextButton(onClick={vm.restoreBackup()},enabled=busy==null){Text("合并导入")}},dismissButton={TextButton(onClick={vm.dismissBackup()},enabled=busy==null){Text("取消")}})}
    report?.let {value->AlertDialog(onDismissRequest={vm.dismissMigration()},title={Text("旧配置导入结果")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
        Text("成功 ${value.imported} 项 · 已导入跳过 ${value.skipped} 项 · 失败 ${value.failed.size} 项")
        value.failed.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
        Text("旧文件未被修改。失败项可修复图片后重新扫描导入。")
    }},confirmButton={TextButton(onClick={vm.dismissMigration()}){Text("完成")}})}
}
