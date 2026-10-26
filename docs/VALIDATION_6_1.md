# EasyOverlay 6.1 验证记录

2026-10-26。交付为单一 universal APK，版本 6.1 / 610，包名 `com.hycnyl.easyoverlay`。
6.0 正式版与标签不变，继续沿用其发布签名及自有数据格式。

## 构建与自动检查

- JDK 17、Gradle 8.12、AGP 8.10.1、Kotlin 2.1.21；compileSdk 36 / targetSdk 35 / minSdk 33。
- 57 项单元/Android 资源测试通过，lint 无错误。保留的警告主要是已说明的 targetSdk、依赖升级提示、Shizuku 内部 API 反射及样式建议。
- 覆盖协调器所有权、异步旧结果、权限回退、可信不透明度、检测来源选择、解锁历史重建、恢复退避、用户停止及 Binder 查询并发。
- 新增回归：先用户停止、再发生系统退出，仍不得恢复；新一次明确开启可重新允许恢复。Provider 唤醒也会持久化用户停止意图。
- release 实际经过 R8/资源压缩、原发布密钥签名、apksigner、身份/版本/ABI/许可证检查。每种原生库均同时包含 arm64-v8a、armeabi-v7a、x86、x86_64。
- 构建脚本产出 APK 与 SHA-256；最终标签对应提交写入 APK 的 version-control-info，发行时再次核对。
- GitHub CI 对各阶段提交运行 debug 构建、全部测试、lint 及 universal APK 检查。

公开签名证书 SHA-256：
`8ba5228b6199097895c2d5114193673580dfd8a8bf847e355f842c2f65c387ea`。
私钥/密码仅在仓库外使用，不进入 Git、CI 或 Release。

## Android 15 模拟器实测

环境为 AOSP API 35 / x86_64，软件模拟 CPU/GPU，720×1280 / 280 dpi。该环境可验证行为与状态，不能用于承诺实机延迟、耗电或长期保活。

| 检查 | 结果与证据 |
| --- | --- |
| 从已签名 6.0 覆盖升级 | 成功，原私有 PNG、快速使用选择和规则保留，无需清除数据 |
| 简体中文 / English | 从界面切换成功；LocaleManager 记录 zh-CN，覆盖更新后保留 |
| 快速使用超过 80% | 出现警告、复选框和确认按钮；勾选后高值滑块仍为红色 |
| 应用规则超过 80% | 关闭提醒后 86% 仍红色；重新开启提醒后，拖动再次出现中文警告、复选框与“确定” |
| 可信窗口实际 alpha | WindowManager 记录 TYPE_ACCESSIBILITY_OVERLAY / type 2032，实际 alpha 0.89；Clock 规则实际 alpha 0.86 |
| 无障碍断开 | 改为 TYPE_APPLICATION_OVERLAY / type 2038，实际 alpha 限制到 0.8，未保留超限普通窗口 |
| 无 Usage Access 的无障碍检测 | GET_USAGE_STATS 保持 ignore；打开 Clock 自动显示对应规则，切到系统设置后移除 |
| 进程终止恢复 | 对测试应用发送 kill -9 后新进程恢复自动监测并重新匹配 Clock；不是把旧窗口状态当作仍在运行 |
| 用户主动停止 | Android `cmd activity stop-app` 后重新打开，监测关闭，持久化 wanted=false |
| 真实 Shizuku | 安装并启动官方 13.6.0，用户授权；关闭无障碍、拒绝 Usage Access 后，诊断实际来源为 Shizuku，任务读取进程运行在 shell UID，Clock 规则生效 |
| 优化改动预览 | 确认前清楚列出仅针对 EasyOverlay 的通知、使用情况访问和电池白名单三项改动 |
| Shizuku 应用与恢复 | 通知 false→true→false，Usage ignore→allow→ignore，用户电池白名单 无→有→无；修正后恢复前后应用 PID 相同，三项完成且恢复点清除 |
| 真实 Dhizuku | 仅在本测试虚拟设备配置官方 2.12.0 为 Device Owner，授权成功；本应用通知权限 false→true 且 POLICY_FIXED 符合管理授权语义 |
| Dhizuku 恢复 | 原管理策略与通知拒绝均恢复，POLICY_FIXED 移除；系统结束客户端后重新打开，只读核对成功并清除恢复点 |
| 关于页 | 设置最下方入口、名称、简介、Hycnyl 作者主页、仓库地址均显示正确；两个链接均成功触发系统浏览器 VIEW Activity |
| 图标 | Android 原生 AdaptiveIconDrawable 渲染彩色/单色图标成功，主体 70% 缩放，无裁切；没有把该检查等同于 MIUI 实机截图 |

通知撤权的初测确实导致了恢复中断，随后已修正并重测：Shizuku 使用 shell 的无终止通知撤销接口；不支持该接口时保留恢复点并提示手动处理。Dhizuku 将两步策略恢复放入管理方单个事务，客户端重开后只做核对，不自动覆盖后来改过的设置。

## 可查看的界面证据

- [原生彩色与主题单色图标](evidence-6.1/icons-native.png)
- [中文关于页](evidence-6.1/about-zh.png)
- [快速使用警告](evidence-6.1/opacity-warning.png)、[中文应用规则警告](evidence-6.1/opacity-warning-rule-zh.png)
- [关闭提醒后的红色应用规则滑块](evidence-6.1/opacity-red-rule.png)
- [仅本应用的优化改动清单](evidence-6.1/optimization-preview-zh.png)

## 数据与平台边界

EasyOverlay 6.0 自有备份格式及当前格式的校验/往返测试通过；旧迁移记录额外字段被安全忽略。
ImageOverlay 导入入口、扫描器、规划器与迁移字段实现已删除，保留 GPL、来源致谢和第三方许可。

自动规则目前只作用于主屏；副屏保留独立手动控制及规则编辑，扩展可信不透明度不向副屏开放。
UsageStats 在多窗口、缺失历史事件或厂商限制下可能不准确；界面显示等待或回退原因。
无障碍、Shizuku、Dhizuku、前台服务及系统白名单均不保证厂商永不清理。

未完成的实机范围包括：MIUI/HyperOS 等厂商长期后台、真实双屏、全部实体键组合、Android 13/14/16 全矩阵及 targetSdk 36 前台服务复核。Dhizuku 本轮验证了 Device Owner；Profile Owner 工作资料场景未实测。
用户对 6.0 基本功能/快速使用的实机验收是用户报告，不扩写为 6.1 的完整实机通过。6.1 先以预发布交付。
