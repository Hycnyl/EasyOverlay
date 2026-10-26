> 历史规格：此文保留 6.0 的施工基线。6.1 对监测权限、可信遮罩、后台恢复、双语及上游导入的变更，以 [CHANGES_6_1.md](CHANGES_6_1.md) 为准。

# EasyOverlay V1 — Codex Cloud 统一施工路线图

> 版本：2026-10-25 / V1.3（品牌定稿版） · 状态：施工规格，尚未实施代码修改或实机验证  
> 上游来源：<https://github.com/PerryKum/ImageOverlay>（上游项目名称为 ImageOverlay）  
> 已审查源码参考提交：`a74ef6f2a2ceedf250f79e265ed2c9433fe929b1`；实际开工应记录 Fork 当前 `HEAD`，以当前源码为准  
> 开发方式：**Codex Cloud**（云端环境自行准备和复用），非本地 Codex/Android Studio 依赖  
> **正式产品名称：EasyOverlay · 最终 Android `applicationId`：`com.hycnyl.easyoverlay`**  
> 发布方式：GitHub Releases / 可下载 APK；
> **此文件是唯一的技术与产品施工规格。** 若有旧的 Phase 0 专用提示词、旧版路线图若与本文件冲突，以本文件为准。本文可在代码仓库中保存为 `docs/FORK_V1_CONSTRUCTION.md`。

---

## 0. 产品定位、施工原则与工作范围

**定位**：**EasyOverlay** 是面向 Android 13–16 的轻量级图片遮罩工具，让用户既能立即选择图片开启触摸穿透遮罩，也能给某个应用保存专属图片、自动随应用切换。相较上游 ImageOverlay，降低学习成本、提升状态可解释性，并采用 Material You（Material 3）。

**三条不可牺牲的用户体验**：

1. **快速使用必须保留，并且是一级入口**：不绑定应用、不建配置、不要求使用情况访问权限；用户选择图片、调整透明度、点击启动，就能用。
2. **应用遮罩直接按应用设置**：选择应用 → 导入图片 → 设置透明度 → 选择是否自动启用。用户看不到旧版“组 / 组内配置 / 默认配置 / 激活圆点”工作流。
3. **错误要说人话**：权限未授予、图片损坏、Android 触摸穿透限制、后台监听被限制、配置迁移失败都要有可理解的原因及下一步动作。

**技术约束**：不用规避 Android 安全规则来换取表面成功率；不承诺所有厂商设备持续后台存活；做不成的真机验证必须标为“未验证”。保留上游适用许可证、版权信息和致谢。

### 0.1 冻结的 V1 产品决策

| ID | 决策 | 验收要点 |
|---|---|---|
| D01 | 取消普通用户界面的配置组 | 每应用每目标屏幕一套直观规则；旧组只出现在迁移器内部 |
| D02 | PNG、JPG/JPEG、静态 WebP、GIF | PNG 透明像素与 GIF 动画仍可用；**不做 SVG / 动态 WebP** |
| D03 | 默认 App 私有存储 | 首启不强制 SAF 文件夹；导入/导出时调用系统选择器 |
| D04 | 触摸穿透优先的透明度控制 | 默认不透明度 60%；普通悬浮窗遵循系统最大遮挡 Alpha |
| D05 | 双屏与实体键保留 | 保持基本能力，入口移到“高级功能”；实机验证各自边界 |
| D06 | 悬浮球保留但默认关闭 | 不全面重写；不得决定遮罩的所有权或与自动规则互相干扰 |
| D07 | 优先改好 UsageStats | 不把无障碍作为应用切换的强制条件；可选增强留待后续 |
| D08 | 提供诊断 | 清晰权限检查、当前模式、目标应用、上次切换及失败原因，支持脱敏日志导出 |
| D09 | GitHub/APK 分发 | 源码、构建说明、签名发行包、哈希、Release Notes、LICENSE/致谢 |
| D10 | **保留“快速使用”** | 与独立应用规则平级，首次启动默认可见，满足偶尔使用的用户 |
| D11 | **正式名称及包名已冻结** | 名称 **EasyOverlay**；正式 `applicationId` **`com.hycnyl.easyoverlay`**；最终 `namespace` 和 Kotlin 包路径也整理为该前缀，保留上游溯源 |
| D12 | 全产品品牌一致性 | Launcher、应用内、通知、磁贴、图标、README、GitHub Release、诊断与导出文件使用 EasyOverlay；旧数据导入识别原 ImageOverlay |

### 0.2 已确认的正式品牌与技术标识（不可由 Codex 擅自改名）

| 标识 | 最终值 / 实施要求 |
|---|---|
| **面向用户的应用名** | **EasyOverlay**（大小写固定，不使用 `Easy Overlay`、`EOverlay`、`ImageOverlay Modern` 等其它正式名称） |
| **正式 Android 应用 ID (`applicationId`)** | **`com.hycnyl.easyoverlay`**；Release APK 必须使用该值 |
| **最终 Gradle `namespace`** | **`com.hycnyl.easyoverlay`**（命名迁移阶段完成，不必在 E0/P0 基线测试前先修改） |
| **最终 Kotlin/Java 源码包** | 逐步整理为 `com.hycnyl.easyoverlay` 及其子包；不要用粗暴字符串替换破坏组件引用 |
| **Manifest 应用标签** | `android:label="@string/app_name"`，`app_name = EasyOverlay`；其它语言也保持专有名词不翻译 |
| **英文/中文宣传定位** | 英文可用 `EasyOverlay — Screen Image Overlay`；中文可用“EasyOverlay · 屏幕图片遮罩”。副标题不是应用名 |
| **建议仓库显示名称** | `EasyOverlay`（若 Fork 已存在其他仓库名，不强制改 GitHub 仓库 URL；调整 README 即可） |
| **建议发布资产文件名** | `EasyOverlay-v<versionName>.apk` 和 `EasyOverlay-v<versionName>.sha256`；版本号按实际发行确定 |
| **历史项目称呼** | 仅在 LICENSE/NOTICE、README 致谢、迁移界面说明、历史源码文档中保留 `ImageOverlay`；不能让用户误认为 EasyOverlay 为上游官方原版 |

