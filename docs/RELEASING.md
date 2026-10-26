# 构建和发行 EasyOverlay

## 正式安装身份

- 应用名：EasyOverlay
- applicationId / namespace：`com.hycnyl.easyoverlay`
- 当前 6.1：versionName `6.1`，versionCode `610`；已发布 6.0 保持 `600`。
- minSdk 33，compileSdk 36，targetSdk 35；target 36 的实机门槛见 FGS_ANDROID_REVIEW.md。
- 单一 universal APK，不配置 ABI splits。每个 AndroidX 原生库均必须包含
  arm64-v8a、armeabi-v7a、x86、x86_64；脚本逐库校验。

## 私密签名文件

将下列文件保存在仓库外，文件权限设为 0600：

```properties
storeFile=/private/path/EasyOverlay-release.keystore
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=easyoverlay-release
keyPassword=YOUR_PRIVATE_PASSWORD
```

```sh
export EASYOVERLAY_SIGNING_PROPERTIES=/private/path/signing.properties
bash scripts/build-release.sh
```

脚本执行真实 release 构建、签名验证、APK 身份/版本/ABI/许可证检查，并在
忽略的 `dist/` 输出 `EasyOverlay-v6.1.apk` 与 `EasyOverlay-v6.1.sha256`。
R8 保留版本化 JSON / 备份模型的字段，防止混淆破坏持久化格式。

6.0 使用单独生成的 RSA 3072 发布密钥。证书 SHA-256 为：
`8ba5228b6199097895c2d5114193673580dfd8a8bf847e355f842c2f65c387ea`。
证书摘要可以公开，私钥和密码不能进入 Git、Release 或日志。后续升级必须使用同一密钥。
私密签名备份单独交付给仓库所有者，不作为 GitHub Release 资产上传。

## 发布检查

```sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
bash scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk
bash scripts/build-release.sh
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 -v 4 dist/EasyOverlay-v6.1.apk
```

确认工作区源码已提交并推送，再为被构建的源码创建 `v6.1` 标签。通过已认证的
GitHub CLI 上传 APK 和 SHA-256，不上传签名备份。Release Notes 必须包含：
验证范围、真实设备未验证项、target SDK 说明、权限用途、EasyOverlay 自有数据升级与已知限制。
真实设备矩阵不完整时使用预发布标记，不能将云端构建等同于实机验收。

本项目为 GPL-3.0 非官方 Fork。Release 对应标签的源码、GPL 许可证与上游致谢
须持续可获得。GitHub 自动生成的 Source code 归档和仓库标签提供对应源码。
