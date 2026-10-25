# EasyOverlay

轻量的 Android 屏幕图片遮罩工具。正式安装身份：`com.hycnyl.easyoverlay`。

这是基于 [PerryKum/ImageOverlay](https://github.com/PerryKum/ImageOverlay) 的非官方
Fork，遵循上游 GPL-3.0 许可证。EasyOverlay 可以与原版同时安装；原版数据需要用户
通过系统文件选择器明确授权导入，不会自动共享原应用的数据或权限。

6.0 正在按 [施工规格](docs/FORK_V1_CONSTRUCTION.md) 分阶段实施。
构建环境、原版基线与实际验证记录见 [docs](docs/)。版本和最终使用说明将在发行时更新。

## 开发

JDK 17、Android SDK、Gradle Wrapper。环境说明见
[CLOUD_ENVIRONMENT.md](docs/CLOUD_ENVIRONMENT.md)。

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

不要提交签名密钥、密码、local.properties、APK 或工具链缓存。
上游原始可回退版本标记为 `baseline-5.2`。