**品牌更名的分阶段安全原则**：E0/P0 保持上游源码原状先建立基线；紧接着用单独可回滚的 **B1「EasyOverlay 更名」阶段**进行应用 ID/展示标识更改，成功构建后再重构 `namespace` 与 Kotlin 包结构。最终以 `applicationId` 为安装身份，以 `namespace` 为生成 `R` 等代码的命名空间；两者意义不同，**不能只改 Manifest `package` 并声称更名完成**。

### 0.3 迁移后的用户体验与身份边界

- EasyOverlay 是**独立应用**，不是 ImageOverlay 的原包覆盖升级。Android 上两者可以同时安装，原版用户配置不因安装 Fork 自动转移。
- 旧数据迁移从用户通过 SAF 授权的 **ImageOverlay** 文件夹或备份包读取。不要把旧备份中的原包名、广播动作名或媒体 URI 直接当作新 App 有权访问的资源。
- Quick Use（界面名称仍为“快速使用”）是 EasyOverlay 的第一入口，其功能和优先级不因品牌更名改变。
- 安装调试版时如选择临时 `applicationIdSuffix`，须标注它**不等于正式包名**；Release 构建不可带后缀。

### 0.4 V1 不做的事情

- 不提供 SVG、动态 WebP、云同步、登录账号、在线图片商店、广告和订阅。
- 不用无障碍服务来强行绕开普通悬浮窗的透明度限制。
- 不在 V1 实现复杂多组模板树；旧组仅为了数据迁移读取。
- 不在 Codex Cloud 中声称模拟器或单元测试能证明真实设备上跨应用的触摸穿透、后台持续运行和 ROM 兼容性。
- 不能保证锁屏、系统安全页面、权限弹窗或每个外接屏都被系统允许覆盖。

---

## 1. 开工前：Codex Cloud 环境应如何自行准备

### 1.1 Codex Cloud 正确工作方式

- 授予完整权限下，首次工作时，Codex应自行配置开发环境并在遇到一般性问题时尝试解决。

### 1.2 正式编码任务与云环境的边界

- **环境准备**：安装依赖和测试现状，审阅后发布环境；不需要在此时改业务架构。
- **开发任务**：在已发布环境中按第 8 节逐阶段施工，任务内每次交付均给出真实构建、测试、差异和剩余风险。
- **更新环境**：依赖或 SDK 升级时，修改并重新发布云环境；后续新任务使用新配置。**云环境缓存不是 Git 源码的替代品**。
- **CI**：GitHub Actions 应独立执行调试构建/单元测试，不能依赖 Codex Cloud 私有缓存；仓库必须具备复现能力。

---

## 2. 上游源码核对与改造范围（原包路径只用于定位上游）

以下为已审阅的上游设计。这里出现的 `com/example/imageoverlay`、`com.example.imageoverlay` 均是**上游既有源码/包路径**，不是 EasyOverlay 的目标安装身份。Codex 开工前需要按实际 `HEAD` 复核，不得直接以本文取代源码事实。

| 源码路径（`app/src/main` 下） | 已观察到的现状 | 施工方向 |
|---|---|---|
| `java/com/example/imageoverlay/OverlayService.kt` | 利用 WindowManager 显示、更新、移除每屏遮罩；先移除旧图后加载新图；维护内存运行集合 | 安全替换、不阻塞主线程、统一运行状态、正确资源释放 |
| `java/com/example/imageoverlay/util/DisplayUtil.kt` | `FLAG_NOT_TOUCHABLE` / `FLAG_NOT_FOCUSABLE`，按系统最大遮挡 Alpha 处理普通窗口 | 修正超过阈值时“滑块显示比实际更大”的问题；检验叠加窗口 |
| `java/com/example/imageoverlay/UsageStatsListener.kt` | 500ms UsageEvents 轮询、3s 冷却、进入与退出分别处理 | 前台观测、单线程状态调和，去重，启动和恢复时重新校准 |
| `java/com/example/imageoverlay/AppLaunchListenerService.kt` | 独立长驻前台服务，被声明为 `dataSync` | 用户主动启用的监听会话，验证正确 FGS 类型和 Android 15/16 限制 |
| `java/com/example/imageoverlay/model/ConfigRepository.kt` | 组、默认项、激活态、共享设置、文件读写、服务启停混在一处 | 拆分存储、规则、会话与协调器；保留只读旧数据迁移 |
| `java/com/example/imageoverlay/model/ConfigModels.kt` | `Group` + `Config`，组只绑定单个包名，默认配置有多套冗余标记 | 统一 `AppRule` / `OverlayAsset` / `OverlaySession` |
| `java/com/example/imageoverlay/QuickUseFragment.kt` | 原版“快速使用”是独立手动模式；选 PNG/GIF，状态保存到 `quick_use_prefs` | **必须保留且提升为一级入口**，统一选图/状态管理/格式 |
| `java/com/example/imageoverlay/GroupDetailFragment.kt` | 组配置文件扩展名主要写死 `.png` / `.gif`；某路径会清空组目录 | 统一导入与文件清理；禁止通过旧目录逻辑删除其它图片 |
| `java/com/example/imageoverlay/MainActivity.kt` | 首次强制选 SAF 根目录、启动时尝试监听 | 内部存储开箱即用、按需权限引导、显式开启自动监听 |
| `java/com/example/imageoverlay/FloatingBallService.kt` | 控件、弹窗、手势、前台检测及状态混合 | 暂保留基础能力、默认关闭、解除对核心会话所有权的耦合 |
| `java/com/example/imageoverlay/keybinding/KeyBindingService.kt` | 通过无障碍服务接收实体按键 | 高级可选功能；最小化权限说明，不强制依赖 |
| `res/values/styles.xml` / `colors.xml` | Material Components 主题、硬编码浅色 | Compose Material 3 动态配色、明暗主题、系统 Insets |
| `AndroidManifest.xml` | 三个 `foregroundServiceType="dataSync"`，UsageStats、Overlay、广泛包可见性等权限 | 审查 API 34–36 服务限制与权限使用范围 |
| `app/build.gradle` / 根 `build.gradle` | 上游 Kotlin 1.9.0、AGP 8.1.2、SDK 34/34/min 23，Gradle Wrapper 8.12 | 分步更新；最终建议 API 33–36，匹配 Compose 和正式发布工具链 |

