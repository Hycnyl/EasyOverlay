# EasyOverlay 6.0（预发布）

基于 ImageOverlay 5.2 的重大版本，采用独立包名 `com.hycnyl.easyoverlay`。

- Material You / Material 3 界面，快速使用、应用遮罩、设置三项一级导航，支持动态配色和深浅色。
- 快速使用无需绑定应用或 Usage Access；每屏手动优先，显示/停止按钮固定在底部。
- PNG / JPG / JPEG / 静态 WebP / GIF，默认私有存储，验证真实格式和尺寸后复制。
- 每应用每屏独立规则；单路前台观测与串行协调，避免旧退出事件误关新遮罩。
- 安全换图、真实窗口状态与系统 alpha 上限（通常 80%），不使用无障碍遮罩绕过限制。
- 旧 ImageOverlay SAF 目录只读扫描预览、冲突待分配；备份导出与 SHA-256 校验导入。
- 可选双屏手动使用、实体键组合、悬浮球、快捷设置磁贴、脱敏诊断。
- 用户主动启动的 specialUse 前台会话，通知提供停止全部入口。

## 下载与安装

下载唯一的 `EasyOverlay-v6.0.apk`；这是包含 arm64-v8a、armeabi-v7a、x86、x86_64
的 universal APK，不需要挑选架构。另附 SHA-256 文件。

支持 Android 13+；minSdk 33、compileSdk 36、targetSdk 35。发布包使用独立 RSA 3072
密钥签名，证书 SHA-256：
`8ba5228b6199097895c2d5114193673580dfd8a8bf847e355f842c2f65c387ea`。

可与原版 ImageOverlay 并存，不能直接覆盖安装原版。旧数据需用户重新授权目录导入；
卸载前请导出备份。后续 EasyOverlay 更新需使用同一发布密钥。

## 已验证与未验证

云端 Debug 构建、32 项 JVM / Robolectric 测试和 lint 通过（0 错误）。独立 GitHub
Actions 已通过。签名、APK 身份、四种 ABI、许可证、16 KB APK 对齐已核验。

Android 15 软件模拟器完成发布包安装、首次启动、系统选图、PNG 透明预览、未授予
Usage Access 的手动会话启动；系统记录确认 specialUse 前台类型和 alpha 0.6 的普通
非触摸悬浮窗。首次软件后端出现图形缓冲 / QEMU 异常，关闭 Vulkan 后重试，
实际窗口可见、跨应用持续显示、遮罩下点击切换时钟页面、停止释放窗口与服务、
同签名更新保留图片均通过。
系统设置页按平台策略隐藏悬浮窗。详细记录见仓库 `docs/VALIDATION.md`。

**真实 Android 13/14/15/16 设备均未完成验收**：触摸穿透、长期后台、厂商电池策略、
真实双屏、实体按键与原版数据迁移仍需设备测试。因此本次标记为预发布。

targetSdk 36 按施工文档等待真实设备上的 FGS 复核；target 35 已使用 specialUse，
没有沿用 dataSync 保活。系统安全页、锁屏、权限弹窗及部分外接屏可能无法覆盖。
UsageStats 不能可靠识别副屏前台应用，V1 仅主屏自动切换，副屏保留独立手动能力。
系统终止会话后需用户主动重新开启，不保证所有设备持续后台运行。

## 权限和许可

悬浮窗：显示图片；使用情况访问：仅自动切换；通知：会话状态与停止；无障碍：仅可选
实体键。没有账号、广告或自动网络日志上报。诊断导出不含文件 URI 和用户目录。

EasyOverlay 是 PerryKum/ImageOverlay 的非官方 Fork，继承 GPL-3.0。
对应标签源码、GPL 许可证与上游致谢随仓库持续提供。私钥和密码不作为公开资产上传。
