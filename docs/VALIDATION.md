# EasyOverlay 6.0 验证记录

记录日期：2026-10-25。自动化、模拟器和真实设备证据分开记录。

## 云端与独立 CI

- 已实际构建原版 5.2 Debug APK，基线 lint 有 14 个错误、122 个警告；见 BUILD_BASELINE_REPORT.md。
- 新版 JVM / Robolectric 共 **32 项测试通过，0 失败、0 跳过**。
- 全新 Debug 构建、单元测试和 lint 已实际通过；清理旧资源后的 lint 为 **0 错误、13 警告**。
- 独立 GitHub Actions 成功运行：
  https://github.com/Hycnyl/EasyOverlay/actions/runs/40217159969 ，源码 `8ce6ec0`。
- 覆盖：系统 opacity 上限、快速 A/B 切换、迟到 pause、多 Activity、手动优先、跨屏隔离、
  关闭手动后按最新前台恢复、权限撤销、过时图片丢弃、坏图保留旧会话、服务初始化竞态、
  原子保存、并发保存、损坏索引保护、共享资产引用、迁移默认项/冲突/幂等键、PNG alpha、
  JPEG / 静态 WebP / GIF 字节保留、损坏图片、动态 WebP、尺寸限制、ZIP 路径和备份版本检查。

## 已实际构建的签名候选包

- applicationId：`com.hycnyl.easyoverlay`（从 APK badging 读取）。
- versionName / versionCode：`6.0` / `600`；min / target / compile：33 / 35 / 36。
- RSA 3072 发布签名通过 apksigner 验证，证书摘要见 RELEASING.md。
- 单一 APK；两种 AndroidX 原生库各包含 arm64-v8a、armeabi-v7a、x86、x86_64。
- APK 16 KB zipalign 通过；arm64 库的 ELF LOAD 对齐为 0x4000。
- R8 / 资源缩减后约 2.6 MB；最终文件哈希由 Release 随附的 SHA-256 文件提供。
- 私钥、密码和签名备份均保存在仓库之外，不上传 GitHub。

## Android 15 / API 35 软件模拟器

环境：AOSP x86_64，软件 CPU / SwiftShader，无 KVM。首次系统启动很慢，出现过系统
System UI ANR 与 Bluetooth 系统进程崩溃；这些不能当作真实设备兼容性结论。

已执行：

- 安装实际经过 R8 和发布签名的 universal APK：成功。
- 启动三 Tab 主界面：成功，无首启 SAF 文件夹或 Usage Access 阻挡。
- 通过系统 ACTION_OPEN_DOCUMENT 选择测试 PNG：成功；360×640 元数据和透明预览正确。
- Usage Access 保持 default / 未授权，手动启动会话：成功。
- dumpsys 确认服务前台类型 `0x40000000`（specialUse），通知 ID 601，含停止动作。
- dumpsys 确认普通 TYPE_APPLICATION_OVERLAY 窗口，`alpha=0.6`，
  `NOT_FOCUSABLE | NOT_TOUCHABLE`。

这次软件模拟器随后报告 `invalid color buffer` 和 QEMU CPU 线程挂起，以 134
退出。这不是应用 Java 崩溃记录。关闭 Vulkan、使用 OpenGL 软件后端重试后：

- 同签名更新成功，上次选择的私有 PNG 保留，重启后未误报旧窗口仍在运行。
- 实际窗口达到 `HAS_DRAWN` / `isVisible=true` / `mShownAlpha=0.6`。
- 切换至时钟应用，手动遮罩继续显示；在遮罩下点击，成功从 Clock 切换到 Stopwatch。
- 返回 EasyOverlay，点击停止后，窗口移除、服务退出、UI 显示已关闭，最近图片保留。
- 系统设置首页主动隐藏普通悬浮窗，WMS 记录 `mForceHideNonSystemOverlayWindow=true`；
  离开该系统页面后遮罩恢复。这是平台安全限制，不尝试绕过。

这些结果仅证明该标准模拟器配置，不能替代真实 Android 设备兼容性。

运行截图见 [screenshots](screenshots/)。

界面检查发现并修复：浅色主题系统栏图标对比度；小屏启动按钮需多次滚动。
快速使用的显示/停止按钮现固定在底部，预览与设置保持可滚动。

## 未验证的真实设备矩阵

| 项目 | Android 13 | Android 14 | Android 15 | Android 16 |
|---|---|---|---|---|
| 实机安装、与原版并存、同签名更新 | 未验证 | 未验证 | 未验证 | 未验证 |
| 跨应用真实触摸穿透、不同 opacity | 未验证 | 未验证 | 未验证 | 未验证 |
| 长期 FGS、杀后台、通知拒绝、厂商电池策略 | 未验证 | 未验证 | 未验证 | 未验证 |
| 旋转、刘海、外接屏、双屏 | 未验证 | 未验证 | 未验证 | 未验证 |
| 实体按键、磁贴、悬浮球 | 未验证 | 未验证 | 未验证 | 未验证 |
| 用户原版 SAF 目录真实迁移 | 未验证 | 未验证 | 未验证 | 未验证 |

targetSdk 36 的真机门槛尚未满足，因此本次 target 35；未以低 target 保留 dataSync。
V1 副屏仅手动工作，不猜测副屏前台包名。系统安全页、权限弹窗和锁屏可能无法覆盖。
本次以预发布形式交付，不宣称已经完成全设备矩阵或 24/7 后台保活验证。