**关键安全风险**：`GroupDetailFragment` 某换图分支清空组目录；将该路径废弃之前必须有可回滚的数据保护。`ConfigRepository.saveInternal` 存在静默异常分支；新的持久化必须报告错误并确保记录与文件一致。

---

## 3. 两种平级使用方式：必须明确保留“快速使用”

### 3.1 建议的导航结构

**一级导航**（底部 3 项）：

1. **快速使用**（推荐首次打开默认页）：立即选择图片和开/关遮罩，主副屏按需展示；突出“当前是否显示”。
2. **应用遮罩**：应用列表、搜索、添加、每个应用的图片、独立透明度和自动切换开关。
3. **设置**：自动监测总开关、权限说明、主题、诊断、存储、导入导出；“高级功能”子页进入双屏、实体键与悬浮球。

顶部或快速使用页显眼但不过度打扰的运行信息：`自动监听：开/关`、`当前显示：快速使用/某应用/已关闭`、`打开设置或诊断`。

### 3.2 快速使用操作体验（必须完整可用）

- **未授权 UsageStats 时照样能用**，首次只在启动遮罩时请求/引导悬浮窗权限；通知权限在需要时按平台行为说明。
- “选择图片”走统一图片导入器，支持 PNG/JPG/JPEG/静态 WebP/GIF，显示缩略图。默认不透明度 60%；允许预览和调整；支持“更换图片”、“显示遮罩”、“停止遮罩”。
- 上次选择的快速使用图片在 App 私有空间持久化，下次打开无需重复选择；也允许清除最近图片。**不需要创建或命名应用规则**。
- 停止/更换时有准确反馈：实际窗口挂载成功后才显示“已开启”，失败时留在可重试状态。
- 用户离开 App、切换游戏时，快速使用遮罩继续显示，直到用户主动停止、收到明确关闭操作或系统终止会话；前台通知提供清晰关闭入口。
- 单屏默认简洁；检测到多屏时提供主屏/副屏面板或选择，不让单屏用户看到无意义选项。
- 支持直接退出/返回，不被首次启动强制选路径、绑定应用、UsageStats 等步骤阻挡。

### 3.3 快速使用与自动切换的优先级（必须实现成规则，不能靠偶然分支）

**同一个 display 上**：用户手动启动的快速使用拥有**最高优先级**，自动应用规则暂停覆盖该屏；用户关闭快速使用后，读取最新前台观测、重新调和自动规则。不同 display 的会话应互不影响。

| 场景 | 应有行为 |
|---|---|
| 未开启自动监测，手动快速使用 | 正常运行；不需要 UsageStats |
| 自动监测开启，当前应用 A 正显示 A 专属遮罩 | 用户启动快速使用后，A 遮罩让位于手动图；提示“已暂时覆盖自动规则” |
| 快速使用期间从 A 切换到 B | 当前手动图保持，后台可记住新的前台目标，但不得抢回屏幕 |
| 用户停止快速使用，B 已在前台且有规则 | 如权限与监测正常，恢复 B 的自动遮罩 |
| 用户停止快速使用，前台无规则或监测不可用 | 关闭该屏遮罩，不恢复历史 A 遮罩 |
| 主屏手动快速使用，副屏自动规则有效 | 主屏手动优先、副屏根据自身可观测能力工作，互不错误关闭 |
| 规则引擎需要停止会话 | 只能停止**自己拥有**的 `APP_RULE` 会话，不可误关 `QUICK_USE` 会话 |

**模式声明**：内部持有来源 `QUICK_USE` / `APP_RULE` / `NONE`，屏幕与运行状态统一管理，杜绝用多个偏好布尔值推断“谁正在显示”。手动优先级必须在 UI 文案和诊断页可见。

### 3.4 最小可行的快速使用验收

- 新安装，拒绝 UsageStats → 仍能选图、请求悬浮权限、显示/停止遮罩。
- 在图下点击游戏按钮，Android 实机触摸正常传递；不同透明度的实际效果与 UI 数字一致。
- 快速使用期间频繁切换有规则应用，手动图不会被替换；停止后按当前前台自动恢复（如果自动监测已开启且可用）。
- 应用进程重启后，能找到最近选用的图片；不会误认为已挂载的窗口仍存在。

---

## 4. 数据与图片架构：从配置组变成以应用为中心

### 4.1 推荐逻辑层划分

```text
UI (Compose Material 3)
  ├── QuickUseScreen (manual)
  ├── AppRulesScreen / RuleEditor
  ├── Settings / Diagnostics / Advanced
  └── ViewModels (StateFlow)

Domain
  ├── ImageImportUseCase
  ├── ForegroundAppObserver (UsageStats)
  ├── RuleResolver
  ├── OverlayCoordinator (one serialized reconciliation pipeline)
  └── SessionStateStore (actual mounted windows, per display)

Data
  ├── AppRuleRepository (Room or equally safe transactional persistence)
  ├── OverlayAssetRepository (app internal filesDir)
  ├── SettingsRepository (DataStore)
  └── LegacyConfigImporter (read-only SAF input, versioned migration)

Platform
  ├── OverlayService / WindowManager overlay views
  ├── Foreground monitoring session service / notification
  ├── Android permission and package visibility adapters
  ├── DisplayManager and device capability checks
  └── Optional advanced hardware-key / floating-ball adapters
```

