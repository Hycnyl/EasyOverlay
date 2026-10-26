# EasyOverlay 6.1 — 本轮实施规格

本轮用户要求取代 6.0 施工文档中冲突的限制。6.0 已由用户转为正式发布，用户自报
实机基本功能、尤其快速使用正常；未提供设备/Android 版本，不能据此扩写完整实机矩阵。
`v6.0` 与发布签名保持不变，6.1 使用相同 applicationId、私有图片/规则格式和签名升级。

## 已授权改进

1. 修复自动监测漏检、迟缓和被系统回收后不恢复的问题。优先无障碍窗口事件，
   提供 Shizuku 任务快照增强，UsageStats 作为可解释的降级路径。Dhizuku 按实际
   Device/Profile Owner 权限接入，不能冒充 shell/root 前台任务能力。
2. 无障碍真实连接、可信覆盖可用时允许扩展不透明度。快速使用和应用规则统一：
   拖动超过 80% 时提示、提供“不再提醒”和确认；超过 80% 的滑块始终为红色，
   即使已关闭提醒也不移除红色状态。普通悬浮窗继续遵守系统 alpha 上限；权限断开
   必须安全降级，不显示虚假的 100% 实际状态。
3. 应用内语言切换：简体中文 / English；系统持久化，运行通知和诊断同步本地化。
4. 删除 ImageOverlay 目录导入、扫描、映射、旧迁移状态和对应 UI/测试。保留有效
   GPL、版权和上游致谢；EasyOverlay 自有 6.0 数据/备份仍须安全升级。
5. 相应更新权限说明、诊断、架构/发行文档、回归与构建检查。

用户另外已选择加入“一键优化自动切换”：主动执行前展示仅针对 EasyOverlay 的
通知/Usage Access/电池优化白名单改动，保留原状态并支持恢复。不把白名单称为永不被杀。

## 生命周期与安全边界

- 仅恢复用户真正开启的自动监测会话；区分系统回收、用户停止、权限断开与服务重连。
  用户从通知/App 停止或系统活动应用管理主动停止后，不循环拉起。
- 恢复仍服从 Android 前台服务启动限制；失败给出可操作的恢复入口和原因，避免静默成功。
- 无障碍只读取识别前台应用所需的包名/窗口属性，不保存或上传屏幕文字。
- Shizuku/Dhizuku 均由用户主动授权，绑定、撤权、死亡和重连状态必须真实可见。
- 私钥/密码继续只在仓库外。交付单一 universal APK，四种 ABI 合一，附 SHA-256。

## 版本与验证

本轮以 6.1（versionCode 610）开发：保留产品与自有数据格式，新增可选能力和兼容修复。
compileSdk 36 / targetSdk 35 暂保持，不能将基本功能实机通过等同 API36 FGS 全矩阵通过。
独立提交分阶段推送；新增状态机、权限回退、恢复及危险不透明度提醒测试，并真实构建、
lint、核对 APK 身份/签名/架构。在可用模拟器上检查升级、语言、无障碍与自动切换，
无法完成的真实 Shizuku/Dhizuku/厂商保活测试分别披露。

技术依据：
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE
- https://developer.android.com/reference/android/app/Service#START_STICKY
- https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping
- https://github.com/RikkaApps/Shizuku-API
- https://github.com/iamr0s/Dhizuku-API

## 中断恢复后的补充要求

- 图标彩色前景以中心为轴缩放到原主体的 70%，单独的 Android 单色动态主题图标采用相同尺寸，并检查安全区。
- 设置页最下方放置关于入口。关于页展示名称、简介、作者 Hycnyl（链接主页）和仓库地址（链接仓库），许可证与上游致谢保留。
- 减少不必要的代理调用；恢复后不再启动子代理，核心集成、验证、修复与交付由主代理完成。

## 特权恢复的实测修正

真实 Shizuku / Dhizuku 模拟器接入发现，普通通知权限撤销会终止客户端 UID。
Shizuku 的恢复先处理使用情况模式/电池白名单，再使用 shell 获准的专用无终止通知
撤销接口；不支持该接口的系统保留恢复点、明确提示到系统设置处理，不冒险中断余下恢复。
Dhizuku 将实际授权和管理策略的恢复放入同一管理方 Binder 事务；若系统关闭客户端，
重新打开后只读核对保存的恢复请求，完全一致才清除恢复点，不自动覆盖后来改过的设置。

接口依据（Android 13 与 15 源码均有该接口）：
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-13.0.0_r1/core/java/android/permission/PermissionManager.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/permission/PermissionManager.java

用户停止判定会检查近期历史中的用户停止记录，不让较晚的系统退出覆盖先前停止意图；
应用进程即使仅由 Provider 唤醒，也先持久化这项取消意图。明确再次开启后才重新允许恢复。
