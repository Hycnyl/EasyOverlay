# EasyOverlay 6.1

Android 图片遮罩工具，支持 Android 13 及以上。作者：[Hycnyl](https://github.com/Hycnyl)，包名 `com.hycnyl.easyoverlay`。

[下载安装包](https://github.com/Hycnyl/EasyOverlay/releases) · [6.1 变更规格](docs/CHANGES_6_1.md) · [验证记录](docs/VALIDATION_6_1.md)

EasyOverlay 独立开发和维护，遵循 GPL-3.0，保留 [ImageOverlay](https://github.com/PerryKum/ImageOverlay) 的许可与上游致谢。6.0 已由用户在实机验收基本功能后转为正式发布，6.1 沿用相同包名、数据格式和发布签名，可覆盖升级。

## 快速使用

1. 选择 PNG、JPG/JPEG、静态 WebP 或 GIF，图片复制到应用私有空间。
2. 调整不透明度，允许“在其他应用上层显示”，或连接无障碍增强服务。
3. 点击“显示遮罩”，从应用、通知、悬浮球或已配置的快捷键停止。

无需创建应用规则或开启自动监测。默认不透明度 60%，普通窗口遵守系统触摸安全上限（通常 80%）。无障碍服务实际连接并支持可信窗口时，主屏可调至 100%；超过 80% 需要确认，可勾选“不再提醒”，高值滑块始终保持红色。断开无障碍后限制至普通窗口安全值，无普通悬浮窗权限则关闭窗口。副屏继续使用普通安全上限。

PNG 的透明像素和 GIF 动画保留；图片会铺满目标屏幕，可能拉伸。

## 应用自动遮罩

“应用遮罩” → 添加应用 → 选择图片和不透明度 → 保存。设置中主动开启“自动监测会话”。每个应用、每块屏幕保存一条规则。

设置 → **监测与后台运行**：

- 自动模式优先使用无障碍窗口事件，其次 Shizuku 任务快照，再回退使用情况访问；也可选择优先来源，或仅使用情况访问。
- 无障碍只使用包名和窗口元数据，不保存或上传屏幕文本；实体键快捷控制可选。
- Shizuku 需先启动并授权。Dhizuku 的设备管理权限不等于 shell/root，不作为全局任务检测来源；对已配置 Dhizuku 的设备提供可选的本应用通知权限管理。
- “一键优化自动切换”先显示 EasyOverlay 的通知授权、使用情况访问与系统电池白名单改动，确认后执行，保留原设置恢复入口。
- 自动会话被系统回收后可尝试恢复，尊重用户停止，连续失败会退避。系统后台启动限制和厂商清理策略仍可能阻止恢复。手动遮罩不在进程重启后自动重现。

同屏快速使用优先于自动规则。停止快速使用后匹配当前前台应用。Usage Access 在多窗口下准确性较低，推荐无障碍或 Shizuku。自动规则目前在主屏执行，副屏提供独立快速使用与规则编辑。

## 设置与数据

- 支持简体中文 / English / 跟随系统，语言由 Android 持久保存。
- 应用图标主体缩至原尺寸 70%，动态主题图标采用相同安全区尺寸。
- 设置最下方“关于”展示软件简介、作者主页、仓库链接与开源许可。
- 图片保存在应用私有目录，无需首启选择文件夹。单图最多 32 MB，静态图片最多 6400 万像素，GIF 最多 1600 万像素。
- 图片库支持复用和删除未引用图片。EasyOverlay ZIP 备份包含版本化 JSON 和图片，导入时校验路径、大小、格式及 SHA-256；合并保留现有规则，冲突进入图片库。兼容 EasyOverlay 6.0 自有数据和备份。
- 已移除 ImageOverlay 导入功能；不再维护上游数据兼容。
- 可添加快捷设置磁贴、默认关闭的悬浮球、实体键组合；诊断支持复制和导出脱敏日志。应用不自动联网。

## 平台与验证

compileSdk 36、targetSdk 35、minSdk 33；target 36 仍等待实机前台服务复核。持续会话使用可见的 specialUse 前台服务，不使用 dataSync 保活。

安全页面、锁屏、权限弹窗和部分外接屏可能隐藏遮罩。新功能、真实 Shizuku/Dhizuku 及 MIUI 等厂商长期后台表现的验证范围见 [6.1 验证记录](docs/VALIDATION_6_1.md)。

## 构建

使用 JDK 17、Android SDK 36、Build Tools 36.0.0 和仓库 Gradle Wrapper，详见 [环境配置](docs/CLOUD_ENVIRONMENT.md)。

```sh
bash scripts/setup-android-env.sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

只交付一个 `EasyOverlay-v6.1.apk`，包含 arm64-v8a、armeabi-v7a、x86、x86_64，不分架构。

```sh
export EASYOVERLAY_SIGNING_PROPERTIES=/private/path/signing.properties
bash scripts/build-release.sh
```

签名规范见 [发行说明](docs/RELEASING.md)。私钥、密码、APK、SDK 缓存与本地配置不提交 Git。原版回退标签为 `baseline-5.2`，已发布 6.0 标签及正式版保持不变。