可依赖重构现有类，但避免一次性把所有业务逻辑放进大单例或 UI Fragment。

### 4.2 逻辑数据模型（字段可由 Codex 细化，语义不得丢失）

```kotlin
// 表示意；非要求一字不差的生产接口。
data class OverlayAsset(
    val id: String,              // UUID
    val relativePath: String,    // 私有目录相对路径，非不受控的绝对路径
    val mimeType: String,
    val isAnimatedGif: Boolean,
    val width: Int,
    val height: Int,
    val createdAt: Long
)

data class AppRule(
    val packageName: String,
    val displayId: Int,          // 一屏一条主规则
    val assetId: String,
    val opacityPercent: Int,
    val autoEnabled: Boolean
)

enum class OverlayOwner { QUICK_USE, APP_RULE }

data class ActiveOverlaySession(
    val displayId: Int,
    val owner: OverlayOwner,
    val assetId: String,
    val opacityPercent: Int,
    val sourcePackageName: String? = null
)
```

- 数据库存储**用户选择的配置**，不是“系统窗口目前存在”的真相。
- `ActiveOverlaySession` 反映服务实际创建的窗口；首次启动必须重新核对系统与本进程服务状态。
- 主键避免依赖应用展示名；卸载重装应用后规则由用户决定保留或移除。
- 使用稳定 asset UUID；引用计数/关系约束防止多规则共用图片时误删。
- 修改图像先验证并复制完成，再事务性更新引用；原图安全删除延迟到引用为 0 后执行。

### 4.3 旧配置导入原则

独立 `applicationId` 的新 Fork **不能无授权读取上游 App 私有数据**。上游依赖用户选过的 SAF 目录；可允许用户通过系统目录选择器重新授权旧 `ImageOverlay` 根目录。读取旧 `config.json`、逐条定位图片，迁移为新的 AppRule / Asset。可能存在历史 `file://`、绝对路径、树形 document URI、无效 URI，必须逐条判断并报告，禁止武断把路径字符串交给 `Uri.parse()` 后假定可读取。

- 导入先扫描预览：列出发现组数/图片数/可关联包名及不确定条目。
- 原组同应用同屏多条：优先旧默认配置；存在冲突时让用户选择或导入为待解决项，不静默覆盖。
- 未绑定应用的组：导入到“未关联图片资产”或待分配列表，不能随意附加到某应用。
- 把图片**复制**进私有目录；源目录不删除、不重命名、不写入。
- 原子提交规则；任何一步失败，报告成功/失败条目，可重试且幂等。
- 可导出备份 JSON 与图片到用户指定目录，带版本号；新包可回滚；迁移器可重复运行时不生成无限重复项。

### 4.4 图片导入与内存管理

- 用系统 Photo Picker 或 `ACTION_OPEN_DOCUMENT`，按是否需要持久授权/多格式决定；统一检验实际字节 MIME、尺寸、文件大小和可解码性，不仅相信扩展名。
- PNG：保留每像素透明度。JPG/JPEG：整体半透明，但 JPEG 本身无透明通道。静态 WebP：保留其可用的 Alpha。GIF：保持动图功能，生命周期释放解码/绘制资源。
- 显示解码不要无限制使用原始超大图；采用适当下采样/解码缓存；图层显示可用 `FIT_XY` 兼容原遮罩，但 UI 给出图像可能拉伸的说明，可在后续添加 fit 模式。
- 导入限制、错误信息与安全处理一并实现：损坏文件、异常巨大图、重复导入、已删除文件、ContentResolver 读取中断。
- V1 不做 SVG 与动态 WebP，过滤或提示不支持，不借助任意第三方“万能解码”来引入不必要依赖。

---

## 5. 遮罩引擎、Android 13–16 兼容与权限

### 5.1 窗口与触摸

现有上游使用 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 与 `FLAG_NOT_TOUCHABLE` 等实现透传。Android 12+ 对“不可信”悬浮窗的触摸遮挡有安全限制：默认最大 `0.8`，准确上限从 `InputManager.maximumObscuringOpacityForTouch` 获取。同 UID 的多层普通悬浮窗重叠需要计算累计不透明度，不能只验证一层。

改进目标：

- 将界面滑块与真实窗口级 alpha 绑定，普通模式最大设置值为当前系统可允许范围（通常 80%），超出时禁用而非显示 100% 但实际 80%。默认 60%。
- 需要显示像素级 PNG/GIF alpha 时，仍保持 WindowManager 的安全阈值；不能把 `View.alpha` 当作绕过遮挡判断的方式。
- 如果有多个本 App 普通悬浮窗口，检查叠加遮挡与触摸交互。悬浮球有自己的可触摸窗口，必须避免误挡游戏关键交互区域。
- 旋转、刘海、沉浸式游戏、手势导航、多窗口和分辨率改变后按实际 display bounds / Insets 重新布局。
- 新图准备成功后再替换旧图；出错时尽量保留旧图并报告，避免先删旧窗造成空屏；解码、复制不得阻塞主线程。
- 对设备/OEM 无法覆盖的系统页面给出限制说明，不做系统层注入、Root/Xposed 假设。

官方：<https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE>

### 5.2 前台服务与 Android 15/16 限制

上游的 Overlay、监听及悬浮球服务都被声明为 `dataSync`。对 target API 35+，`dataSync` 前台服务在 24 小时内有累计 6 小时的限制；Android 15 后台启动前台服务的悬浮窗豁免也有额外条件。因此**先验证长期运行的用户可见会话方案，再重写自动切换**。

