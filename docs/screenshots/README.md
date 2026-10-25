# API 35 软件模拟器运行证据

这些截图来自安装了发布签名候选包的 Android 15 AOSP x86_64 软件模拟器。
使用程序生成的透明边框测试 PNG，不包含用户图片。

- quick-use-image.png：同签名更新后图片仍在，窗口尚未启动。
- overlay-manual.png：实际手动遮罩已显示，底部提供停止入口。
- overlay-over-clock.png：切到时钟后遮罩保留，在下层点击成功切换至 Stopwatch。

这些截图不能替代真实设备验证。最终源码另增加了权限撤销与全部屏幕切换的状态回归修复，
不改变这里展示的布局。其它证据见 ../VALIDATION.md。
