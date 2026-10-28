# EasyOverlay

在其他应用上方显示自选图片的 Android 遮罩工具，由 [Hycnyl](https://github.com/Hycnyl) 维护，支持 Android 13 及以上。

[下载 APK / 查看版本更新](https://github.com/Hycnyl/EasyOverlay/releases) · 安装包为 universal APK，无需选择架构。

## 功能

- **快速使用**：选图、调节不透明度、显示遮罩，无需绑定应用。支持 PNG、JPEG、静态 WebP 和 GIF，保留图片透明度与 GIF 动画。
- **快捷控制**：通过通知、快捷设置磁贴、悬浮球或实体键组合开关遮罩；支持双屏独立手动控制。
- **不透明度调节**：普通模式通常最高为 80%；启用无障碍后主屏可达 100%。超过 80% 时提供警告，滑块持续显示红色。
- **应用规则**：按应用配置图片与不透明度，可选无障碍、Shizuku 或使用情况访问检测前台应用；快速使用优先于自动规则。
- **后台与授权管理**：支持自动会话恢复；借助 Shizuku 预览并优化通知、使用情况访问和电池白名单设置，可恢复原设置。已配置 Dhizuku 的设备可管理本应用通知权限。
- **图片与备份**：私有图片库支持复用图片，EasyOverlay ZIP 备份支持导入、导出与合并。
- **主题与语言**：支持 Material You 动态配色、深浅色主题、简体中文与 English。

应用自动切换仍在完善，日常使用推荐快速使用与快捷设置磁贴。

## 许可与致谢

项目源自 [PerryKum/ImageOverlay](https://github.com/PerryKum/ImageOverlay)，现独立维护，遵循 [GPL-3.0](LICENSE)。

- [AndroidX（Compose、DataStore）](https://developer.android.com/jetpack/androidx)、[Kotlin](https://github.com/JetBrains/kotlin)、[kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)、[Gson](https://github.com/google/gson)：Apache-2.0。
- [Shizuku API（RikkaW）](https://github.com/RikkaApps/Shizuku-API)、[Dhizuku API（R0S）](https://github.com/iamr0s/Dhizuku-API)：MIT。