- 以用户主动开启的“自动切换会话”为主，通知必须可见且允许停止。
- 对持续悬浮显示和持续监测分别确定合法的组件生命周期；研究适用的 `specialUse` FGS 类别，并配置正确权限与 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 描述（若采用）；不要把 `specialUse` 当作无条件保活手段。
- 明确检查 `ForegroundServiceStartNotAllowedException`、系统重启、杀后台、省电策略、Android 13+ 通知权限、用户停止服务、更新后的重启行为；只能在系统允许的情形下恢复。
- 手动“快速使用”应比长期 UsageStats 监测具有更少依赖；仅启用自动切换才要求 Usage Access。
- 长期运行通知的 ID、渠道与状态要避免上游服务重叠/相互覆盖。
- 先有 PoC 与真机复核再升 `targetSdk=36`；不以低 targetSdk 规避限制。

官方：<https://developer.android.com/develop/background-work/services/fgs/changes>、<https://developer.android.com/develop/background-work/services/fgs/service-types>

### 5.3 权限请求与拒绝降级

| 权限/能力 | 何时引导 | 拒绝时应怎样 |
|---|---|---|
| 悬浮窗 `SYSTEM_ALERT_WINDOW` | 用户首次尝试显示遮罩时 | 允许选择和预览图，清楚提示无法实际覆盖其它应用 |
| 使用情况访问 `PACKAGE_USAGE_STATS`（特殊访问） | 用户主动开启“自动切换”时 | **快速使用照常可用**；应用规则可编辑，自动模式提示未启用 |
| 通知 `POST_NOTIFICATIONS` | 需要前台会话通知时按平台及体验处理 | 明确是否影响通知可见性与系统服务行为，不误判为悬浮权限 |
| Accessibility | 仅高级实体键或未来用户同意的可选检测 | 主体使用流程不能受影响 |
| 应用列表可见性 | 用户打开应用选择页时 | 优先最小化包可见性；GitHub APK 可按实际功能保留有依据的权限，但需隐私说明 |

---

## 6. 自动应用识别与切换状态机（UsageStats 第一优先）

### 6.1 原实现主要问题

`UsageStatsListener` 使用 `queryEvents` 每 500ms 获取前后台事件，存在固定 3s 处理冷却和单次处理门闩；`ConfigRepository` 又单独处理启动、桌面和应用退后台，造成 A→B 快速切换时漏掉 B 或 A 退后台误关闭 B 的风险。单纯缩短某个冷却常量不足以解决模型问题。

### 6.2 新实现要求

1. `ForegroundAppObserver` 是唯一的前台观测入口。查询时间窗允许小幅重叠，按事件时间与唯一特征去重，避免边界丢失；处理 `ACTIVITY_RESUMED`/`PAUSED`（及兼容事件）、多 Activity、系统桌面、暂时失去焦点等特殊情况。
2. `RuleResolver` 依据**最新可信前台目标**查询 `(packageName, displayId)` 对应独立应用规则；未配置或规则关闭时目标是关闭自动图层。
3. `OverlayCoordinator` 使用串行队列/单线程 reducer：`observe foreground → compute desired state → compare current session → apply change`。旧过时请求不能覆盖新请求。短暂事件抖动可做有限去抖，但绝不能用“全局 3 秒跳过合法切换”。
4. 应用进入与退出事件只更新观测状态，不得在 `MOVE_TO_BACKGROUND` 回调中无条件关闭当前已显示的其它应用图层。
5. 快速使用 `QUICK_USE` 手动所有权优先于同一屏 `APP_RULE` 自动目标；停止后基于最新前台观测恢复自动规则。
6. 监听服务被系统重建时恢复规则缓存并重新确认前台，避免仅靠内存栈的历史记录；没有可靠数据时不擅自显示历史游戏遮罩。
7. 监测器启动与停止由用户总开关和服务状态控制，不在每次进入首页时未经同意启动长驻服务；监听不可用时诊断应显示原因。
8. 每次决策记录结构化状态码（包名必要时脱敏）：检测到应用、匹配规则、准备图片、切换成功/失败、过时事件丢弃、缺少权限。

**多屏现实边界**：UsageStats 事件通常无法稳定、跨 ROM 地提供“每块物理屏幕分别是哪一个最前台应用”这一完整事实。V1 必须以已验证的观测能力定义副屏自动切换的能力范围；如无法可靠确定副屏对应包名，副屏应保留手动模式和规则编辑，并提示自动切换在该设备有限制。**不得让主屏的猜测覆盖副屏当前的手动遮罩**。

### 6.3 关键状态迁移回归集

- A（有规则）→ B（有规则），1 秒内切换、反复切换，最终始终显示最新 B/A。
- A → C（无规则），自动遮罩应按策略关闭；快速使用手动遮罩不受影响。
- A → 桌面 → B；通知栏、锁屏、系统弹窗出现时状态可解释且无闪烁死循环。
- 应用自身进入前台进行配置时不产生错误的旧规则回放。
- 手动快速使用 → A→B→C → 停止手动：依据当前 C，而不是恢复过时的 A。
- 禁用自动监测后不再主动显示应用规则；用户手动遮罩可继续运行。
- UsageStats 权限被系统撤销、服务被终止或设备进入省电模式时，展示“暂停/受限/失败”，而不是显示“已正常自动切换”。

---

## 7. Material You UI、可访问性和诊断

### 7.1 UI 实施

- Jetpack Compose + Material 3（动态取色 Android 12+、深浅色主题、支持字体缩放与系统无障碍规范）。
- **底部导航固定“快速使用 / 应用遮罩 / 设置”**。主页默认“快速使用”；保留自动开关状态，减少不必要弹窗。
- 应用选择器：应用图标、名称、包名辅助文字、即时搜索、已配置标志；只在必要时查询系统应用。
- 应用详情：主视觉是预览图；选择或更换图、滑块、自动启用开关；如设备存在副屏，使用明确屏幕选择。
- 图片导入错误用文字明确说明。透明度滑块旁显示数字与“为什么最大值是 80%？”帮助入口。
- 设置下分区：权限与自动监测、外观、备份/迁移、诊断、高级功能、关于/许可证。
- 高级功能包含双屏、实体键绑定、可选悬浮球；不要在新手操作流程中强推无障碍授权。
- UI 不直接发起多个独立服务操作，所有请求都进入协调器，以真实状态流驱动 UI。

