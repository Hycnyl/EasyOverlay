# EasyOverlay 6.1

- 主屏无障碍遮罩支持最高 100% 不透明度；快速使用和应用规则超过 80% 时弹出警告，支持“不再提醒”，高值滑块持续红色。
- 新增无障碍与 Shizuku 前台应用检测、使用情况访问回退，以及自动会话恢复。
- 新增“一键优化自动切换”：执行前展示通知、使用情况访问及电池白名单改动，支持恢复原设置；新增 Dhizuku 通知权限管理。
- 新增简体中文 / English 切换。
- 移除 ImageOverlay 导入，保留 EasyOverlay 自有备份及 6.0 数据兼容。
- 图标主体缩至原尺寸的 70%，新增主题单色图标。
- 新增关于页，提供作者主页与仓库链接。

应用自动切换仍在完善，推荐通过快速使用和快捷设置磁贴开关遮罩。

## 许可与致谢

项目源自 [PerryKum/ImageOverlay](https://github.com/PerryKum/ImageOverlay)，遵循 [GPL-3.0](https://github.com/Hycnyl/EasyOverlay/blob/main/LICENSE)。

本版新增使用 [Shizuku API（RikkaW）](https://github.com/RikkaApps/Shizuku-API) 与 [Dhizuku API（R0S）](https://github.com/iamr0s/Dhizuku-API)（MIT）；基础组件为 AndroidX（Compose、DataStore）、Kotlin / kotlinx.coroutines、Gson（Apache-2.0）。
