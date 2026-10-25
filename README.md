# EasyOverlay 6.0

Android 图片遮罩工具，支持 Android 13 及以上。正式包名 `com.hycnyl.easyoverlay`。

[下载安装包](https://github.com/Hycnyl/EasyOverlay/releases) · [施工规格](docs/FORK_V1_CONSTRUCTION.md) · [验证记录](docs/VALIDATION.md)

这是基于 [PerryKum/ImageOverlay](https://github.com/PerryKum/ImageOverlay) 的非官方 Fork，遵循 GPL-3.0。6.0 是从原版 5.2 延续的重大版本：独立身份、新存储、统一遮罩会话和 Material You 界面。保留上游许可与致谢。

## 快速使用

1. 在“快速使用”选择 PNG、JPG/JPEG、静态 WebP 或 GIF，图片会复制到私有空间。
2. 调整不透明度，首次启动时允许“在其他应用上层显示”。
3. 点击“显示遮罩”。从应用或会话通知停止。

无需创建组、绑定应用或授予使用情况访问。默认不透明度 60%，上限直接读取系统触摸安全限制（通常 80%）。PNG 的透明像素和 GIF 原始动画均保留。图片显示时铺满目标屏幕，可能拉伸。

## 应用自动遮罩

“应用遮罩” → 添加应用 → 选择图片和不透明度 → 保存。需要自动切换时，再主动开启“自动监测会话”并授权使用情况访问。每个应用、每块目标屏幕保存一条规则。

同一屏幕的快速使用优先于自动规则。停止快速使用后，依据当前前台应用重新匹配，不恢复过时的历史遮罩。停止自动监测不会关闭其他屏幕的手动会话。

## 图片、备份与高级控制

- 无需首启选择文件夹；默认存储位于应用私有空间。单张图片最多 32 MB，静态图片最多 6400 万像素，GIF 最多 1600 万像素。
- 设置 → 图片库 / 待分配项：复用图片、处理迁移冲突。仍被引用的图片受到删除保护。
- 设置 → 导出备份：包含版本化 JSON 与图片；卸载前请先备份。导入时校验 SHA-256，合并时保留现有规则，冲突项进入图片库。
- 设置 → 从 ImageOverlay 导入：重新授权旧目录，先扫描预览，再复制图片。旧目录始终只读。
- 设置 → 高级功能：双屏手动控制、实体键组合、可拖动悬浮球（默认关）。实体键服务为可选，不读取窗口内容。
- 系统快捷设置可添加 EasyOverlay 磁贴；通知可停止全部会话。诊断支持复制和导出脱敏日志，无自动联网。

EasyOverlay 可与原版并存，无法直接覆盖安装原版或自动继承它的私有数据、签名、图片 URI 授权。

## 验证与限制

使用 compileSdk 36、targetSdk 35、minSdk 33。按照施工文档，target 36 等待真实设备上的前台服务复核。持续会话采用 specialUse，未使用 dataSync 保活或无障碍遮罩绕过透明度限制。

副屏前台应用无法通过 UsageStats 可靠区分，V1 仅在主屏自动切换；副屏支持独立快速使用和规则编辑。系统安全页面、锁屏、权限弹窗和部分外接屏可能无法覆盖。系统终止会话后，需要用户主动重新开启。

真实 Android 13/14/15/16 设备上的触摸穿透、厂商省电策略、长期后台、双屏和实体键仍未验证；具体云端、CI 与模拟器结果见 [VALIDATION.md](docs/VALIDATION.md)。

## 构建

JDK 17、Android SDK 36、Build Tools 36.0.0、仓库内 Gradle Wrapper。环境设置见 [CLOUD_ENVIRONMENT.md](docs/CLOUD_ENVIRONMENT.md)。

```sh
bash scripts/setup-android-env.sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

正式发行包只有一个 `EasyOverlay-v6.0.apk`，同时包含 arm64-v8a、armeabi-v7a、x86、x86_64。没有 ABI 分包。

```sh
export EASYOVERLAY_SIGNING_PROPERTIES=/private/path/signing.properties
bash scripts/build-release.sh
```

签名文件格式见 [发行说明](docs/RELEASING.md)。密钥、密码、APK、SDK 缓存与本地配置均不可提交。原版回退标签：`baseline-5.2`；各阶段均有独立 Git 提交。