### 7.2 诊断页规格

必须至少提供：

- Android API 与设备信息（脱敏适度）、应用版本、软件包名；悬浮窗、Usage Access、通知及高级可选权限状态。
- 自动监测：已开启 / 暂停 / 权限缺失 / 后台受限；最近一次观测时间、已识别目标包名（可隐藏）。
- 每块显示屏：当前模式（手动/规则/关闭）、当前图片、实际允许最大 Alpha、最近挂载时间与失败码。
- 最近 N 条本地事件，可一键复制**去除文件 URI、用户目录和敏感路径信息**后的诊断摘要；无默认联网日志上报。
- 常见问题和一键前往系统授权入口；诊断信息真实，不能把“发送启动命令成功”当作“窗口挂载成功”。

---

## 8. Codex Cloud 分期施工顺序与交付物

**原则**：每一阶段用独立 PR/可回滚提交；尽量在该阶段完成可编译、可测试的纵切片，不搞一次性全工程替换。阶段之间由实际依赖决定，不能因 UI 开发速度快就绕过安卓安全和数据安全验证。

| 阶段 | 优先级 | 主要施工内容 | 完成验收 |
|---|---|---|---|
| E0 云端环境初始化 | **最先** | 创建 Cloud 环境、自检 JDK/SDK/Gradle、基线构建、记录环境脚本与复现说明 | 云端能真实构建 Debug APK，或给出可复现阻塞信息；环境已发布 |
| P0 基线与安全网 | P0 | 记录 HEAD、旧核心功能清单、改 GitHub CI、写关键状态与文件处理测试、回滚标记 | CI 可复现；不改变现有用户行为；有源码与测试基线 |
| **B1 EasyOverlay 品牌更名** | **P0** | 固定 `applicationId` 和名称，核对 Manifest/资源/通知/磁贴/Provider/动作常量；分提交重构 `namespace` 与 Kotlin 包 | 新 APK 身份为 `com.hycnyl.easyoverlay`；包组件正常；与上游并存；完整更名测试通过 |
| P1A FGS/Android 安全验证 | **P0 阻塞项** | Android 13–16 服务能力审查、specialUse 合规 PoC、后台启动限制、通知/用户停止策略 | 真实运行记录和明确设计决策；不能把“Cloud 可编译”等同实机通过 |
| P1B Overlay 内核 | P0 | 透明度/穿透边界、安全替换图片、运行真相、旋转/双屏、状态反馈、主线程优化 | 手动遮罩显示、触摸穿透、失败回滚，原本能力不倒退 |
| P2 统一图片与存储 | P0 | 统一导入器、5 种格式、filesDir UUID 资产与索引、生命周期清理 | PNG Alpha、JPG、静态 WebP、GIF 能导入/显示；旧路径不误删 |
| P3 应用规则与旧配置导入 | P0 | AppRule、规则编辑、只读 SAF 旧数据迁移、导出备份 | 每应用独立映射；冲突可解释；迁移幂等，不损毁旧数据 |
| P4 UsageStats & Coordinator | P0 | 单路前台观测、串行调和、正确手动优先级、恢复与异常状态 | 快速切换不漏切/误关；手动快速使用不会被自动覆盖 |
| P5 Material You 主界面 | P1 | Compose 3 Tab、新手友好设置、动态色、深色、适配大字号 | 快速使用始终是一级入口；新手 3 步完成手动或应用配置 |
| P6 高级功能 & 诊断 | P1 | 双屏、实体键、磁贴、悬浮球兼容、诊断记录与脱敏导出 | 旧能力按设备可验证；无障碍非强制；悬浮球默认关 |
| P7 发布验收 | P0 | API 33/34/35/36 测试矩阵、无回归、EasyOverlay 品牌核验、版本号/包名/签名/许可证、Release | CI 构建+实机 QA 记录；最终 `com.hycnyl.easyoverlay` 的 GitHub Release APK 与 SHA-256 |

### 8.1 第一阶段 E0 + P0 的具体任务清单（先保留上游身份建立可对比基线）

**E0：云环境初始化**

- [ ] 连接并确认正确的 Fork、记录 `git rev-parse HEAD`；确认当前工作区是否有用户改动。
- [ ] 识别 Gradle Wrapper/JDK/Android SDK 需求；在 Cloud 环境安装缺失工具并确定兼容组合。
- [ ] 验证网络与仓库依赖解析；配置 `JAVA_HOME` / `ANDROID_HOME`。
- [ ] 跑真实 `:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:lintDebug`；保存日志、报告与 APK 路径。
- [ ] 输出 `docs/CLOUD_ENVIRONMENT.md`，保留安装/测试/网络依赖与排错过程，配套 `scripts/verify-android-env.sh`。
- [ ] 将测试过的 Install script / Start skill 保存至云环境并发布；后续复用。

**P0：安全网与基线**

- [ ] 编制 `docs/UPSTREAM_BASELINE.md`：功能与源码路径、依赖、权限、DataSync 风险、QuickUse 现状、测试清单。
- [ ] 编制 `docs/REGRESSION_MATRIX.md`：快速使用、PNG/GIF、触摸、透明度、应用切换、主副屏、快捷键、磁贴、悬浮球与存储流程。
- [ ] 配置 `.github/workflows/android-ci.yml`：在独立 Ubuntu runner 设置 Java/Android SDK、执行 `assembleDebug` / 单元测试 / lint；必要时输出调试产物。
- [ ] 编写可在云端运行的纯 JVM 测试骨架，优先覆盖透明度计算、会话优先级规划、旧数据导入映射（实际迁移逻辑可在后续阶段落地）。
- [ ] 识别证实风险与尚待实机验证事项；生成 `docs/BUILD_BASELINE_REPORT.md`，明确每项测试是否实际执行。
- [ ] 保持旧功能行为不变，**仅 E0/P0 暂不改包名**；结束并标记可回退基线后**必须开始 B1 EasyOverlay 品牌更名**，再推进后续功能阶段。
- [ ] 如果本阶段出现系统工具不能修复的阻塞，记录精确错误与可行替代，不编造成功结果或擅自砍功能。

