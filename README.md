# Onecode Terminal

一个原生 Android Ubuntu 终端应用。打开应用后直接进入 Ubuntu shell，不做多余的首页。

## 特性

- Ubuntu 24.04 arm64 用户空间，使用 PRoot + PTY 在 Android 上运行
- 原生 Kotlin / Jetpack Compose UI
- 多终端会话，可新建、切换、关闭
- 真正的 PTY 终端渲染，支持 ANSI、交互式程序和全屏终端应用
- 命令输入、系统软键盘、Ctrl+C、常用命令快捷键
- 深色、克制的终端界面：不使用渐变、卡片堆叠或 AI 风格营销元素
- arm64-v8a 单 ABI，减少 APK 体积
- Ubuntu rootfs 采用现有的精简 Noble 镜像；当前压缩包约 61 MiB，目标 APK 控制在 100 MiB 内
- 不使用 GitHub Actions，构建完全由本地 Android Studio / Gradle 完成

## 项目结构

- `app/`：Android 应用壳、生命周期、Manifest 和启动逻辑
- `terminal-core/`：终端核心（Git submodule），包含 PTY、ANSI emulator、Ubuntu 用户空间、会话管理和 Compose 终端 UI

## 构建

要求：Android Studio、JDK 17、Android SDK 34。

```bash
git clone --recurse-submodules https://github.com/hishow1996/OnecodeTerminal.git
cd OnecodeTerminal
./gradlew assembleRelease
```

APK 输出：`app/build/outputs/apk/release/app-release.apk`

如果已经普通 clone：

```bash
git submodule update --init --recursive
```

## 体积策略

应用只构建 `arm64-v8a`，并保留 Ubuntu 精简 rootfs；没有加入 x86、x86_64、armeabi-v7a 等额外 ABI。发布前建议在本地执行 `assembleRelease` 后检查 APK 实际大小。

## GitHub Actions

本项目刻意不包含 GitHub Actions workflow。

## License

GPLv3