**首阶段交付检查**：仓库可审计、环境可复用、PR 可回滚、CI 能运行、文档与事实一致；下一阶段 P1A 的阻塞实验清单完整。

### 8.2 B1 — 品牌更名专门阶段（E0/P0 通过后立即实施）

建议以两个可回滚提交/PR 子任务完成：① **安装身份 + 可见品牌**；② **namespace/Kotlin 路径与关联引用清理**。按实际工程规模可合并 PR，但中间测试结果必须可追溯。

**B1-A：安装身份与视觉文案**

- [ ] `app/build.gradle` 中的 Release `defaultConfig.applicationId` 改为且固定为 **`com.hycnyl.easyoverlay`**；检查不同 buildType/flavor 对正式构建无错误 suffix 或覆盖。
- [ ] 将应用显示名称 `@string/app_name` 统一为 **`EasyOverlay`**；检查默认、英文、中文等资源值，修正 UI 中原版“ImageOverlay”作为现任产品名的用法。
- [ ] 替换/创建有独立辨识度的自适应 Launcher 图标及 Android 13+ 单色图标；SplashScreen 使用一致的名称与图标（图标文件应为项目资源，不从网上取侵权素材）。
- [ ] 前台服务通知、通知渠道**展示名称和说明**、快捷设置磁贴标签、快捷键/悬浮球设置界面、诊断标题、关于页、导出备份/日志默认文件名显示 `EasyOverlay`；保留简短准确的功能描述。
- [ ] README、仓库文档首页、构建和安装说明、截图标题、Release 模板与 issue 模板使用 EasyOverlay；明确写明“基于 ImageOverlay 的非官方 Fork”，上游链接和许可/版权原文不可删除。

**B1-B：源码与 Android 资源标识完整性**

- [ ] 以 **`com.hycnyl.easyoverlay`** 为最终目标更改 Gradle `namespace`，再使用 IDE/可审查脚本分步迁移 Kotlin/Java package 与目录，并检查 `R`、`BuildConfig`、测试包名、导入及 ProGuard/R8 配置。可先保留旧 namespace 做过渡，但 **P7 Release 前必须统一**。
- [ ] 检查 `AndroidManifest.xml` 的 `application`、Activity、Service、Receiver、TileService、AccessibilityService 等组件；旧相对 `android:name=".Xxx"` 在 namespace 变化后必须仍解析到真实类，必要时先改全限定类名再移动源码。
- [ ] 检查所有与包身份关联的标识：显式 Intent、广播 Action、IntentFilter、PendingIntent、`setPackage`、`FileProvider` authorities、应用内分享 URI、deep link、App Shortcuts、磁贴组件名、无障碍服务配置/组件字符串、权限与组件的 `android:exported`。不要将旧广播常量复制到新应用导致冲突；对 App 内广播优先显式或限定到自己的包。
- [ ] `getSharedPreferences`、Room DB、DataStore、私有目录等不因改名擅自清空；如迁移内部键名，提供幂等迁移和回归测试。**用于识别旧备份的 `ImageOverlay` 文件夹名与旧 JSON 字段必须保留兼容**。
- [ ] 检查任意硬编码 `com.example.imageoverlay`、`ImageOverlay`：分别标注“应更名 / 上游致谢 / 历史数据识别 / 上游源码记录”；扫描报告中对保留的每一项说明理由。
- [ ] GitHub CI 在改名后构建 EasyOverlay Debug APK，并检查产物 Manifest 中的 application ID；以后所有构建仍以正式 ID 作为 Release 验收标准。

**B1 验收条件（自动化 + 实机）**

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 实际通过，或单独列出未运行/受阻项，不得报告虚构测试结果。
- [ ] 用 `apkanalyzer manifest application-id <apk>`、`aapt dump badging <apk>` 或同等准确方法从**最终 APK**核对 `com.hycnyl.easyoverlay`，不以仅检查源代码字符串替代。
- [ ] 在真实 Android 设备安装新 APK，桌面显示 EasyOverlay；点击图标能打开主界面，现有“快速使用/配置/设置”等基线入口仍能正常操作（新“快速使用/应用遮罩/设置”文案与布局在 P5 验收）；通知/磁贴/权限入口正确工作。
- [ ] 若设备装有原版 ImageOverlay，两者并存且互不相互启动/关闭服务、互不共享状态；新安装不会静默访问或修改原版的数据。
- [ ] 对旧配置迁移执行一次“仅扫描不写入”演练，确保新的包名不会破坏后续明确授权导入的设计。
- [ ] 产出 `docs/BRANDING_MIGRATION_REPORT.md`：变更清单、保留的上游标识、实测 APK ID、自动测试结果、待实机验证项。

**警告**：改应用 `applicationId` 与更改源码 package 是相关但不同的两项工程操作。严禁使用无审查的全仓库替换，把上游版权链接、旧备份目录名和需要兼容的历史数据字段统统改掉。

### 8.3 阶段验收时如何区分自动与人工测试

| 类型 | Codex Cloud 能证明什么 | 仍需 Android 实机确认 |
|---|---|---|
| Kotlin/Compose 编译 | 代码是否能构建 APK | 安装后运行是否稳定 |
| JVM/状态机单元测试 | 事件输入与规则优先级逻辑正确 | UsageStats 事件真实性、ROM 差异 |
| Robolectric/静态检查 | Android API 调用契约的一部分 | 真实悬浮窗触摸、窗口安全规则 |
| Instrumentation/Emulator（如果 Cloud 支持） | 标准系统部分交互验证 | 多厂商省电机制、外接屏、物理按键 |
| GitHub CI | 独立构建环境复现 | 设备上用户体验是否符合预期 |

---

## 9. EasyOverlay 品牌交付、GitHub APK 发行与迁移兼容

### 9.1 正式名称已定稿：EasyOverlay

用户已明确确认：**应用名 EasyOverlay；正式 `applicationId = com.hycnyl.easyoverlay`**。这不是候选值，也不是留给 Codex 自行决策的设计空间。

- 对外文案、应用图标、Launcher、Quick Settings Tile、通知、关于/诊断、GitHub README 与 Release 的产品名均采用 **EasyOverlay**。
- 上游仅在版权、许可证、非官方 Fork 说明、技术审查和历史配置导入中称为 **ImageOverlay**。不得去掉有效版权归属，也不得把新应用冒充原作者发行的官方更新。
- 从 E0/P0 建立可比较的上游基线开始，紧接着执行 **B1** 独立更名阶段（完整清单见第 8.2 节）。无须在 E0 初始化云环境时先重构全部源码包。
- Release 时 `applicationId`、最终 `namespace` 与 Kotlin/Java 顶层包前缀均应是 `com.hycnyl.easyoverlay`；允许迁移中短期出现旧 namespace/旧源包以保证小步验证，但不得把过渡状态当成发行完成。
- GitHub 仓库名如果暂时仍为原 Fork URL，可以保持；应用显示名和 APK 包身份不依赖仓库名。
- 正式更名后的代码合并前必须检查广播 Action、Provider authorities、`android:exported`、通知与磁贴组件解析、安全权限、图标、国际化、备份及旧数据导入功能。

### 9.2 独立包和历史数据

新 `applicationId`、新签名意味着**不能把新 APK 当成原版直接覆盖升级**；仍可与原版并存。旧数据通过用户重新授权的 SAF/导出备份显式导入，不能声称 Android 会自动共享原版私有配置或 URI 授权。

### 9.3 GitHub Releases 要求

- GPL-3.0 或上游实际许可证的正确继承和著作权说明；README 标示为非官方 Fork 并链接原项目。
- 自己生成并妥善保存发布签名密钥，**不放到仓库或日志**；GitHub Actions 使用安全变量时也不得泄露；早期先以 Debug APK 验收。
- **正式包名已确定为 `com.hycnyl.easyoverlay`**。根据实际发布设置 `versionCode`、`versionName`；检查发布产物 Manifest 和实际安装身份；测试安装、同签名更新、与原版并存和卸载后重装。
- GitHub Release 名称、签名 APK、SHA-256 文件统一使用 **EasyOverlay** 品牌；附设备/Android 版本测试范围、已知问题、权限说明、升级和旧数据导入说明。

---

## 10. 最终 V1 真实用户旅程与“完成”的定义

**旅程 A：偶尔使用，无绑定应用**

> 首次进入快速使用 → 从相册/文件选择图片 → 调整到 60% → 授予悬浮窗权限 → 点击显示 → 切换到任意 App 下层可正常触摸 → 从通知或快速使用页关闭。没有创建组、没有绑定包名、没有 Usage Access 强制要求。

**旅程 B：为一个游戏创建自动遮罩**

> 应用遮罩 → 搜索目标游戏 → 导入图片 → 调整透明度 → 开启该规则和自动监测 → 用户按引导授予使用情况访问与系统要求的权限 → 打开该游戏自动显示，切至其它无规则应用自动关闭。

**旅程 C：自动模式期间临时换图**

> A 游戏正在自动显示 A 图 → 打开“快速使用”临时选另一张图片 → 用户启动快速遮罩 → 切到 B 游戏后临时图仍保持 → 关闭临时图，规则引擎按最新 B 状态恢复 B 图或关闭。

**完成标准**：正式对外安装包以 **EasyOverlay** 标识、`com.hycnyl.easyoverlay` 的 Android 应用 ID 发布；上述旅程在 Android 13/14/15/16 的代表性真机上分别有记录；手动与自动模式优先级稳定；图片导入不误删；Cloud/CI 构建可复现；错误能诊断；安全限制文案真实；版本能通过 GitHub 安装。若无部分设备，Release 必须披露未验证范围。

---

## 11. 官方技术参考与来源

- Codex Cloud 环境：<https://developers.openai.com/codex/environments/cloud-environments>
- Android SDK 工具：<https://developer.android.com/tools>
- Android SDK 命令行：<https://developer.android.com/tools/sdkmanager>
- Android SDK 环境变量：<https://developer.android.com/tools/variables>
- Android 前台服务变化：<https://developer.android.com/develop/background-work/services/fgs/changes>
- Android FGS 类型：<https://developer.android.com/develop/background-work/services/fgs/service-types>
- Android `FLAG_NOT_TOUCHABLE`：<https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE>
- Android 15 target 行为变化：<https://developer.android.com/about/versions/15/behavior-changes-15>
- Jetpack Compose Material 3：<https://developer.android.com/develop/ui/compose/designsystems/material3>
- 原项目：<https://github.com/PerryKum/ImageOverlay>

---

**交接提示**：本文件是唯一权威施工规格，建议在仓库保存为 `docs/FORK_V1_CONSTRUCTION.md`。Codex Cloud 环境创建时先解决第 1 节（E0）；随后按第 8 节先完成 P0、**B1 EasyOverlay 品牌更名**，再继续 P1A–P7。用户已决定正式名称与包名，无需再询问。开发顺序与实现细节在能证明等效及完成验收的前提下允许 Codex 自主调整，不需要额外的逐条强约束提示词。
